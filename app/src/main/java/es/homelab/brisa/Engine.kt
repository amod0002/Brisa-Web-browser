package es.homelab.brisa

import androidx.compose.material.icons.outlined.LockOpen
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
            .aboutConfigEnabled(false)
            .loginAutofillEnabled(true)
            .automaticFontSizeAdjustment(false)
            .fontSizeFactor(ctx.getSharedPreferences("ajustes", 0).getFloat("texto", 1f))
            .build()
    ).also { rt ->
        runtime = rt
        Passwords.init(ctx)
        Report.load(ctx)
        rt.autocompleteStorageDelegate = Passwords
        Settings.load(ctx)
        // Extensión propia (assets/extensions/brisa): iconos y modo lectura.
        rt.webExtensionController.ensureBuiltIn("resource://android/assets/extensions/brisa/", "brisa@homelab.es")
            .accept({ ext -> if (ext != null) { builtIn = ext; Privacy.attach(ext); waiting.forEach { it(ext) }; waiting.clear() } }, { })
        Privacy.load(ctx)
        Privacy.apply(ctx)
        // Las extensiones las instala el usuario desde el menú «aA»: se aceptan sin más diálogos.
        rt.webExtensionController.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                ext: WebExtension, perms: Array<String>, origins: Array<String>, data: Array<String>
            ): GeckoResult<WebExtension.PermissionPromptResponse> {
                // uBlock Origin lo instala Brisa: sin preguntar. Cualquier otra extensión, solo si el usuario acepta.
                if (ext.id == UBLOCK_ID) return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, false, false))
                val r = GeckoResult<WebExtension.PermissionPromptResponse>()
                val h = Tab.tabHost as? PromptHost ?: return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(false, false, false))
                var answered = false
                fun answer(ok: Boolean) { if (!answered) { answered = true; r.complete(WebExtension.PermissionPromptResponse(ok, false, false)) } }
                val name = ext.metaData.name ?: ext.id
                h.show { close ->
                    androidx.compose.material3.AlertDialog(onDismissRequest = { answer(false); close() },
                        title = { androidx.compose.material3.Text("¿Instalar la extensión «$name»?") },
                        text = { androidx.compose.material3.Text(
                            (if (origins.any { it == "<all_urls>" || it.startsWith("*://*/") }) "Podrá leer y cambiar todo lo que veas en todas las webs.\n\n" else "") +
                            "Permisos: " + (perms + origins).joinToString(", ").ifEmpty { "ninguno" }) },
                        confirmButton = { androidx.compose.material3.TextButton({ answer(true); close() }) { androidx.compose.material3.Text("Instalar") } },
                        dismissButton = { androidx.compose.material3.TextButton({ answer(false); close() }) { androidx.compose.material3.Text("Cancelar") } })
                }
                return r
            }
            override fun onUpdatePrompt(
                ext: WebExtension, perms: Array<String>, origins: Array<String>, data: Array<String>
            ): GeckoResult<AllowOrDeny> =
                // Una actualización que pide permisos nuevos solo se acepta sola para uBlock
                GeckoResult.fromValue(if (ext.id == UBLOCK_ID) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
        }
    }

    /** Sesión ya abierta y esperando: la siguiente pestaña nueva arranca sin esperar a Gecko. */
    private var spare: GeckoSession? = null
    fun takeSpare(ctx: Context): GeckoSession? = spare.also { spare = null; android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ warm(ctx) }, 1500) }
    fun warm(ctx: Context) {
        if (spare == null) spare = GeckoSession(GeckoSessionSettings.Builder().build()).apply { open(get(ctx)) }
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

    /** Primer arranque: instala uBlock Origin sin preguntar (es lo que más acelera la carga). Solo se intenta una vez. */
    fun autoUblock(ctx: Context, done: (Boolean) -> Unit) {
        val p = ctx.getSharedPreferences("ajustes", 0)
        if (p.getBoolean("ublock_auto", false)) return
        hasUblock(ctx) { has ->
            if (has) { p.edit().putBoolean("ublock_auto", true).apply(); return@hasUblock }
            installUblock(ctx) { ok -> if (ok) p.edit().putBoolean("ublock_auto", true).apply(); done(ok) }
        }
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
class Tab(private val ctx: Context, val private: Boolean, startUrl: String?, restore: String? = null, openNow: Boolean = true, group: String? = null,
          lazy: Boolean = false) {
    /** Pestaña restaurada que aún no se ha cargado: se carga la primera vez que se muestra. */
    private var pending: (() -> Unit)? = null
    fun wake() { pending?.let { pending = null; it() } }
    /** Grupo de pestañas (null = sin grupo). */
    var group by mutableStateOf(group)
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
    /** Permiso de rastreo de la web actual. ALLOW = protección desactivada en esta web. */
    var trackingPerm by mutableStateOf<GeckoSession.PermissionDelegate.ContentPermission?>(null)
    val shieldOff get() = trackingPerm?.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
    fun toggleShield() {
        val p = trackingPerm ?: return
        val v = if (shieldOff) GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                else GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
        Engine.get(ctx).storageController.setPermission(p, v)
        session.reload()
    }
    var reading by mutableStateOf(false)
    private var port: WebExtension.Port? = null
    /** Desplazamiento acumulado: la barra se encoge al bajar y vuelve al subir. */
    var scrollDelta by mutableIntStateOf(0)

    val session: GeckoSession = ((if (!private && openNow) Engine.takeSpare(ctx) else null) ?: GeckoSession(
        GeckoSessionSettings.Builder().usePrivateMode(private).build()
    )).apply {
        progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(s: GeckoSession, u: String) { url = u; progress = 5; blocked = 0; readerable = false; reading = false }
            override fun onProgressChange(s: GeckoSession, p: Int) { progress = p }
            override fun onPageStop(s: GeckoSession, ok: Boolean) { progress = 100; Privacy.restoreAfterLoad(ctx, s) }
            override fun onSessionStateChange(s: GeckoSession, st: GeckoSession.SessionState) { if (!private) state = st.toString() }
        }
        contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(s: GeckoSession, t: String?) {
                title = t ?: ""; if (!private) History.get(ctx).title(url, title)
            }
            override fun onExternalResponse(s: GeckoSession, r: WebResponse) { Downloads.ask(ctx, r, url) }
            override fun onContextMenu(s: GeckoSession, x: Int, y: Int, e: GeckoSession.ContentDelegate.ContextElement) {
                if (e.linkUri != null || e.srcUri != null) tabHost?.contextMenu(this@Tab, e)
            }
            override fun onFullScreen(s: GeckoSession, on: Boolean) { tabHost?.fullScreen(on) }
        }
        if (!private) historyDelegate = object : GeckoSession.HistoryDelegate {
            override fun onVisited(s: GeckoSession, u: String, last: String?, flags: Int): GeckoResult<Boolean> {
                if (flags and GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL != 0) History.get(ctx).visit(u)
                return GeckoResult.fromValue(true)
            }
        }
        navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(s: GeckoSession, v: Boolean) { canBack = v }
            // Mientras otra pestaña tiene «Continuar sin seguridad», esta no puede abrir webs http (se retienen)
            override fun onLoadRequest(s: GeckoSession, req: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? =
                if (Privacy.holdDuringInsecure(s, req.uri)) GeckoResult.fromValue(AllowOrDeny.DENY) else null
            // Gecko nos da el permiso de «rastreo» de cada web: sirve para el escudo por web.
            override fun onLocationChange(s: GeckoSession, u: String?, perms: List<GeckoSession.PermissionDelegate.ContentPermission>, gesture: Boolean) {
                trackingPerm = perms.firstOrNull { it.permission == GeckoSession.PermissionDelegate.PERMISSION_TRACKING }
            }
            // Solo HTTPS: la web no tiene versión segura → preguntar en vez de mostrar un error.
            override fun onLoadError(s: GeckoSession, uri: String?, e: org.mozilla.geckoview.WebRequestError): GeckoResult<String>? {
                if (e.code != org.mozilla.geckoview.WebRequestError.ERROR_HTTPS_ONLY || uri == null) return null
                val host = History.hostOf(uri)
                (tabHost as? PromptHost)?.show { close ->
                    androidx.compose.material3.AlertDialog(onDismissRequest = { close() },
                        icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.LockOpen, null) },
                        title = { androidx.compose.material3.Text("$host no tiene conexión segura") },
                        text = { androidx.compose.material3.Text("Lo que envíes o veas en esta web podría leerlo alguien de tu red. Brisa la ha bloqueado porque tienes activado «Solo HTTPS».") },
                        confirmButton = { androidx.compose.material3.TextButton({ close(); if (canBack) s.goBack() }) { androidx.compose.material3.Text("Volver") } },
                        dismissButton = { androidx.compose.material3.TextButton({
                            close(); Privacy.allowInsecureOnce(ctx, s); s.loadUri(uri.replaceFirst("https://", "http://"))
                        }) { androidx.compose.material3.Text("Continuar sin seguridad") } })
                }
                return null
            }
            override fun onCanGoForward(s: GeckoSession, v: Boolean) { canForward = v }
            // Enlaces target=_blank y window.open: pestaña nueva junto a esta.
            override fun onNewSession(s: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val t: Tab = Tab(ctx, private, uri, openNow = false)
                tabHost?.openTab(t)
                return GeckoResult.fromValue(t.session)
            }
        }
        contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(s: GeckoSession, e: ContentBlocking.BlockEvent) { blocked++; if (!private) Report.add(host) }
        }
        scrollDelegate = object : GeckoSession.ScrollDelegate {
            var last = 0
            override fun onScrollChanged(s: GeckoSession, x: Int, y: Int) {
                scrollDelta = (y - last).coerceIn(-50, 50).let { d -> if (y < 40) -100 else d }
                last = y
            }
        }
        // Tablet: las webs se piden en versión de escritorio (la pantalla ya es ancha, así que el viewport sigue siendo el del dispositivo)
        if (Settings.isTablet && Settings.desktopTablet) {
            desktop = true
            settings.userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        }
        promptDelegate = Prompts(ctx) { tabHost as? PromptHost }
        permissionDelegate = Permissions { tabHost as? PromptHost }
        if (openNow && !isOpen) open(Engine.get(ctx)) else Engine.get(ctx)
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
                                    val icon = j.optString("icon")
                                    if (!private && WebIcons.allowed(h, icon)) WebIcons.fetch(ctx, h, icon)
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
            val go = {
                val st = restore?.let { runCatching { GeckoSession.SessionState.fromString(it) }.getOrNull() }
                if (st != null) restoreState(st) else if (!startUrl.isNullOrBlank()) loadUri(startUrl)
            }
            if (lazy) pending = go else go()
        }
    }

    val isHome get() = url.isBlank() || url == "about:blank"
    val host get() = url.substringAfter("://").substringBefore("/").removePrefix("www.")

    fun load(input: String) { pending = null; val u = toUrl(input, Settings.search); session.loadUri(u); url = u }

    /** Modo lectura: lo monta la extensión dentro de la página; salir = recargar el original. */
    fun toggleReader() {
        if (reading) { reading = false; session.reload() }
        else port?.postMessage(JSONObject().put("action", "reader"))
    }

    fun toggleDesktop() {
        desktop = !desktop
        session.settings.userAgentMode =
            if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        // En tablet la pantalla ya es ancha: solo se cambia el «user agent», no el ancho de la página
        session.settings.viewportMode =
            if (desktop && !Settings.isTablet) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
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
        var tabHost: TabHost? = null
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
