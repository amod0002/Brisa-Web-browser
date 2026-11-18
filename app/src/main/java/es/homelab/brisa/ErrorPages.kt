package es.homelab.brisa

import org.mozilla.geckoview.WebRequestError
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * Páginas de error de Brisa (#61). Gecko no pinta ninguna por su cuenta: si onLoadError devuelve null,
 * la carga se para y la pantalla se queda en negro. Aquí se elige qué contar según el error y se abre
 * assets/errores/error.html con los datos en la dirección.
 *
 * Certificado no válido (lo típico de Proxmox, el router o cualquier servicio del homelab con certificado
 * autofirmado): se explica y, en «Avanzado», se puede aceptar el riesgo. En la red local la excepción se
 * recuerda; en internet solo dura hasta cerrar Brisa.
 */
object ErrorPages {
    private const val PAGE = "resource://android/assets/errores/error.html"
    /** Gecko 160+: la web intentó entrar en la red local y se le negó. */
    private const val LOCAL_NETWORK_DENIED = 0xC3

    data class Info(val kind: String, val title: String, val message: String, val explain: String? = null)

    fun info(code: Int, host: String, local: Boolean): Info = when (code) {
        WebRequestError.ERROR_SECURITY_BAD_CERT ->
            if (local) Info("cert", "Certificado no verificado en tu red",
                "Este servicio de tu red usa un certificado que no está firmado por una autoridad conocida. Es lo normal en Proxmox, el router o un NAS con certificado propio.",
                "Si este es tu servidor, puedes continuar: Brisa lo recordará para no volver a preguntar. Si no lo esperabas, comprueba la huella con la que muestra el propio servidor.")
            else Info("cert", "Este sitio no es seguro",
                "El certificado de esta web no es válido: puede estar caducado, ser de otra web o no estar firmado por una autoridad de confianza. Alguien podría estar haciéndose pasar por ella.",
                "Continúa solo si sabes por qué pasa y confías en esta web. La excepción durará hasta que cierres Brisa.")
        WebRequestError.ERROR_BAD_HSTS_CERT -> Info("hsts", "Este sitio no es seguro",
            "Esta web exige siempre una conexión segura y su certificado no es válido. Por seguridad, no se puede continuar.")
        WebRequestError.ERROR_SECURITY_SSL -> Info("ssl", "No se ha podido conectar de forma segura",
            "La web y Brisa no se han puesto de acuerdo para cifrar la conexión. Puede que el servidor use una versión antigua de TLS.")
        WebRequestError.ERROR_HTTPS_ONLY -> Info("https", "Esta web no tiene conexión segura",
            "Brisa la ha bloqueado porque tienes activado «Solo HTTPS». Elige en la ventana si quieres continuar.")
        WebRequestError.ERROR_SAFEBROWSING_MALWARE_URI, WebRequestError.ERROR_SAFEBROWSING_HARMFUL_URI ->
            Info("peligro", "Web peligrosa", "Esta web está marcada por intentar instalar programas dañinos. Brisa no la ha abierto.")
        WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI ->
            Info("peligro", "Web engañosa", "Esta web está marcada por hacerse pasar por otra para robar contraseñas o datos. Brisa no la ha abierto.")
        WebRequestError.ERROR_SAFEBROWSING_UNWANTED_URI ->
            Info("peligro", "Web con programas no deseados", "Esta web está marcada por ofrecer programas no deseados. Brisa no la ha abierto.")
        WebRequestError.ERROR_UNKNOWN_HOST -> Info("red", "No se encuentra el servidor",
            if (local) "Brisa no encuentra «$host» en tu red. Comprueba que el servidor está encendido y que estás en la misma red (o en la VPN)."
            else "Brisa no encuentra «$host». Comprueba la dirección y tu conexión.")
        WebRequestError.ERROR_CONNECTION_REFUSED -> Info("red", "El servidor ha rechazado la conexión",
            "El servidor está ahí, pero no responde en esa dirección o puerto. Comprueba que el servicio está en marcha.")
        WebRequestError.ERROR_NET_TIMEOUT -> Info("red", "El servidor tarda demasiado",
            "No ha respondido a tiempo. Puede estar ocupado o la conexión ser lenta.")
        WebRequestError.ERROR_OFFLINE -> Info("red", "Sin conexión", "Brisa no tiene conexión a internet ahora mismo.")
        WebRequestError.ERROR_NET_RESET, WebRequestError.ERROR_NET_INTERRUPT -> Info("red", "Se ha cortado la conexión",
            "La conexión con el servidor se ha interrumpido mientras cargaba la página.")
        WebRequestError.ERROR_REDIRECT_LOOP -> Info("red", "La web no termina de cargar",
            "La web redirige una y otra vez sin llegar a ninguna parte. Borrar sus cookies suele arreglarlo.")
        WebRequestError.ERROR_PORT_BLOCKED -> Info("red", "Puerto no permitido",
            "Por seguridad, Gecko no deja abrir webs en ese puerto.")
        WebRequestError.ERROR_PROXY_CONNECTION_REFUSED, WebRequestError.ERROR_UNKNOWN_PROXY_HOST ->
            Info("red", "No se puede usar el proxy", "El proxy configurado no responde.")
        LOCAL_NETWORK_DENIED -> Info("red", "Acceso a la red local bloqueado",
            "Esta web ha intentado conectarse a un dispositivo de tu red local y se ha bloqueado.")
        WebRequestError.ERROR_CORRUPTED_CONTENT, WebRequestError.ERROR_INVALID_CONTENT_ENCODING,
        WebRequestError.ERROR_UNSAFE_CONTENT_TYPE -> Info("red", "La web ha llegado dañada",
            "El servidor ha enviado una respuesta que no se puede mostrar.")
        WebRequestError.ERROR_MALFORMED_URI, WebRequestError.ERROR_UNKNOWN_PROTOCOL -> Info("red", "Dirección no válida",
            "Brisa no sabe abrir esta dirección.")
        else -> Info("red", "No se ha podido abrir la página", "Ha habido un problema al cargarla.")
    }

    /** Dirección de la página de error para este fallo. */
    fun url(uri: String?, e: WebRequestError, private: Boolean): String {
        val host = uri?.let { u -> History.hostOf(u) }.orEmpty()
        val local = Privacy.isLocal(host)
        val i = info(e.code, host, local)
        val p = linkedMapOf("k" to i.kind, "h" to host, "t" to i.title, "m" to i.message)
        i.explain?.let { p["x"] = it }
        if (i.kind == "cert") {
            // En la red local (y fuera de privadas) la excepción se recuerda; en internet, solo hasta cerrar Brisa
            p["tmp"] = if (local && !private) "0" else "1"
            if (local) p["abrir"] = "1"
            e.certificate?.let { c -> p.putAll(certInfo(c)) }
        }
        return PAGE + "?" + p.entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8").replace("+", "%20") }
    }

    private fun certInfo(c: X509Certificate): Map<String, String> = runCatching {
        fun cn(dn: String) = Regex("CN=([^,]+)").find(dn)?.groupValues?.get(1) ?: dn
        val subject = c.subjectX500Principal.name
        val issuer = c.issuerX500Principal.name
        val selfSigned = subject == issuer
        val fp = MessageDigest.getInstance("SHA-256").digest(c.encoded).joinToString(":") { "%02X".format(it) }
        mapOf(
            "cp" to cn(subject),
            "ci" to (if (selfSigned) "${cn(issuer)} (autofirmado)" else cn(issuer)),
            "cv" to java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("es")).format(c.notAfter),
            "cf" to fp,
        )
    }.getOrDefault(emptyMap())
}
