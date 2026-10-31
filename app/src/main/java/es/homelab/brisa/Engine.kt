package es.homelab.brisa

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.mozilla.geckoview.WebResponse
import org.mozilla.geckoview.StorageController
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/** Un único motor Gecko para toda la app: arrancarlo es lo más caro, así que se hace una vez. */
object Engine {
    private var runtime: GeckoRuntime? = null

    fun get(ctx: Context): GeckoRuntime = runtime ?: GeckoRuntime.create(
        ctx.applicationContext,
        GeckoRuntimeSettings.Builder()
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    .antiTracking(ContentBlocking.AntiTracking.STRICT)
                    .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                    .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                    .build()
            )
            .aboutConfigEnabled(true)
            .automaticFontSizeAdjustment(false)
            .fontSizeFactor(ctx.getSharedPreferences("ajustes", 0).getFloat("texto", 1f))
            .build()
    ).also { rt ->
        runtime = rt
        Settings.load(ctx)
        // Extensión propia (assets/extensions/brisa): iconos y modo lectura.
        rt.webExtensionController.ensureBuiltIn("resource://android/assets/extensions/brisa/", "brisa@homelab.es")
            .accept({ ext -> if (ext != null) { builtIn = ext; waiting.forEach { it(ext) }; waiting.clear() } }, { })
        // Las extensiones las instala el usuario desde el menú «aA»: se aceptan sin más diálogos.
        rt.webExtensionController.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                ext: WebExtension, perms: Array<String>, origins: Array<String>, data: Array<String>
            ): GeckoResult<WebExtension.PermissionPromptResponse> =
                GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, true, false))
            override fun onUpdatePrompt(
                ext: WebExtension, perms: Array<String>, origins: Array<String>, data: Array<String>
            ): GeckoResult<AllowOrDeny> = GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }
    }

    var builtIn: WebExtension? = null
        private set
    private val waiting = mutableListOf<(WebExtension) -> Unit>()
    fun withBuiltIn(cb: (WebExtension) -> Unit) { builtIn?.let(cb) ?: waiting.add(cb) }

    /** Borra cookies, caché y datos de todas las webs, y el historial. */
    fun clearData(ctx: Context, done: () -> Unit) {
        History.get(ctx).clear()
        get(ctx).storageController.clearData(StorageController.ClearFlags.ALL).accept({ done() }, { done() })
    }

    /** Tamaño del texto de las webs (0,8 – 1,6), para todas las pestañas. */
    fun setTextSize(ctx: Context, f: Float) {
        get(ctx).settings.setFontSizeFactor(f)
        ctx.getSharedPreferences("ajustes", 0).edit().putFloat("texto", f).apply()
    }
    fun textSize(ctx: Context) = ctx.getSharedPreferences("ajustes", 0).getFloat("texto", 1f)

    const val UBLOCK = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
    const val UBLOCK_ID = "uBlock0@raymondhill.net"

    fun hasUblock(ctx: Context, cb: (Boolean) -> Unit) {
        get(ctx).webExtensionController.list().accept({ l -> cb(l?.any { it.id == UBLOCK_ID } == true) }, { cb(false) })
    }

    fun installUblock(ctx: Context, done: (Boolean) -> Unit) {
        get(ctx).webExtensionController.install(UBLOCK).accept({ done(true) }, { done(false) })
    }
}

/** Una pestaña: su sesión Gecko y el estado que pinta la interfaz. */
/** Lo que una pestaña necesita pedirle a la pantalla principal. */
interface TabHost {
    fun openTab(tab: Tab)
    fun contextMenu(tab: Tab, e: GeckoSession.ContentDelegate.ContextElement)
    fun fullScreen(on: Boolean)
}

/**
 * [restore]: estado guardado (historial atrás/adelante incluido).
 * [openNow] = false cuando Gecko nos pide una sesión para un enlace «abrir en ventana nueva»: él la abre.
 */
class Tab(private val ctx: Context, val private: Boolean, startUrl: String?, restore: String? = null, openNow: Boolean = true) {
    var state: String? = restore
        private set
    var url by mutableStateOf(startUrl ?: "")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var blocked by mutableIntStateOf(0)
    var desktop by mutableStateOf(false)
    var thumb by mutableStateOf<Bitmap?>(null)
    var readerable by mutableStateOf(false)
    var reading by mutableStateOf(false)
    private var port: WebExtension.Port? = null
    /** Desplazamiento acumulado: la barra se encoge al bajar y vuelve al subir. */
    var scrollDelta by mutableIntStateOf(0)

