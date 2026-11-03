package es.homelab.brisa

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension

/**
 * Ajustes de seguridad y privacidad (0.9). Todo se aplica en caliente al motor;
 * lo que Gecko no ofrece (banners de cookies, WebRTC) lo hace la extensión interna.
 */
object Privacy {
    val DOH = linkedMapOf(
        "Quad9" to "https://dns.quad9.net/dns-query",
        "Mullvad" to "https://dns.mullvad.net/dns-query",
        "Mullvad (sin anuncios)" to "https://adblock.dns.mullvad.net/dns-query",
        "Cloudflare" to "https://mozilla.cloudflare-dns.com/dns-query",
    )

    // Solo HTTPS: 0 = no, 1 = solo en privadas, 2 = siempre
    var httpsOnly by mutableIntStateOf(2); private set
    // DNS cifrado: 0 = no, 1 = automático (si falla, DNS normal), 2 = estricto
    var dohMode by mutableIntStateOf(1); private set
    var dohUri by mutableStateOf(DOH.getValue("Quad9")); private set
    /** Servidor DNS propio (DoH), p. ej. AdGuard Home o NextDNS. Se guarda aunque no esté elegido. */
    var dohCustom by mutableStateOf(""); private set
    var fingerprint by mutableStateOf(true); private set
    var stripLinks by mutableStateOf(true); private set
    var cookieBanners by mutableStateOf(true); private set
    var gpc by mutableStateOf(true); private set
    var webrtc by mutableStateOf(true); private set
    var clearOnExit by mutableStateOf(false); private set
    var lockPrivate by mutableStateOf(true); private set
    var askDownloads by mutableStateOf(true); private set


