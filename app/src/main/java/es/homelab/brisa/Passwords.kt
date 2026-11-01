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
        logins = runCatching { parse(decrypt(file.readBytes())) }.getOrDefault(emptyList())
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
        tmp.renameTo(file)
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
    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>> {
        val h = domain.removePrefix("www.")
        return GeckoResult.fromValue(logins.filter { it.host == h || it.host.endsWith(".$h") || h.endsWith(".${it.host}") }
            .map { it.toEntry() }.toTypedArray())
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
