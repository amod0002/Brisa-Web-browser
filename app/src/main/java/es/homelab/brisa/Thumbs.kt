package es.homelab.brisa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executors

/**
 * Miniaturas de la cuadrícula guardadas en disco (#58), para que sigan ahí cuando Android cierra Brisa en
 * segundo plano. Solo de pestañas normales, nunca de privadas. Van en la caché de la app
 * (cacheDir/miniaturas/<id>.webp): Android puede vaciarla si le falta espacio, y no pasa nada.
 * Todo el trabajo con archivos va en un hilo aparte y en orden.
 */
object Thumbs {
    private val io = Executors.newSingleThreadExecutor()
    private val main by lazy { Handler(Looper.getMainLooper()) }

    private fun dir(ctx: Context) = File(ctx.applicationContext.cacheDir, "miniaturas")
    private fun file(ctx: Context, id: String) = File(dir(ctx), "$id.webp")

    @Suppress("DEPRECATION")
    private val format = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    fun save(ctx: Context, id: String, b: Bitmap) {
        val app = ctx.applicationContext
        io.execute {
            runCatching {
                val d = dir(app); d.mkdirs()
                val tmp = File(d, "$id.tmp")
                tmp.outputStream().use { if (b.isRecycled) return@runCatching; b.compress(format, 70, it) }
                if (!tmp.renameTo(file(app, id))) tmp.delete()
            }
        }
    }

    /** Carga la miniatura guardada (si la hay) y la entrega en el hilo principal. */
    fun load(ctx: Context, id: String, cb: (Bitmap) -> Unit) {
        val app = ctx.applicationContext
        io.execute {
            val b = runCatching { file(app, id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }.getOrNull()
            if (b != null) main.post { cb(b) }
        }
    }

    fun delete(ctx: Context, id: String) {
        val app = ctx.applicationContext
        io.execute { runCatching { file(app, id).delete() } }
    }

    /** Fuera las de pestañas que ya no existen. */
    fun prune(ctx: Context, keep: Set<String>) {
        val app = ctx.applicationContext
        io.execute { runCatching { dir(app).listFiles()?.forEach { if (it.nameWithoutExtension !in keep) it.delete() } } }
    }

    /** «Borrar datos al salir» y borrar datos: todas fuera. */
    fun clear(ctx: Context) {
        val app = ctx.applicationContext
        io.execute { runCatching { dir(app).deleteRecursively() } }
    }
}
