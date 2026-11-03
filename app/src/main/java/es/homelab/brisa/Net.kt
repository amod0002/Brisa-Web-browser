package es.homelab.brisa

import android.content.Context
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Peticiones propias de Brisa (iconos, sugerencias) hechas con la red de Gecko: mismo DNS cifrado,
 * mismo TLS y misma identificación que las páginas, sin cookies (anónimas) y sin seguir redirecciones.
 * Llamar SIEMPRE desde un hilo de fondo (espera la respuesta).
 */
object Net {
    @Volatile private var exec: GeckoWebExecutor? = null
    private fun executor(ctx: Context) = exec ?: synchronized(this) { exec ?: GeckoWebExecutor(Engine.get(ctx)).also { exec = it } }

    /** IP de la red local o especial: privadas, loopback, link-local, CGNAT, multicast, IPv6 ULA/link-local. */
    fun isLocalAddress(a: InetAddress): Boolean {
        if (a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress || a.isAnyLocalAddress || a.isMulticastAddress) return true
        val b = a.address
        if (a is Inet4Address) return (b[0].toInt() and 0xFF) == 100 && (b[1].toInt() and 0xC0) == 64          // 100.64.0.0/10
        if (a is Inet6Address) return (b[0].toInt() and 0xFE) == 0xFC                                          // fc00::/7
        return false
    }

    /** ¿Este nombre resuelve (con el DNS de Gecko) a alguna IP local? Si no se puede resolver, se trata como local. */
    fun resolvesToLocal(ctx: Context, host: String): Boolean = try {
        val addrs = executor(ctx).resolve(host.removePrefix("[").removeSuffix("]")).poll(4000)
        addrs.isNullOrEmpty() || addrs.any(::isLocalAddress)
    } catch (_: Throwable) { true }

    // Combinar ANONYMOUS y NO_REDIRECTS es válido; lint no reconoce la combinación con «or»
    @android.annotation.SuppressLint("WrongConstant")
    /** GET anónimo; devuelve el cuerpo (máximo [maxBytes]) o null si falla o no es 2xx. */
    fun get(ctx: Context, url: String, maxBytes: Int, timeoutMs: Long = 5000): ByteArray? = try {
        val req = WebRequest.Builder(url).method("GET").build()
        val r = executor(ctx).fetch(req, GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS or GeckoWebExecutor.FETCH_FLAGS_NO_REDIRECTS).poll(timeoutMs)
        if (r == null || r.statusCode !in 200..299) null
        else r.body?.use { inp ->
            val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(16 * 1024); var n: Int
            while (inp.read(buf).also { n = it } >= 0) { out.write(buf, 0, n); if (out.size() > maxBytes) error("demasiado grande") }
            out.toByteArray()
        }
    } catch (_: Throwable) { null }
}
