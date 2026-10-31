package es.homelab.brisa

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Historial guardado en el móvil (las pestañas privadas no escriben aquí). Máximo 500 entradas. */
class History private constructor(ctx: Context) {
    data class Entry(val url: String, val title: String, val time: Long)

    private val prefs = ctx.getSharedPreferences("history", 0)
    var entries by mutableStateOf(load())
        private set

    private fun load(): List<Entry> = prefs.getString("l", "")!!.split('\n').mapNotNull {
        val p = it.split('\t'); if (p.size == 3) Entry(p[0], p[1], p[2].toLongOrNull() ?: 0) else null
    }

    private fun save() = prefs.edit().putString("l", entries.joinToString("\n") {
        "${it.url}\t${it.title.replace('\t', ' ').replace('\n', ' ')}\t${it.time}"
    }).apply()

    fun visit(url: String) {
        if (!url.startsWith("http")) return
        val old = entries.firstOrNull { it.url == url }
        entries = (listOf(Entry(url, old?.title ?: "", System.currentTimeMillis())) + entries.filter { it.url != url }).take(500)
        save()
    }

    fun title(url: String, title: String) {
        if (title.isBlank() || entries.none { it.url == url }) return
        entries = entries.map { if (it.url == url) it.copy(title = title) else it }; save()
    }

    fun clear() { entries = emptyList(); save() }

    /** Sugerencias para lo que se escribe: coincidencias en dirección o título, las visitadas antes primero. */
    fun suggest(q: String, max: Int = 6): List<Entry> {
        val t = q.trim().lowercase()
        if (t.isEmpty()) return emptyList()
        return entries.asSequence()
            .filter { it.url.lowercase().contains(t) || it.title.lowercase().contains(t) }
            .sortedByDescending { if (hostOf(it.url).startsWith(t)) 1 else 0 }
            .take(max).toList()
    }

    companion object {
        @Volatile private var inst: History? = null
        fun get(ctx: Context) = inst ?: synchronized(this) { inst ?: History(ctx.applicationContext).also { inst = it } }
        fun hostOf(url: String) = url.substringAfter("://").substringBefore("/").removePrefix("www.")
    }
}
