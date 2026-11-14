package es.homelab.brisa

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Extensiones que instala el usuario (#16), además de las internas de Brisa.
 *
 * Solo se instalan extensiones firmadas por Mozilla: es lo que GeckoView exige y no se desactiva.
 * Cada extensión tiene aquí lo que necesita para poder usarse de verdad: su botón y su ventanita
 * (el «browser action» con su popup), abrir pestañas propias y su página de opciones.
 */
object Extensions {
    /** Las del usuario (no las internas), para la pantalla de extensiones. */
    val list = mutableStateListOf<WebExtension>()
    /** Botón de cada extensión (su icono, título y lo que hace al tocarlo), por id. */
    val actions = mutableStateMapOf<String, WebExtension.Action>()
    /** Ventanita abierta de una extensión, si la hay. */
    var popup by mutableStateOf<Popup?>(null)
        private set

    class Popup(val ext: WebExtension, val session: GeckoSession)

    /** Recomendadas: id → (nombre, slug en addons.mozilla.org, para qué sirve). El resto sale como «experimental». */
    val RECOMMENDED = linkedMapOf(
        Engine.UBLOCK_ID to Triple("uBlock Origin", "ublock-origin", "Bloquea anuncios y rastreadores"),
        "{446900e4-71c2-419f-a6a7-df9c091e268b}" to Triple("Bitwarden", "bitwarden-password-manager", "Gestor de contraseñas (también con Vaultwarden)"),
        "addon@darkreader.org" to Triple("Dark Reader", "darkreader", "Modo oscuro en todas las webs"),
        "sponsorBlocker@ajay.app" to Triple("SponsorBlock", "sponsorblock", "Salta los patrocinios de YouTube"),
    )
    private val INTERNAL = setOf("brisa@homelab.es", "red@homelab.es")
    const val AMO = "https://addons.mozilla.org/es-ES/android/"

    private lateinit var app: Context
    private lateinit var controller: WebExtensionController

    fun init(ctx: Context, c: WebExtensionController) {
        app = ctx.applicationContext
        controller = c
        c.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
            override fun onInstalled(extension: WebExtension) { attach(extension); refresh() }
            override fun onReady(extension: WebExtension) { attach(extension) }
            override fun onEnabled(extension: WebExtension) { attach(extension); refresh() }
            override fun onDisabled(extension: WebExtension) { actions.remove(extension.id); closePopupOf(extension); refresh() }
            override fun onUninstalled(extension: WebExtension) { actions.remove(extension.id); closePopupOf(extension); refresh() }
        })
        refresh(attachAll = true)
    }

    private fun isMine(e: WebExtension) = !e.isBuiltIn && e.id !in INTERNAL

    fun refresh(attachAll: Boolean = false) {
        if (!::controller.isInitialized) return
        controller.list().accept({ l ->
            val mine = l.orEmpty().filter(::isMine)
            if (attachAll) mine.forEach(::attach)
            // #69: quien ya tenía uBlock lo tenía desactivado en privadas. Se activa una sola vez
            // (si luego el usuario lo desactiva a mano, se respeta).
            val p = app.getSharedPreferences("ajustes", 0)
            if (attachAll && !p.getBoolean("ublock_privadas", false)) mine.firstOrNull { it.id == Engine.UBLOCK_ID }?.let { u ->
                p.edit().putBoolean("ublock_privadas", true).apply()
                if (!u.metaData.allowedInPrivateBrowsing) controller.setAllowedInPrivateBrowsing(u, true).accept({ refresh() }, { })
            }
            list.clear(); list.addAll(mine.sortedBy { (it.metaData.name ?: it.id).lowercase() })
        }, { })
    }

    /** Lo que la extensión necesita para usarse: botón, ventanita y pestañas propias. */
    private fun attach(e: WebExtension) {
        if (!isMine(e)) return
        e.setActionDelegate(object : WebExtension.ActionDelegate {
            // Botón por defecto de la extensión (session == null). Los cambios por pestaña (p. ej. un contador) aún no se muestran.
            // Por pestaña (session != null): p. ej. el contador de uBlock (#67 #77)
            override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
                if (session == null) actions[extension.id] = action
                else Tab.bySession[session]?.extActions?.put(extension.id, action)
            }
            override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? {
                android.util.Log.d("Brisa", "onTogglePopup ${extension.id}")
                return if (popup?.ext?.id == extension.id) { closePopup(); null } else openPopup(extension)
            }
            override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? {
                android.util.Log.d("Brisa", "onOpenPopup ${extension.id}")
                return openPopup(extension)
            }
        })
        e.setTabDelegate(object : WebExtension.TabDelegate {
            // La extensión abre una pestaña suya (bienvenida, ajustes, una web…): Gecko abre la sesión
            override fun onNewTab(source: WebExtension, createDetails: WebExtension.CreateTabDetails): GeckoResult<GeckoSession>? {
                val host = Tab.tabHost ?: return null
                val t = Tab(tabCtx(), false, createDetails.url, openNow = false)
                host.openTab(t)
                return GeckoResult.fromValue(t.session)
            }
            override fun onOpenOptionsPage(source: WebExtension) { openOptions(source) }
        })
    }

    private fun openPopup(e: WebExtension): GeckoResult<GeckoSession> {
        closePopup()
        val s = GeckoSession(GeckoSessionSettings.Builder().build())
        s.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(session: GeckoSession) { closePopup() } // window.close() desde la ventanita
        }
        s.navigationDelegate = object : GeckoSession.NavigationDelegate {
            // Un enlace de la ventanita que abre otra ventana: pestaña nueva, y se cierra la ventanita
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val host = Tab.tabHost ?: return null
                // Desde una privada, la pestaña también es privada (#75)
                val t = Tab(tabCtx(), Tab.visible?.private == true, uri, openNow = false)
                host.openTab(t); closePopup()
                return GeckoResult.fromValue(t.session)
            }
        }
        s.open(Engine.get(app))
        popup = Popup(e, s)
        return GeckoResult.fromValue(s)
    }

    fun closePopup() {
        val p = popup ?: return
        popup = null
        if (p.session.isOpen) p.session.close()
    }

    private fun closePopupOf(e: WebExtension) { if (popup?.ext?.id == e.id) closePopup() }

    /** Página de opciones de la extensión, en una pestaña. */
    fun openOptions(e: WebExtension): Boolean {
        val u = e.metaData.optionsPageUrl ?: return false
        Tab.tabHost?.openTab(Tab(tabCtx(), false, u))
        return true
    }

    fun recommended(e: WebExtension) = e.id in RECOMMENDED

    /** addons.mozilla.org (y su CDN de descargas): la única web que instala extensiones sin aviso (#64). */
    fun isAmo(url: String): Boolean {
        val h = History.hostOf(url).lowercase()
        return url.startsWith("https://") && (h == "addons.mozilla.org" || h == "addons.cdn.mozilla.net")
    }

    /** Contexto para las pestañas que abren las extensiones: la Activity, no el de la aplicación (#75). */
    private fun tabCtx(): Context = (Tab.tabHost as? Context) ?: app

    /** Botón de una extensión tal como se ve en la pestaña visible (con su contador, si lo tiene). */
    fun actionFor(id: String): WebExtension.Action? {
        val def = actions[id]
        val mine = Tab.visible?.extActions?.get(id) ?: return def
        return if (def != null) mine.withDefault(def) else mine
    }

    /**
     * Instalar desde un enlace: una ficha de addons.mozilla.org (…/addon/<nombre>/) o un .xpi.
     * Gecko comprueba la firma de Mozilla y pregunta los permisos antes de instalar (Engine.onInstallPromptRequest).
     */
    fun install(ctx: Context, link: String, done: (Boolean) -> Unit = {}) {
        val url = toInstallUrl(link)
        if (url == null) {
            toast(ctx, "Ese enlace no es de una extensión. Usa la ficha de addons.mozilla.org o un archivo .xpi"); done(false); return
        }
        installUrl(ctx, url, done)
    }

    /** Instala desde la dirección del archivo de la extensión, tal cual. */
    fun installUrl(ctx: Context, url: String, done: (Boolean) -> Unit = {}) {
        toast(ctx, "Preparando la extensión…")
        Engine.get(ctx).webExtensionController.install(url).accept({ e ->
            if (e != null) toast(ctx, "«${e.metaData.name ?: e.id}» instalada")
            refresh(); done(e != null)
        }, { err ->
            val msg = err?.message.orEmpty()
            toast(ctx, when {
                msg.contains("signature", true) || msg.contains("signed", true) -> "No se instala: no está firmada por Mozilla"
                msg.contains("cancel", true) || msg.contains("USER_CANCELED", true) -> "Instalación cancelada"
                msg.contains("incompatible", true) -> "Esta extensión no funciona en el motor de Brisa"
                else -> "No se pudo instalar la extensión"
            })
            done(false)
        })
    }

    /** Ficha de AMO → su .xpi más reciente; un .xpi https se usa tal cual. */
    fun toInstallUrl(link: String): String? {
        val t = link.trim()
        if (!t.startsWith("https://", true)) return null
        Regex("^https://addons\\.mozilla\\.org/(?:[a-zA-Z-]+/)?(?:android|firefox)/addon/([^/?#]+)", RegexOption.IGNORE_CASE).find(t)?.let {
            return "https://addons.mozilla.org/firefox/downloads/latest/${it.groupValues[1]}/latest.xpi"
        }
        return t.takeIf { it.substringBefore('?').substringBefore('#').endsWith(".xpi", true) }
    }

    fun setEnabled(e: WebExtension, on: Boolean) {
        val r = if (on) controller.enable(e, WebExtensionController.EnableSource.USER)
                else controller.disable(e, WebExtensionController.EnableSource.USER)
        r.accept({ refresh() }, { refresh() })
    }

    fun setPrivate(e: WebExtension, on: Boolean) {
        controller.setAllowedInPrivateBrowsing(e, on).accept({ refresh() }, { refresh() })
    }

    fun uninstall(e: WebExtension) {
        controller.uninstall(e).accept({ refresh() }, { refresh() })
    }

    /** Respuesta de una web que es una extensión (.xpi): se instala en vez de descargarla como archivo. */
    fun isXpi(contentType: String?, uri: String) =
        contentType?.contains("x-xpinstall", true) == true || uri.substringBefore('?').substringBefore('#').endsWith(".xpi", true)

    /** Icono de la extensión o de su botón, cargado aparte. */
    fun icon(img: org.mozilla.geckoview.Image?, size: Int, cb: (Bitmap?) -> Unit) {
        if (img == null) { cb(null); return }
        runCatching { img.getBitmap(size).accept({ cb(it) }, { cb(null) }) }.onFailure { cb(null) }
    }

    private fun toast(ctx: Context, s: String) = android.widget.Toast.makeText(ctx, s, android.widget.Toast.LENGTH_SHORT).show()

}