    val session = GeckoSession(
        GeckoSessionSettings.Builder().usePrivateMode(private).build()
    ).apply {
        progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(s: GeckoSession, u: String) { url = u; progress = 5; blocked = 0; readerable = false; reading = false }
            override fun onProgressChange(s: GeckoSession, p: Int) { progress = p }
            override fun onPageStop(s: GeckoSession, ok: Boolean) { progress = 100 }
            override fun onSessionStateChange(s: GeckoSession, st: GeckoSession.SessionState) { if (!private) state = st.toString() }
        }
        contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(s: GeckoSession, t: String?) {
                title = t ?: ""; if (!private) History.get(ctx).title(url, title)
            }
            override fun onExternalResponse(s: GeckoSession, r: WebResponse) { Downloads.save(ctx, r) }
            override fun onContextMenu(s: GeckoSession, x: Int, y: Int, e: GeckoSession.ContentDelegate.ContextElement) {
                if (e.linkUri != null || e.srcUri != null) host?.contextMenu(this@Tab, e)
            }
            override fun onFullScreen(s: GeckoSession, on: Boolean) { host?.fullScreen(on) }
        }
        if (!private) historyDelegate = object : GeckoSession.HistoryDelegate {
            override fun onVisited(s: GeckoSession, u: String, last: String?, flags: Int): GeckoResult<Boolean> {
                if (flags and GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL != 0) History.get(ctx).visit(u)
                return GeckoResult.fromValue(true)
            }
        }
        navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(s: GeckoSession, v: Boolean) { canBack = v }
            override fun onCanGoForward(s: GeckoSession, v: Boolean) { canForward = v }
            // Enlaces target=_blank y window.open: pestaña nueva junto a esta.
            override fun onNewSession(s: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val t = Tab(ctx, private, uri, openNow = false)
                host?.openTab(t)
                return GeckoResult.fromValue(t.session)
            }
        }
        contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(s: GeckoSession, e: ContentBlocking.BlockEvent) { blocked++ }
        }
        scrollDelegate = object : GeckoSession.ScrollDelegate {
            var last = 0
            override fun onScrollChanged(s: GeckoSession, x: Int, y: Int) {
                scrollDelta = (y - last).coerceIn(-50, 50).let { d -> if (y < 40) -100 else d }
                last = y
            }
        }
        if (openNow) open(Engine.get(ctx)) else Engine.get(ctx)
        val session = this
        Engine.withBuiltIn { ext ->
            session.webExtensionController.setMessageDelegate(ext, object : WebExtension.MessageDelegate {
                override fun onConnect(p: WebExtension.Port) {
                    port = p
                    p.setDelegate(object : WebExtension.PortDelegate {
                        override fun onPortMessage(msg: Any, p: WebExtension.Port) {
                            val j = msg as? JSONObject ?: return
                            when (j.optString("type")) {
                                "page" -> {
                                    readerable = j.optBoolean("readerable")
                                    val h = History.hostOf(j.optString("url"))
                                    j.optString("icon").takeIf { it.startsWith("http") }?.let { WebIcons.fetch(ctx, h, it) }
                                }
                                "reader" -> reading = j.optBoolean("ok")
                            }
                        }
                        override fun onDisconnect(p: WebExtension.Port) { if (port === p) port = null }
                    })
                }
            }, "brisa")
        }
        if (openNow) {
            val st = restore?.let { runCatching { GeckoSession.SessionState.fromString(it) }.getOrNull() }
            if (st != null) restoreState(st) else if (!startUrl.isNullOrBlank()) loadUri(startUrl)
        }
    }

    val isHome get() = url.isBlank() || url == "about:blank"
    val host get() = url.substringAfter("://").substringBefore("/").removePrefix("www.")

    fun load(input: String) { val u = toUrl(input, Settings.search); session.loadUri(u); url = u }

    /** Modo lectura: lo monta la extensión dentro de la página; salir = recargar el original. */
    fun toggleReader() {
        if (reading) { reading = false; session.reload() }
        else port?.postMessage(JSONObject().put("action", "reader"))
    }

    fun toggleDesktop() {
        desktop = !desktop
        session.settings.userAgentMode =
            if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        session.settings.viewportMode =
            if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        session.reload()
    }

    // Buscar en la página
    var findText by mutableStateOf<String?>(null)
    var findResult by mutableStateOf("")
    fun find(q: String, back: Boolean = false) {
        if (q.isEmpty()) { session.finder.clear(); findResult = ""; return }
        val flags = if (back) GeckoSession.FINDER_FIND_BACKWARDS else 0
        val next = q == lastFind
        lastFind = q
        session.finder.find(if (next) null else q, flags).accept({ r ->
            findResult = if (r == null || !r.found) "0/0" else "${r.current}/${r.total}"
        }, { })
    }
    private var lastFind = ""
    fun endFind() { session.finder.clear(); findText = null; findResult = ""; lastFind = "" }

    fun close() = session.close()

    companion object {
        var host: TabHost? = null
        /** Texto escrito → URL: dirección si lo parece, si no búsqueda en DuckDuckGo. */
        fun toUrl(s: String, search: String = "https://duckduckgo.com/?q=%s"): String {
            val t = s.trim()
            return when {
                t.matches(Regex("^[a-zA-Z]+://.*")) -> t
                t.matches(Regex("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?(/.*)?$")) -> "http://$t"
                !t.contains(' ') && t.contains('.') -> "https://$t"
                else -> search.replace("%s", java.net.URLEncoder.encode(t, "UTF-8"))
            }
        }
    }
}
