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

    /** La página dice cuál es su icono: se descarga si no lo teníamos. */
    fun fetch(ctx: Context, host: String, iconUrl: String) {
        if (host.isBlank() || map.containsKey(host) || !pending.add(host)) return
        pool.execute {
            try {
                val f = file(ctx, host)
                if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 7L * 86_400_000) {
                    val c = URL(iconUrl).openConnection() as HttpURLConnection
                    c.connectTimeout = 5000; c.readTimeout = 5000
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Brisa")
                    val bytes = c.inputStream.use { it.readBytes() }
                    if (BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) f.writeBytes(bytes)
                }
                BitmapFactory.decodeFile(f.path)?.let { map[host] = it.asImageBitmap() }
            } catch (_: Exception) {
            } finally { pending.remove(host) }
        }
    }
}
