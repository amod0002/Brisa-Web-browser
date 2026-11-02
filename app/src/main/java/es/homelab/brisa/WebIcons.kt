package es.homelab.brisa

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Iconos de las webs: los indica la propia página (extensión) y se guardan en disco por dominio. */
object WebIcons {
    val map = mutableStateMapOf<String, ImageBitmap>()
    private val pool = Executors.newFixedThreadPool(2)
    private val pending = HashSet<String>()

    private fun file(ctx: Context, host: String) = File(File(ctx.cacheDir, "icons").apply { mkdirs() }, host.replace(Regex("[^a-zA-Z0-9.-]"), "_"))

    /** Devuelve el icono si ya está; si no, lo busca en disco en segundo plano. */
    fun get(ctx: Context, host: String): ImageBitmap? {
        map[host]?.let { return it }
        if (host.isNotBlank() && pending.add(host)) pool.execute {
            val f = file(ctx, host)
            if (f.exists()) BitmapFactory.decodeFile(f.path)?.let { map[host] = it.asImageBitmap() }
            pending.remove(host)
        }
        return null
    }

    /** Solo http(s), y una web de internet no puede hacer que Brisa pida algo a la red local. */
    fun allowed(pageHost: String, iconUrl: String): Boolean {
        if (!iconUrl.startsWith("https://") && !iconUrl.startsWith("http://")) return false
        val iconHost = History.hostOf(iconUrl)
        return !Privacy.isLocal(iconHost) || Privacy.isLocal(pageHost)
    }

    /** La página dice cuál es su icono: se descarga si no lo teníamos. */
    fun fetch(ctx: Context, host: String, iconUrl: String) {
        if (host.isBlank() || map.containsKey(host) || !pending.add(host)) return
        pool.execute {
            try {
                val f = file(ctx, host)
                if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 7L * 86_400_000) {
                    val c = URL(iconUrl).openConnection() as HttpURLConnection
                    c.connectTimeout = 5000; c.readTimeout = 5000
                    c.instanceFollowRedirects = false   // una redirección podría apuntar a la red local
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Brisa")
                    // Máximo 512 KB: un «icono» enorme no debe llenar la memoria
                    val bytes = c.inputStream.use { inp ->
                        val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(16 * 1024); var n: Int
                        while (inp.read(buf).also { n = it } >= 0) { out.write(buf, 0, n); if (out.size() > 512 * 1024) error("demasiado grande") }
                        out.toByteArray()
                    }
                    if (BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) f.writeBytes(bytes)
                }
                BitmapFactory.decodeFile(f.path)?.let { map[host] = it.asImageBitmap() }
            } catch (_: Exception) {
            } finally { pending.remove(host) }
        }
    }
}
