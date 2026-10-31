package es.homelab.brisa

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.WebResponse

/**
 * Descargas a Descargas/Brisa. En Android 10+ se guarda el flujo que ya abrió Gecko
 * (conserva sesión y cookies de la web) y se ve el progreso dentro de la app;
 * en versiones anteriores, con el gestor del sistema.
 */
object Downloads {
    class Item(val name: String, val mime: String, val time: Long) {
        var uri by androidx.compose.runtime.mutableStateOf<Uri?>(null)
        var done by androidx.compose.runtime.mutableLongStateOf(0L)
        var total by androidx.compose.runtime.mutableLongStateOf(-1L)
        var status by androidx.compose.runtime.mutableStateOf("Descargando")   // Descargando · Terminada · Error · Cancelada
        @Volatile var cancel = false
    }

    val items = mutableStateListOf<Item>()
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return; loaded = true
        ctx.getSharedPreferences("descargas", 0).getString("l", "")!!.split('\n').forEach { line ->
            val p = line.split('\t'); if (p.size < 4) return@forEach
            items += Item(p[0], p[1], p[2].toLongOrNull() ?: 0).apply {
                uri = p[3].takeIf { it.isNotEmpty() }?.let(Uri::parse); status = "Terminada"
                total = p.getOrNull(4)?.toLongOrNull() ?: -1; done = total
            }
        }
    }

    private fun persist(ctx: Context) = ctx.getSharedPreferences("descargas", 0).edit().putString("l",
        items.filter { it.status == "Terminada" }.take(100)
            .joinToString("\n") { "${it.name}\t${it.mime}\t${it.time}\t${it.uri ?: ""}\t${it.total}" }).apply()

    fun clear(ctx: Context) { items.removeAll { it.status != "Descargando" }; persist(ctx) }

    fun open(ctx: Context, it: Item) {
        val u = it.uri ?: return
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(u, it.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Toast.makeText(ctx, "No hay ninguna app para abrir ${it.name}", Toast.LENGTH_SHORT).show() }
    }

    fun save(ctx: Context, r: WebResponse) {
        load(ctx)
        val disp = r.headers["Content-Disposition"] ?: r.headers["content-disposition"]
        val type = (r.headers["Content-Type"] ?: r.headers["content-type"])?.substringBefore(';')?.trim()
        var name = URLUtil.guessFileName(r.uri, disp, type)
        if (!name.contains('.')) MimeTypeMap.getSingleton().getExtensionFromMimeType(type)?.let { name += ".$it" }
        val mime = type ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.')) ?: "application/octet-stream"
        val main = Handler(Looper.getMainLooper())
        fun toast(t: String) = main.post { Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show() }
        val item = Item(name, mime, System.currentTimeMillis())
        item.total = (r.headers["Content-Length"] ?: r.headers["content-length"])?.toLongOrNull() ?: -1
        main.post { items.add(0, item) }

        val body = r.body
        if (Build.VERSION.SDK_INT >= 29 && body != null) {
            toast("Descargando $name…")
            Thread {
                val res = ctx.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Brisa")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = res.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                try {
                    uri ?: error("sin destino")
                    res.openOutputStream(uri)!!.use { out ->
                        body.use { inp ->
                            val buf = ByteArray(64 * 1024); var n: Int; var got = 0L; var last = 0L
                            while (inp.read(buf).also { n = it } >= 0) {
                                if (item.cancel) error("cancelada")
                                out.write(buf, 0, n); got += n
                                if (got - last > 256 * 1024) { last = got; val g = got; main.post { item.done = g } }
                            }
                            main.post { item.done = got; if (item.total < 0) item.total = got }
                        }
                    }
                    res.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                    main.post { item.uri = uri; item.status = "Terminada"; persist(ctx) }
                    toast("Descargado: $name")
                } catch (e: Exception) {
                    uri?.let { res.delete(it, null, null) }
                    main.post { item.status = if (item.cancel) "Cancelada" else "Error" }
                    if (!item.cancel) toast("No se pudo descargar $name")
                }
            }.start()
        } else {
            val dm = ctx.getSystemService(DownloadManager::class.java)
            dm.enqueue(DownloadManager.Request(Uri.parse(r.uri))
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Brisa/$name"))
            main.post { item.status = "En el gestor del sistema" }
            toast("Descargando $name…")
        }
    }
}
