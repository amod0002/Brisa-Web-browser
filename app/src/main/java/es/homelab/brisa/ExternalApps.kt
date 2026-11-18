package es.homelab.brisa

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * Abrir enlaces en su app (YouTube, WhatsApp, Telegram, Maps…), como Safari y Chrome:
 * - enlaces web (https://youtube.com/…) que tiene una app que no es un navegador, al tocarlos desde una
 *   web de otro sitio; lo que se escribe en la barra se queda siempre en Brisa;
 * - enlaces que no son web (tg://, whatsapp://, intent://, mailto:, tel:…), que Gecko no sabe abrir.
 * Solo con un toque reciente del usuario: una web no puede lanzar apps por su cuenta.
 */
object ExternalApps {
    /** Esquemas que abre Gecko. El resto va a las apps (salvo los peligrosos). */
    private val GECKO = setOf("http", "https", "about", "data", "blob", "javascript", "moz-extension", "resource", "view-source", "ws", "wss")
    /** Nunca a otra app desde una web: darían acceso a archivos del móvil o de otras apps. */
    private val BLOCKED = setOf("file", "content", "chrome", "jar")

    fun scheme(uri: String) = uri.substringBefore(':', "").lowercase()

    /** Qué hacer con un enlace que no es web: null si lo abre Gecko. Puro, para los tests. */
    fun isExternal(uri: String): Boolean? {
        val s = scheme(uri)
        if (s.isEmpty() || s in GECKO) return null
        return s !in BLOCKED
    }

    /** Apps que no son navegadores y saben abrir esta dirección web. */
    private fun appsFor(ctx: Context, url: String): List<android.content.pm.ResolveInfo> = runCatching {
        val pm = ctx.packageManager
        val view = { u: String -> Intent(Intent.ACTION_VIEW, Uri.parse(u)).addCategory(Intent.CATEGORY_BROWSABLE) }
        val browsers = pm.queryIntentActivities(view("https://example.com/"), PackageManager.MATCH_ALL).map { it.activityInfo.packageName }.toSet()
        pm.queryIntentActivities(view(url), PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.packageName != ctx.packageName && it.activityInfo.packageName !in browsers }
    }.getOrDefault(emptyList())

    /** intent://… o tg://… → Intent seguro (sin componente ni selector y nunca hacia Brisa). */
    private fun intentFor(ctx: Context, uri: String): Pair<Intent, String?>? = runCatching {
        if (scheme(uri) == "intent") {
            val i = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
            val fallback = i.getStringExtra("browser_fallback_url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            i.selector = null; i.component = null
            i.addCategory(Intent.CATEGORY_BROWSABLE)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (i.`package` == ctx.packageName || i.data?.scheme?.lowercase() in BLOCKED) return null
            i to fallback
        } else Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) to null
    }.getOrNull()

    private fun label(ctx: Context, i: Intent): String? = runCatching {
        val r = ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        if (r.activityInfo.packageName == "android" || r.activityInfo.packageName == ctx.packageName) null
        else r.loadLabel(ctx.packageManager).toString()
    }.getOrNull()

    private fun start(ctx: Context, i: Intent): Boolean =
        try { ctx.startActivity(i); true } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }

    /**
     * Para onLoadRequest. null = que siga Gecko. Si hay app, pregunta (o abre directamente si el ajuste lo dice)
     * y contesta DENY cuando se va a la app.
     */
    fun check(ctx: Context, tab: Tab, req: GeckoSession.NavigationDelegate.LoadRequest, gesture: Boolean): GeckoResult<AllowOrDeny>? {
        val uri = req.uri
        val ext = isExternal(uri)
        if (ext == false) return GeckoResult.fromValue(AllowOrDeny.DENY)
        if (ext == null) {
            // Web: solo enlaces tocados que van a otro sitio, nunca lo escrito en la barra ni dentro del mismo sitio
            if (!Settings.openApps || !gesture || req.isDirectNavigation) return null
            if (scheme(uri) !in setOf("http", "https")) return null
            val host = History.hostOf(uri)
            if (host.isEmpty() || host == History.hostOf(tab.url)) return null
            val apps = appsFor(ctx, uri)
            if (apps.isEmpty()) return null
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (apps.size == 1) i.setPackage(apps[0].activityInfo.packageName)
            else if (android.os.Build.VERSION.SDK_INT >= 30) i.addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
            val name = if (apps.size == 1) apps[0].loadLabel(ctx.packageManager).toString() else null
            return askThen(ctx, tab, name, host, stayInBrisa = AllowOrDeny.ALLOW) { start(ctx, i) }
        }
        // No web (tg://, whatsapp://, intent://, mailto:…): Gecko no sabe abrirlos
        if (!gesture) return GeckoResult.fromValue(AllowOrDeny.DENY)
        val (i, fallback) = intentFor(ctx, uri) ?: return GeckoResult.fromValue(AllowOrDeny.DENY)
        val openFallback = { fallback?.let { tab.open(it) } }
        if (!Settings.openApps) { openFallback(); return GeckoResult.fromValue(AllowOrDeny.DENY) }
        val name = label(ctx, i)
        val result = askThen(ctx, tab, name, null, stayInBrisa = AllowOrDeny.DENY) {
            start(ctx, i).also { ok ->
                if (!ok) {
                    if (fallback != null) openFallback()
                    else Toast.makeText(ctx, "No hay ninguna app para abrir este enlace", Toast.LENGTH_SHORT).show()
                }
            }
        }
        return result.map { AllowOrDeny.DENY }
    }

    /** Pregunta «¿Abrir en X?» (siempre en privadas) o, si el ajuste lo dice, abre directamente. */
    private fun askThen(ctx: Context, tab: Tab, app: String?, host: String?, stayInBrisa: AllowOrDeny, open: () -> Boolean): GeckoResult<AllowOrDeny> {
        // #75: el host siempre de la Activity, aunque la pestaña se creara con otro contexto
        val host2 = (Tab.tabHost as? PromptHost) ?: (ctx as? PromptHost)
        if ((!Settings.askApps && !tab.private) || host2 == null) {
            return GeckoResult.fromValue(if (open()) AllowOrDeny.DENY else stayInBrisa)
        }
        val r = GeckoResult<AllowOrDeny>()
        var answered = false
        val answer = { v: AllowOrDeny -> if (!answered) { answered = true; r.complete(v) } }
        host2.show { close ->
            AlertDialog(onDismissRequest = { close(); answer(stayInBrisa) },
                icon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                title = { Text(if (app != null) "¿Abrir en $app?" else "¿Abrir en otra app?") },
                text = {
                    Text(when {
                        host != null && tab.private -> "Estás en una pestaña privada: la app sabrá que has abierto $host."
                        host != null -> "Este enlace de $host se puede ver en la app."
                        else -> "La web quiere abrir otra app del móvil."
                    })
                },
                confirmButton = { TextButton({ close(); answer(if (open()) AllowOrDeny.DENY else stayInBrisa) }) { Text("Abrir") } },
                dismissButton = { TextButton({ close(); answer(stayInBrisa) }) { Text(if (host != null) "Seguir en Brisa" else "Cancelar") } })
        }
        return r
    }
}