    /** Parámetros de rastreo que se quitan de las direcciones. */
    val STRIP = arrayOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id", "utm_name",
        "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid", "ttclid",
        "igshid", "igsh", "mc_eid", "mc_cid", "_hsenc", "_hsmi", "__hssc", "__hstc", "__hsfp", "hsCtaTracking",
        "mkt_tok", "vero_id", "vero_conv", "oly_anon_id", "oly_enc_id", "rb_clickid", "s_cid", "wickedid",
        "si", "_openstat", "ref_src", "ref_url", "spm", "scm",
    )

    /** Dominios que nunca pasan por el DNS cifrado (nombres del homelab). */
    private val LOCAL_DOMAINS = listOf("local", "lan", "home", "home.arpa", "internal", "localdomain")

    private lateinit var prefs: android.content.SharedPreferences

    fun load(ctx: Context) {
        prefs = ctx.getSharedPreferences("privacidad", 0)
        httpsOnly = prefs.getInt("https", 2)
        dohMode = prefs.getInt("doh", 1)
        dohUri = prefs.getString("doh_uri", dohUri)!!
        dohCustom = prefs.getString("doh_propio", "")!!
        fingerprint = prefs.getBoolean("huella", true)
        stripLinks = prefs.getBoolean("enlaces", true)
        cookieBanners = prefs.getBoolean("cookies", true)
        gpc = prefs.getBoolean("gpc", true)
        webrtc = prefs.getBoolean("webrtc", true)
        clearOnExit = prefs.getBoolean("borrar_salir", false)
        lockPrivate = prefs.getBoolean("bloquear_privadas", true)
        askDownloads = prefs.getBoolean("preguntar_descargas", true)
    }

    /** Cambia un ajuste, lo guarda y lo aplica. */
    fun set(ctx: Context, key: String, v: Any) {
        when (key) {
            "https" -> httpsOnly = v as Int
            "doh" -> dohMode = v as Int
            "doh_uri" -> dohUri = v as String
            "doh_propio" -> dohCustom = v as String
            "huella" -> fingerprint = v as Boolean
            "enlaces" -> stripLinks = v as Boolean
            "cookies" -> cookieBanners = v as Boolean
            "gpc" -> gpc = v as Boolean
            "webrtc" -> webrtc = v as Boolean
            "borrar_salir" -> clearOnExit = v as Boolean
            "bloquear_privadas" -> lockPrivate = v as Boolean
            "preguntar_descargas" -> askDownloads = v as Boolean
        }
        prefs.edit().apply {
            when (v) { is Int -> putInt(key, v); is Boolean -> putBoolean(key, v); is String -> putString(key, v) }
        }.apply()
        apply(ctx)
    }

    /** Lleva los ajustes al motor y a la extensión. */
    fun apply(ctx: Context) {
        val s = Engine.get(ctx).settings
        s.setAllowInsecureConnections(when (httpsOnly) {
            0 -> GeckoRuntimeSettings.ALLOW_ALL
            1 -> GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE
            else -> GeckoRuntimeSettings.HTTPS_ONLY
        })
        s.setTrustedRecursiveResolverExcludedDomains(LOCAL_DOMAINS)
        s.setTrustedRecursiveResolverUri(dohUri)
        s.setTrustedRecursiveResolverMode(when (dohMode) {
            0 -> GeckoRuntimeSettings.TRR_MODE_DISABLED
            2 -> GeckoRuntimeSettings.TRR_MODE_ONLY
            else -> GeckoRuntimeSettings.TRR_MODE_FIRST
        })
        s.setFingerprintingProtection(fingerprint)
        s.setFingerprintingProtectionPrivateBrowsing(true)
        s.setGlobalPrivacyControl(gpc)
        s.contentBlocking.setQueryParameterStrippingStripList(*STRIP)
        s.contentBlocking.setQueryParameterStrippingEnabled(stripLinks)
        s.contentBlocking.setQueryParameterStrippingPrivateBrowsingEnabled(true)
        pushToExtension()
    }

    // ---- Extensión: banners de cookies y WebRTC -------------------------------------------------
    private var bgPort: WebExtension.Port? = null

    /** La parte de fondo de la extensión se conecta al arrancar; le mandamos los ajustes. */
    fun attach(ext: WebExtension) {
        ext.setMessageDelegate(object : WebExtension.MessageDelegate {
            override fun onConnect(p: WebExtension.Port) {
                bgPort = p
                p.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(msg: Any, p: WebExtension.Port) {}
                    override fun onDisconnect(p: WebExtension.Port) { if (bgPort === p) bgPort = null }
                })
                pushToExtension()
            }
        }, "brisa")
    }

    private fun pushToExtension() {
        bgPort?.postMessage(JSONObject().put("action", "settings").put("cookies", cookieBanners).put("webrtc", webrtc))
    }

    // ---- Borrar al salir --------------------------------------------------------------------------
    fun clearAll(ctx: Context, done: () -> Unit = {}) {
        History.get(ctx).clear()
        Engine.get(ctx).storageController.clearData(StorageController.ClearFlags.ALL).accept({ done() }, { done() })
    }

    /** Un DNS propio tiene que ser DNS sobre HTTPS: https://servidor/ruta (sin espacios). */
    fun isValidDoh(u: String): Boolean {
        val t = u.trim()
        if (!t.startsWith("https://") || t.contains(' ')) return false
        val host = t.removePrefix("https://").substringBefore('/').substringBefore(':')
        return host.isNotEmpty() && (host.contains('.') || host == "localhost")
    }

    /** Las IPs y nombres de la red local no tienen HTTPS: Gecko no los fuerza (para Proxmox, router…). */
    fun isLocal(host: String): Boolean {
        val t = host.trim().lowercase()
        // IPv6 entre corchetes: [::1], [fd00::1]:8080, [fe80::1]
        if (t.startsWith("[")) {
            val ip = t.removePrefix("[").substringBefore(']')
            return ip == "::1" || ip.startsWith("fc") || ip.startsWith("fd") || ip.startsWith("fe8") || ip.startsWith("fe9") ||
                ip.startsWith("fea") || ip.startsWith("feb")
        }
        val h = t.substringBefore(':').substringBefore('/')
        return h == "localhost" || LOCAL_DOMAINS.any { h.endsWith(".$it") } || Regex("^169\\.254\\.").containsMatchIn(h) ||
            Regex("^(10|127)\\.").containsMatchIn(h) || Regex("^192\\.168\\.").containsMatchIn(h) ||
            Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(h) || Regex("^100\\.(6[4-9]|[7-9]\\d|1[01]\\d|12[0-7])\\.").containsMatchIn(h)
    }

    /**
     * «Continuar sin seguridad»: HTTP se permite solo para la carga de ESA pestaña. Se vuelve a cerrar
     * cuando termina esa carga o a los 15 s, lo que ocurra antes. Mientras tanto, las webs http que
     * intenten abrir otras pestañas se retienen y se cargan después, ya con «Solo HTTPS».
     */
    @Volatile var insecureOnce = false; private set
    @Volatile private var insecureSession: org.mozilla.geckoview.GeckoSession? = null
    private val heldLoads = mutableListOf<Pair<org.mozilla.geckoview.GeckoSession, String>>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var timeout: Runnable? = null

    fun allowInsecureOnce(ctx: Context, s: org.mozilla.geckoview.GeckoSession) {
        insecureOnce = true; insecureSession = s
        Engine.get(ctx).settings.setAllowInsecureConnections(GeckoRuntimeSettings.ALLOW_ALL)
        timeout?.let(main::removeCallbacks)
        timeout = Runnable { closeInsecure(ctx) }.also { main.postDelayed(it, 15_000) }
    }

    fun restoreAfterLoad(ctx: Context, s: org.mozilla.geckoview.GeckoSession) {
        if (insecureOnce && s === insecureSession) closeInsecure(ctx)
    }

    /** Otra pestaña quiere abrir una web http mientras está abierta la excepción: se retiene. */
    fun holdDuringInsecure(s: org.mozilla.geckoview.GeckoSession, uri: String): Boolean {
        if (!insecureOnce || s === insecureSession || !uri.startsWith("http://", true)) return false
        if (isLocal(History.hostOf(uri))) return false
        synchronized(heldLoads) { heldLoads += s to uri }
        return true
    }

    private fun closeInsecure(ctx: Context) {
        if (!insecureOnce) return
        insecureOnce = false; insecureSession = null
        timeout?.let(main::removeCallbacks); timeout = null
        apply(ctx)
        val held = synchronized(heldLoads) { heldLoads.toList().also { heldLoads.clear() } }
        held.forEach { (ses, u) -> if (ses.isOpen) ses.loadUri(u) }
    }

}
