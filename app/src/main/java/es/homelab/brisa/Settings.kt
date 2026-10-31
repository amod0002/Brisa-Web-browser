package es.homelab.brisa

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Ajustes de la app. El buscador admite uno propio (p. ej. SearXNG del homelab) con %s donde va la búsqueda. */
object Settings {
    val engines = linkedMapOf(
        "DuckDuckGo" to "https://duckduckgo.com/?q=%s",
        "Google" to "https://www.google.com/search?q=%s",
        "Startpage" to "https://www.startpage.com/do/search?q=%s",
        "Brave" to "https://search.brave.com/search?q=%s",
        "Ecosia" to "https://www.ecosia.org/search?q=%s",
    )
    var search by mutableStateOf(engines.getValue("DuckDuckGo"))
        private set

    fun load(ctx: Context) { search = ctx.getSharedPreferences("ajustes", 0).getString("buscador", search)!! }
    fun setSearch(ctx: Context, t: String) {
        search = t; ctx.getSharedPreferences("ajustes", 0).edit().putString("buscador", t).apply()
    }
    fun isValidTemplate(t: String) = t.startsWith("http") && t.contains("%s")
}
