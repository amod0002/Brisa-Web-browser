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
        Permissions.init(ctx.applicationContext, rt)
        Passwords.init(ctx)
        rt.setWebNotificationDelegate(WebNotifs(ctx.applicationContext))
        Report.load(ctx)
        rt.autocompleteStorageDelegate = Passwords
        Settings.load(ctx)
        // Extensión propia (assets/extensions/brisa): iconos y modo lectura.
        rt.webExtensionController.ensureBuiltIn("resource://android/assets/extensions/brisa/", "brisa@homelab.es")
            .accept({ ext -> if (ext != null) { builtIn = ext; Privacy.attach(ext); waiting.forEach { it(ext) }; waiting.clear() } }, { })
        Privacy.load(ctx)
        Privacy.apply(ctx)
        // #59: extensión aparte para avisar a Gecko de los cambios de red. Si no carga, solo falla ella.
        rt.webExtensionController.ensureBuiltIn("resource://android/assets/extensions/brisanet/", "red@homelab.es")
            .accept({ ext -> if (ext != null) NetWatch.attach(ext) }, { e -> android.util.Log.w("Brisa", "brisanet", e) })
        NetWatch.start(ctx.applicationContext)
        // #16: extensiones del usuario (botón, ventanita, pestañas propias y opciones)
        Extensions.init(ctx, rt.webExtensionController)
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
    /** Ajustes → «Borrar historial, cookies y caché»: la misma limpieza completa que «Borrar datos al salir». */
    fun clearData(ctx: Context, done: () -> Unit) = Privacy.clearAll(ctx, done)

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

    /** Página de ajustes y filtros de uBlock Origin (moz-extension://…/dashboard.html), si está instalado. */
    fun ublockOptions(ctx: Context, cb: (String?) -> Unit) {
        get(ctx).webExtensionController.list().accept({ l ->
            val u = l?.firstOrNull { it.id == UBLOCK_ID }
            cb(u?.metaData?.optionsPageUrl ?: u?.metaData?.baseUrl?.let { it.trimEnd('/') + "/dashboard.html" })
        }, { cb(null) })
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
    /** La web pide cerrar su ventana (window.close(), #38): se cierra y se vuelve a la que la abrió. */
    fun closeTab(tab: Tab)
    /** La web pide ponerse delante (window.focus(), p. ej. al tocar su notificación, #39). */
    fun focusTab(tab: Tab)
}

/**
 * [restore]: estado guardado (historial atrás/adelante incluido).
 * [openNow] = false cuando Gecko nos pide una sesión para un enlace «abrir en ventana nueva»: él la abre.
 */
