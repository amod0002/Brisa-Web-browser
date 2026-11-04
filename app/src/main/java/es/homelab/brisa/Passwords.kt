package es.homelab.brisa

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Contraseñas guardadas, cifradas con una clave AES del almacén de claves de Android
 * (la clave no sale nunca del chip de seguridad del móvil). Se guardan en un único archivo
 * cifrado dentro de la app; sin copias en la nube.
 */
object Passwords : Autocomplete.StorageDelegate {
    data class Login(
        val guid: String, val origin: String, val formActionOrigin: String?, val httpRealm: String?,
        val username: String, val password: String, val created: Long, val lastUsed: Long
    ) {
        val host get() = History.hostOf(origin)
        fun toEntry(): Autocomplete.LoginEntry = Autocomplete.LoginEntry.Builder()
            .guid(guid).origin(origin).formActionOrigin(formActionOrigin).httpRealm(httpRealm)
            .username(username).password(password).build()
    }

    private lateinit var file: File
    var logins by mutableStateOf(emptyList<Login>())
        private set
    /** Preguntar si se guarda una contraseña (Ajustes). */
    var offerSave by mutableStateOf(true)
        private set
    /** Webs para las que se ha dicho «Nunca». */
    var never by mutableStateOf(emptySet<String>())
        private set
    private lateinit var prefs: android.content.SharedPreferences

    fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.filesDir, "contrasenas.bin")
        prefs = ctx.getSharedPreferences("contrasenas", 0)
        offerSave = prefs.getBoolean("ofrecer", true)
        never = prefs.getStringSet("nunca", emptySet())!!.toSet()
        val (list, apartado) = readOrQuarantine(file, ::decrypt, ::parse)
        logins = list
        quarantined = apartado
    }

    /**
     * Si las contraseñas guardadas no se pueden leer (clave del Keystore invalidada, archivo dañado…),
     * el archivo NO se sobrescribe: se aparta como contrasenas.bin.roto-<fecha> y se avisa en Ajustes.
     */
    var quarantined by mutableStateOf<File?>(null)
        private set
    fun dismissQuarantine() { quarantined = null }

    /** Tres casos: no existe (vacía), se lee bien, o existe y no se lee (se aparta y se devuelve el apartado). */
    internal fun readOrQuarantine(f: File, dec: (ByteArray) -> ByteArray, parser: (ByteArray) -> List<Login>): Pair<List<Login>, File?> {
        if (!f.exists()) return emptyList<Login>() to null
        return try {
            parser(dec(f.readBytes())) to null
        } catch (e: Throwable) {
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date())
            val dest = File(f.parentFile, f.name + ".roto-" + stamp)
            if (!f.renameTo(dest)) { f.copyTo(dest, overwrite = false); f.delete() }
            emptyList<Login>() to dest
        }
    }

    fun changeOfferSave(v: Boolean) { offerSave = v; prefs.edit().putBoolean("ofrecer", v).apply() }
    fun addNever(host: String) { never = never + host; prefs.edit().putStringSet("nunca", never).apply() }
    fun removeNever(host: String) { never = never - host; prefs.edit().putStringSet("nunca", never).apply() }

    // ---- Cifrado -------------------------------------------------------------------------------
    private const val ALIAS = "brisa_contrasenas"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return c.iv + c.doFinal(plain)          // 12 bytes de IV + datos cifrados
    }

    private fun decrypt(data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, 12))
        return c.doFinal(data, 12, data.size - 12)
    }

    private fun parse(b: ByteArray): List<Login> {
        val a = JSONArray(String(b, Charsets.UTF_8))
        return (0 until a.length()).map { i -> a.getJSONObject(i).let {
            Login(it.getString("guid"), it.getString("origin"), it.optString("form").ifEmpty { null },
                it.optString("realm").ifEmpty { null }, it.optString("user"), it.getString("pass"),
                it.optLong("created"), it.optLong("used"))
        } }
    }

    private fun persist() {
        val a = JSONArray()
        logins.forEach { a.put(JSONObject().put("guid", it.guid).put("origin", it.origin).put("form", it.formActionOrigin ?: "")
            .put("realm", it.httpRealm ?: "").put("user", it.username).put("pass", it.password)
            .put("created", it.created).put("used", it.lastUsed)) }
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(encrypt(a.toString().toByteArray(Charsets.UTF_8)))
        // Copia de la versión anterior antes de sustituirla. Protege de un archivo dañado a medio guardar,
        // NO de perder la clave del Keystore (está cifrada con la misma clave): no es una copia de seguridad.
        if (file.exists()) runCatching { file.copyTo(File(file.path + ".bak"), overwrite = true) }
        if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
    }

    // ---- Operaciones ---------------------------------------------------------------------------
    fun save(e: Autocomplete.LoginEntry) {
        val now = System.currentTimeMillis()
        val origin = e.origin
        val same = logins.firstOrNull { (e.guid != null && it.guid == e.guid) || (it.origin == origin && it.username == (e.username ?: "")) }
        val l = Login(same?.guid ?: UUID.randomUUID().toString(), origin, e.formActionOrigin, e.httpRealm,
            e.username ?: "", e.password, same?.created ?: now, now)
        logins = listOf(l) + logins.filter { it.guid != l.guid }
        persist()
    }

    fun delete(guid: String) { logins = logins.filter { it.guid != guid }; persist() }

    fun update(guid: String, user: String, pass: String) {
        logins = logins.map { if (it.guid == guid) it.copy(username = user, password = pass) else it }; persist()
    }

    /** ¿Ya está guardada exactamente esta contraseña? (para no volver a preguntar). */
    fun isKnown(e: Autocomplete.LoginEntry) = logins.any { it.origin == e.origin && it.username == (e.username ?: "") && it.password == e.password }

    // ---- Gecko pide/guarda ---------------------------------------------------------------------
    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>> =
        GeckoResult.fromValue(match(logins, domain, Tab.visible?.url).map { it.toEntry() }.toTypedArray())

    private fun hostOnly(origin: String): String =
        runCatching { java.net.URI(origin).host }.getOrNull()?.lowercase()?.removePrefix("www.")?.removePrefix("[")?.removeSuffix("]") ?: ""
    private fun port(origin: String): Int = runCatching { java.net.URI(origin).let { u ->
        if (u.port != -1) u.port else if (u.scheme == "https") 443 else 80 } }.getOrDefault(-1)

    /**
     * Qué contraseñas se ofrecen en [domain]: mismo servidor (sin subdominios cruzados). Si sabemos la página
     * (la pestaña visible es de ese servidor), además mismo puerto, y una guardada en https NUNCA se ofrece
     * en http, salvo en la red local (homelab).
     */
    internal fun match(all: List<Login>, domain: String, pageUrl: String?): List<Login> {
        val h = domain.lowercase().removePrefix("www.").removePrefix("[").removeSuffix("]")
        val page = pageUrl?.takeIf { hostOnly(it) == h }
        return all.filter { l ->
            if (hostOnly(l.origin) != h) return@filter false
            val savedHttps = l.origin.startsWith("https://", true)
            // No sabemos de qué página viene la petición (pestaña de fondo): no se ofrecen las de https,
            // porque no consta que la página lo sea. En la red local, sí.
            if (page == null) return@filter !savedHttps || Privacy.isLocal(h)
            if (port(l.origin) != port(page)) return@filter false
            val pageHttp = page.startsWith("http://", true)
            !(savedHttps && pageHttp && !Privacy.isLocal(h))
        }
    }

    override fun onLoginFetch(): GeckoResult<Array<Autocomplete.LoginEntry>> =
        GeckoResult.fromValue(logins.map { it.toEntry() }.toTypedArray())

    override fun onLoginSave(login: Autocomplete.LoginEntry) = save(login)

    override fun onLoginUsed(login: Autocomplete.LoginEntry, usedFields: Int) {
        val now = System.currentTimeMillis()
        logins = logins.map { if (it.guid == login.guid) it.copy(lastUsed = now) else it }; persist()
    }

    /** Contraseña aleatoria fuerte para sugerir al registrarse. */
    fun generate(len: Int = 18): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789-_.!?"
        val r = java.security.SecureRandom()
        return (1..len).map { chars[r.nextInt(chars.length)] }.joinToString("")
    }

    @Suppress("unused") private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
}
