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

    var suggest by mutableStateOf(true)
        private set
    fun setSuggest(ctx: Context, v: Boolean) { suggest = v; ctx.getSharedPreferences("ajustes", 0).edit().putBoolean("sugerencias", v).apply() }

    /** Pantalla de tablet (ancho mínimo ≥ 600 dp). Se fija al arrancar. */
    var isTablet = false
    /** En tablet, las webs se piden en versión de escritorio (como el iPad). */
    var desktopTablet by mutableStateOf(true)
        private set
    fun changeDesktopTablet(ctx: Context, v: Boolean) { desktopTablet = v; ctx.getSharedPreferences("ajustes", 0).edit().putBoolean("escritorio_tablet", v).apply() }

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("ajustes", 0)
        isTablet = ctx.resources.configuration.smallestScreenWidthDp >= 600
        desktopTablet = p.getBoolean("escritorio_tablet", true)
        search = p.getString("buscador", search)!!; suggest = p.getBoolean("sugerencias", true)
    }

    /** Sugerencias del buscador mientras se escribe (servicio de DuckDuckGo, no guarda historial). */
    fun fetchSuggestions(q: String): List<String> = runCatching {
        val c = java.net.URL("https://duckduckgo.com/ac/?type=list&q=" + java.net.URLEncoder.encode(q, "UTF-8"))
            .openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 2500; c.readTimeout = 2500
        val a = org.json.JSONArray(c.inputStream.bufferedReader().readText()).getJSONArray(1)
        (0 until a.length()).map { a.getString(it) }.take(4)
    }.getOrDefault(emptyList())
    fun setSearch(ctx: Context, t: String) {
        search = t; ctx.getSharedPreferences("ajustes", 0).edit().putString("buscador", t).apply()
    }
    /** Lo que parece una dirección (dominio, IP, ruta) no se manda al buscador para sugerencias. */
    fun looksLikeAddress(t: String): Boolean {
        val s = t.trim()
        if (s.contains("://") || Privacy.isLocal(s.substringBefore('/'))) return true
        return !s.contains(' ') && (s.contains('.') || s.contains(':') || s.contains('/'))
    }

    fun isValidTemplate(t: String) = t.startsWith("http") && t.contains("%s")
}