class Tab(private val ctx: Context, val private: Boolean, startUrl: String?, restore: String? = null, openNow: Boolean = true, group: String? = null,
          lazy: Boolean = false, id: String? = null) {
    /** Id estable (se guarda en pestanas.json): nombre de su miniatura en disco (#58). */
    val id: String = id ?: java.util.UUID.randomUUID().toString()
    /** La página ya ha pintado algo: hasta entonces se tapa la vista para no ver la pestaña anterior (#55). */
    var painted by mutableStateOf(false)
    /** Pestaña restaurada que aún no se ha cargado: se carga la primera vez que se muestra. */
    private var pending: (() -> Unit)? = null
    /** Se muestra por primera vez: abre su sesión en el motor (#36) y carga la página. Antes se espera a que se
     *  hayan retirado los «Solo esta vez» de la vez anterior (#40). */
    fun wake() { ensureOpen(); pending?.let { pending = null; Permissions.whenReady(it) } }
    /** Las pestañas restauradas no abren su sesión hasta que se ven: con 30 pestañas el arranque es como con 1. */
    fun ensureOpen() { if (!session.isOpen && !byGecko) session.open(Engine.get(ctx)) }
    /** Ventanas de window.open: la sesión la abre Gecko, no Brisa (#45). Si en 1 s no lo ha hecho, la abre Brisa. */
    private val geckoOpens = !openNow && !lazy
    private val byGecko get() = geckoOpens && openedByGeckoPending
    private var openedByGeckoPending = true
    suspend fun awaitOpen() {
        if (session.isOpen || !geckoOpens) { ensureOpen(); return }
        kotlinx.coroutines.withTimeoutOrNull(1000) { while (!session.isOpen) kotlinx.coroutines.delay(16) }
        openedByGeckoPending = false
        ensureOpen()
    }
    /** Pausar/reanudar sin tocar sesiones que aún no se han abierto. */
    fun active(on: Boolean) { if (session.isOpen) session.setActive(on) }
    /** Pestaña que la abrió con window.open (para volver a ella al cerrarse, #38). */
    var opener: Tab? = null
    /** Último estado conocido, también en privadas (solo en memoria): para recuperar la pestaña si Gecko se cae (#36). */
    private var lastState: GeckoSession.SessionState? = null
    private var crashes = 0
    private var lastCrash = 0L
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
    /** Ventanas de la web (alert, confirm…), con el bloqueo de diálogos en bucle (#21) por pestaña. */
    private val prompts = Prompts(ctx, { tabHost as? PromptHost }) { url }
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
    /** Barras encogidas por el scroll. Solo cambia al cambiar de sentido, no en cada evento (#47). */
    var barsHidden by mutableStateOf(false)
    private var lastGesture = 0L

    val session: GeckoSession = ((if (!private && openNow) Engine.takeSpare(ctx) else null) ?: GeckoSession(
        GeckoSessionSettings.Builder().usePrivateMode(private).build()
    )).apply {
        progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(s: GeckoSession, u: String) { url = u; progress = 5; blocked = 0; readerable = false; reading = false; prompts.resetDialogs(u) }
            override fun onProgressChange(s: GeckoSession, p: Int) { progress = p }
            override fun onPageStop(s: GeckoSession, ok: Boolean) { progress = 100; if (!isHome) painted = true; Privacy.restoreAfterLoad(ctx, s) }
            override fun onSessionStateChange(s: GeckoSession, st: GeckoSession.SessionState) { lastState = st; if (!private) state = st.toString() }
        }
        contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(s: GeckoSession, t: String?) {
                title = t ?: ""; if (!private) History.get(ctx).title(url, title)
            }
            override fun onExternalResponse(s: GeckoSession, r: WebResponse) {
                // #16: un .xpi (el botón «Añadir» de addons.mozilla.org) se instala como extensión, no se descarga
                if (Extensions.isXpi(r.headers["Content-Type"] ?: r.headers["content-type"], r.uri)) {
                    runCatching { r.body?.close() }
                    Extensions.installUrl(ctx, r.uri); return
                }
                Downloads.ask(ctx, r, url, private)
            }
            override fun onContextMenu(s: GeckoSession, x: Int, y: Int, e: GeckoSession.ContentDelegate.ContextElement) {
                if (e.linkUri != null || e.srcUri != null) tabHost?.contextMenu(this@Tab, e)
            }
            override fun onFirstContentfulPaint(s: GeckoSession) { painted = true }
            override fun onFullScreen(s: GeckoSession, on: Boolean) { tabHost?.fullScreen(on) }
            override fun onCloseRequest(s: GeckoSession) { tabHost?.closeTab(this@Tab) }
            override fun onFocusRequest(s: GeckoSession) { tabHost?.focusTab(this@Tab) }
            // El proceso que pinta las webs se ha caído (onCrash: la sesión queda cerrada) o Android lo ha
            // matado por memoria (onKill): se recupera la pestaña en vez de dejarla en negro (#36)
            override fun onCrash(s: GeckoSession) { recover(reopen = true) }
            override fun onKill(s: GeckoSession) { recover(reopen = false) }
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
            override fun onLoadRequest(s: GeckoSession, req: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                if (Privacy.holdDuringInsecure(s, req.uri)) return GeckoResult.fromValue(AllowOrDeny.DENY)
                // Toque del usuario ahora o hace muy poco (las webs suelen redirigir a la app tras el toque)
                val now = System.currentTimeMillis()
                if (req.hasUserGesture) lastGesture = now
                return ExternalApps.check(ctx, this@Tab, req, gesture = now - lastGesture < 4000)
            }
            // Gecko nos da el permiso de «rastreo» de cada web: sirve para el escudo por web.
            override fun onLocationChange(s: GeckoSession, u: String?, perms: List<GeckoSession.PermissionDelegate.ContentPermission>, gesture: Boolean) {
                // Permisos de «Solo esta vez»: se retiran al irse a otra web (#24)
                Permissions.leaveSite(ctx, s, History.hostOf(u ?: ""))
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
                val t: Tab = Tab(ctx, private, uri, openNow = false).also { it.opener = this@Tab }
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
                val d = if (y < 40) -100 else y - last
                last = y
                val hide = when { d > 8 -> true; d < -8 -> false; else -> return }
                if (hide != barsHidden) barsHidden = hide
            }
        }
        // Tablet: las webs se piden en versión de escritorio (la pantalla ya es ancha, así que el viewport sigue siendo el del dispositivo)
        if (Settings.isTablet && Settings.desktopTablet) {
            desktop = true
            settings.userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        }
        promptDelegate = prompts
        permissionDelegate = Permissions { tabHost as? PromptHost }
        if (openNow && !isOpen && !lazy) open(Engine.get(ctx)) else Engine.get(ctx)
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
            if (lazy) pending = go else Permissions.whenReady(go)
        }
    }

    val isHome get() = url.isBlank() || url == "about:blank"
    val host get() = url.substringAfter("://").substringBefore("/").removePrefix("www.")

    /** Lo que se escribe a mano en la barra: pasa por toUrl() (dirección, red local o búsqueda). */
    fun load(input: String) { pending = null; ensureOpen(); val u = toUrl(input, Settings.search); session.loadUri(u); url = u }

    /**
     * Direcciones que YA son direcciones (imágenes data:/blob:, favoritos, historial, páginas de extensiones):
     * se abren tal cual y nunca se mandan al buscador. Gecko decide si se pueden mostrar.
     */
    fun open(address: String) { pending = null; ensureOpen(); session.loadUri(address); url = address }

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

    fun close() {
        Permissions.leaveSite(ctx, session, null); thumb = null; opener = null; if (session.isOpen) session.close()
        if (!private) Thumbs.delete(ctx, id)
    }

    /** Recuperar la pestaña tras caerse su proceso. Si no es la visible, se recarga cuando se vuelva a mirar. */
    private fun recover(reopen: Boolean) {
        val now = System.currentTimeMillis()
        crashes = if (now - lastCrash < 15_000) crashes + 1 else 1
        lastCrash = now
        painted = false
        val st = lastState; val u = url
        val go = {
            if (!session.isOpen) session.open(Engine.get(ctx))
            // Si se cae una y otra vez, no se insiste: se queda en blanco y el usuario decide
            if (crashes <= 2) { if (st != null) session.restoreState(st) else if (u.isNotBlank()) session.loadUri(u) }
        }
        if (reopen && this !== visible) { pending = go; return }
        // Con Brisa en segundo plano no se recarga nada (Android está liberando memoria): se hará al volver (#44)
        if (this === visible && foreground) {
            go()
            android.widget.Toast.makeText(ctx, if (crashes <= 2) "Esta pestaña se había cerrado. Recargada." else "Esta pestaña se cierra una y otra vez: no se ha recargado.", android.widget.Toast.LENGTH_SHORT).show()
        } else pending = go
    }

    /** Miniatura para la cuadrícula a un tercio de tamaño: unos 1 MB en vez de 10 (#36). */
    fun saveThumb(b: Bitmap?) {
        if (b == null) return
        val small = runCatching { Bitmap.createScaledBitmap(b, (b.width / 3).coerceAtLeast(1), (b.height / 3).coerceAtLeast(1), true) }.getOrNull() ?: return
        if (small !== b) b.recycle()
        thumb = small
        // En disco solo las normales, nunca las privadas (#58)
        if (!private) Thumbs.save(ctx, id, small)
    }

    companion object {
        /** Brisa está delante (entre onResume y onPause). */
        var foreground = false
        var tabHost: TabHost? = null
        /** Pestaña que se está viendo (para saber el esquema y puerto de la página al rellenar contraseñas). */
        @Volatile var visible: Tab? = null
        /** Texto escrito → URL: dirección si lo parece, si no búsqueda en DuckDuckGo. */
        private val LOCAL_SUFFIXES = listOf("local", "lan", "home", "home.arpa", "internal", "localdomain")

        fun toUrl(s: String, search: String = "https://duckduckgo.com/?q=%s"): String {
            val t = s.trim()
            val host = t.substringBefore('/').substringBefore(':').lowercase()
            return when {
                // Solo se abren webs y páginas internas de Gecko; file://, content://, intent://… se buscan
                t.matches(Regex("^(https?)://.*", RegexOption.IGNORE_CASE)) -> t
                t.matches(Regex("^about:(blank|reader|logins|support)$", RegexOption.IGNORE_CASE)) -> t.lowercase()
                t.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) && !t.matches(Regex("^[a-zA-Z0-9-]+:\\d+(/.*)?$")) &&
                    !t.startsWith("localhost:", true) -> search.replace("%s", java.net.URLEncoder.encode(t, "UTF-8"))
                // IPv6 entre corchetes: [fd00::1]:8080
                t.matches(Regex("^\\[[0-9a-fA-F:.]+](:\\d+)?(/.*)?$")) -> "http://$t"
                // IPv4
                t.matches(Regex("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?(/.*)?$")) -> "http://$t"
                // localhost, nombre:puerto (proxmox:8006), nombre/ruta (nas/admin)
                t.matches(Regex("^localhost(:\\d+)?(/.*)?$", RegexOption.IGNORE_CASE)) -> "http://$t"
                t.matches(Regex("^[a-zA-Z0-9-]+:\\d+(/.*)?$")) -> "http://$t"
                t.matches(Regex("^[a-zA-Z0-9-]+/\\S*$")) -> "http://$t"
                // dominios de red local: .lan, .local, .home.arpa…
                !t.contains(' ') && LOCAL_SUFFIXES.any { host.endsWith(".$it") } -> "http://$t"
                !t.contains(' ') && t.contains('.') -> "https://$t"
                else -> search.replace("%s", java.net.URLEncoder.encode(t, "UTF-8"))
            }
        }
    }
}
