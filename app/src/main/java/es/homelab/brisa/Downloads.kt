package es.homelab.brisa

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast
import org.mozilla.geckoview.WebResponse

/**
 * Descargas a Descargas/Brisa. En Android 10+ se guarda el flujo que ya abrió Gecko
 * (conserva sesión y cookies de la web); en versiones anteriores, con el gestor del sistema.
 */
object Downloads {
    fun save(ctx: Context, r: WebResponse) {
        val disp = r.headers["Content-Disposition"] ?: r.headers["content-disposition"]
        val type = (r.headers["Content-Type"] ?: r.headers["content-type"])?.substringBefore(';')?.trim()
        var name = URLUtil.guessFileName(r.uri, disp, type)
        if (!name.contains('.')) MimeTypeMap.getSingleton().getExtensionFromMimeType(type)?.let { name += ".$it" }
        val main = Handler(Looper.getMainLooper())
        fun toast(t: String) = main.post { Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show() }

        val body = r.body
        if (Build.VERSION.SDK_INT >= 29 && body != null) {
            toast("Descargando $name…")
            Thread {
                val res = ctx.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, type ?: "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Brisa")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = res.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                try {
                    uri ?: error("sin destino")
                    res.openOutputStream(uri)!!.use { out -> body.use { it.copyTo(out) } }
                    res.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                    toast("Descargado: $name")
                } catch (e: Exception) {
                    uri?.let { res.delete(it, null, null) }
                    toast("No se pudo descargar $name")
                }
            }.start()
        } else {
            val dm = ctx.getSystemService(DownloadManager::class.java)
            dm.enqueue(DownloadManager.Request(Uri.parse(r.uri))
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Brisa/$name"))
            toast("Descargando $name…")
        }
    }
}
