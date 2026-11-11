package es.homelab.brisa

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.mozilla.geckoview.WebExtension

/**
 * Cambios de red (#59): p. ej. al quitar la VPN la Wi-Fi sigue siendo la misma y Gecko no se entera, así
 * que sigue usando conexiones muertas (la del DNS cifrado y las de las webs) hasta que vence TCP.
 * Brisa escucha la red por defecto de Android y, cuando cambia, avisa a Gecko:
 *  - por la extensión interna «brisanet», que manda dentro de Gecko el aviso de red cambiada
 *    (network:link-status-changed): Gecko comprueba sus conexiones y tira las muertas;
 *  - y reaplica el DNS cifrado (apagar y volver a encender), que vacía su caché y su conexión.
 * Los cambios seguidos se agrupan (1,5 s).
 */
object NetWatch {
    private var started = false
    private lateinit var app: Context
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var lastKey: String? = null
    private var port: WebExtension.Port? = null
    private val fire = Runnable { onChange() }

    fun start(ctx: Context) {
        if (started) return
        started = true
        app = ctx.applicationContext
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(n: Network) = changed(n, cm.getLinkProperties(n))
                override fun onLinkPropertiesChanged(n: Network, lp: LinkProperties) = changed(n, lp)
                override fun onLost(n: Network) = changed(null, null)
            })
        }
    }

    fun attach(ext: WebExtension) {
        ext.setMessageDelegate(object : WebExtension.MessageDelegate {
            override fun onConnect(p: WebExtension.Port) {
                port = p
                p.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(msg: Any, p: WebExtension.Port) {}
                    override fun onDisconnect(p: WebExtension.Port) { if (port === p) port = null }
                })
            }
        }, "brisanet")
    }

    /** Red por defecto + interfaz + servidores DNS: si cambia algo de eso, ha cambiado la red. */
    private fun key(n: Network?, lp: LinkProperties?) =
        if (n == null) "sin red" else "$n|${lp?.interfaceName}|${lp?.dnsServers?.joinToString()}"

    @Synchronized private fun changed(n: Network?, lp: LinkProperties?) {
        val k = key(n, lp)
        if (lastKey == null) { lastKey = k; return } // el primer aviso llega al registrarse: es la red actual
        if (k == lastKey) return
        lastKey = k
        main.removeCallbacks(fire)
        main.postDelayed(fire, 1500)
    }

    private fun onChange() {
        if (lastKey == "sin red") return // se hará cuando vuelva la red
        runCatching { port?.postMessage(JSONObject().put("action", "changed")) }
        Privacy.resetDns(app)
    }
}
