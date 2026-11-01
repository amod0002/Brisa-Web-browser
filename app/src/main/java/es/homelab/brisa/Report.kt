package es.homelab.brisa

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.time.LocalDate

/**
 * Informe de privacidad: rastreadores bloqueados por día y por web (últimos 30 días).
 * Solo se guarda en el móvil y solo cuenta pestañas normales.
 */
object Report {
    /** día (aaaa-mm-dd) → (web → bloqueos) */
    private val days = HashMap<String, HashMap<String, Int>>()
    private lateinit var prefs: android.content.SharedPreferences
    private var dirty = 0
    /** Cambia cada vez que hay datos nuevos, para que la pantalla se repinte. */
    var version by mutableIntStateOf(0); private set

    fun load(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.getSharedPreferences("informe", 0)
        runCatching {
            val j = JSONObject(prefs.getString("d", "{}")!!)
            j.keys().forEach { d ->
                val m = HashMap<String, Int>(); val o = j.getJSONObject(d)
                o.keys().forEach { h -> m[h] = o.getInt(h) }
                days[d] = m
            }
        }
        prune()
    }

    fun add(host: String) {
        if (!::prefs.isInitialized || host.isBlank()) return
        val m = days.getOrPut(LocalDate.now().toString()) { HashMap() }
        m[host] = (m[host] ?: 0) + 1
        version++
        if (++dirty >= 25) save()
    }

    fun save() {
        if (!::prefs.isInitialized || dirty == 0) return
        dirty = 0
        val j = JSONObject()
        days.forEach { (d, m) -> j.put(d, JSONObject(m as Map<*, *>)) }
        prefs.edit().putString("d", j.toString()).apply()
    }

    fun clear() { days.clear(); dirty = 1; save(); version++ }

    private fun prune() {
        val limit = LocalDate.now().minusDays(30).toString()
        days.keys.filter { it < limit }.forEach { days.remove(it) }
    }

    /** Bloqueos de los últimos [n] días, del más antiguo al de hoy. */
    fun perDay(n: Int = 7): List<Pair<LocalDate, Int>> = (n - 1 downTo 0).map { back ->
        val d = LocalDate.now().minusDays(back.toLong())
        d to (days[d.toString()]?.values?.sum() ?: 0)
    }

    /** Webs con más bloqueos en los últimos [n] días. */
    fun topSites(n: Int = 7, max: Int = 10): List<Pair<String, Int>> {
        val limit = LocalDate.now().minusDays((n - 1).toLong()).toString()
        val total = HashMap<String, Int>()
        days.filterKeys { it >= limit }.values.forEach { m -> m.forEach { (h, c) -> total[h] = (total[h] ?: 0) + c } }
        return total.entries.sortedByDescending { it.value }.take(max).map { it.key to it.value }
    }

    fun total(n: Int = 7) = perDay(n).sumOf { it.second }
}
