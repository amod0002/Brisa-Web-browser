package es.homelab.brisa

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.ContentBlocking
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
            .build()
    ).also { runtime = it }
}

/** Una pestaña: su sesión Gecko y el estado que pinta la interfaz. */
class Tab(ctx: Context, val private: Boolean, startUrl: String?) {
    var url by mutableStateOf(startUrl ?: "")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var blocked by mutableIntStateOf(0)
    var desktop by mutableStateOf(false)
    var thumb by mutableStateOf<Bitmap?>(null)
    /** Desplazamiento acumulado: la barra se encoge al bajar y vuelve al subir. */
    var scrollDelta by mutableIntStateOf(0)

    val session = GeckoSession(
        GeckoSessionSettings.Builder().usePrivateMode(private).build()
    ).apply {
        progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(s: GeckoSession, u: String) { url = u; progress = 5; blocked = 0 }
            override fun onProgressChange(s: GeckoSession, p: Int) { progress = p }
            override fun onPageStop(s: GeckoSession, ok: Boolean) { progress = 100 }
        }
        contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(s: GeckoSession, t: String?) { title = t ?: "" }
        }
        navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(s: GeckoSession, v: Boolean) { canBack = v }
            override fun onCanGoForward(s: GeckoSession, v: Boolean) { canForward = v }
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
        open(Engine.get(ctx))
        if (!startUrl.isNullOrBlank()) loadUri(startUrl)
    }

    val isHome get() = url.isBlank() || url == "about:blank"
    val host get() = url.substringAfter("://").substringBefore("/").removePrefix("www.")

    fun load(input: String) { session.loadUri(toUrl(input)) ; url = toUrl(input) }

    fun toggleDesktop() {
        desktop = !desktop
        session.settings.userAgentMode =
            if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        session.settings.viewportMode =
            if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        session.reload()
    }

    fun close() = session.close()

    companion object {
        /** Texto escrito → URL: dirección si lo parece, si no búsqueda en DuckDuckGo. */
        fun toUrl(s: String): String {
            val t = s.trim()
            return when {
                t.matches(Regex("^[a-zA-Z]+://.*")) -> t
                t.matches(Regex("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?(/.*)?$")) -> "http://$t"
                !t.contains(' ') && t.contains('.') -> "https://$t"
                else -> "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(t, "UTF-8")
            }
        }
    }
}
