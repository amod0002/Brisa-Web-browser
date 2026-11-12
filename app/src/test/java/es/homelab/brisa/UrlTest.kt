package es.homelab.brisa

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlTest {
    @Test fun direccion() = assertEquals("https://wikipedia.org", Tab.toUrl("wikipedia.org"))
    @Test fun conEsquema() = assertEquals("http://nube.local:5212/", Tab.toUrl("http://nube.local:5212/"))
    @Test fun ipHomelab() = assertEquals("http://192.168.1.50:8006", Tab.toUrl("192.168.1.50:8006"))
    @Test fun busqueda() = assertEquals("https://duckduckgo.com/?q=recetas+de+%C3%B1oquis", Tab.toUrl("recetas de ñoquis"))
    @Test fun espacios() = assertEquals("https://elpais.com", Tab.toUrl("  elpais.com  "))
}

class HistoryHostTest {
    @Test fun host() = assertEquals("elpais.com", History.hostOf("https://www.elpais.com/espana/"))
}

class SearchTest {
    @Test fun searxng() = assertEquals("http://192.168.1.60:8888/search?q=gato", Tab.toUrl("gato", "http://192.168.1.60:8888/search?q=%s"))
    @Test fun plantilla() {
        org.junit.Assert.assertTrue(Settings.isValidTemplate("https://www.google.com/search?q=%s"))
        org.junit.Assert.assertFalse(Settings.isValidTemplate("google.com"))
    }
}

class FrequentTest {
    @Test fun agrupaPorDominio() {
        val e = listOf(
            History.Entry("https://elpais.com/a", "A", 1, 3), History.Entry("https://elpais.com/b", "B", 2, 2),
            History.Entry("https://marca.com/", "M", 3, 1))
        val freq = e.groupBy { History.hostOf(it.url) }.map { (_, l) -> l.maxBy { it.visits }.copy(visits = l.sumOf { it.visits }) }
            .filter { it.visits >= 2 }
        assertEquals(1, freq.size); assertEquals(5, freq[0].visits)
    }
}

class PasswordGenTest {
    @Test fun largoYVariado() {
        val p = Passwords.generate(18)
        assertEquals(18, p.length)
        org.junit.Assert.assertNotEquals(p, Passwords.generate(18))
    }
}

class PrivacyTest {
    @Test fun redLocal() {
        org.junit.Assert.assertTrue(Privacy.isLocal("192.168.1.50:8006"))
        org.junit.Assert.assertTrue(Privacy.isLocal("10.0.0.2"))
        org.junit.Assert.assertTrue(Privacy.isLocal("nube.local"))
        org.junit.Assert.assertTrue(Privacy.isLocal("172.20.1.1"))
        org.junit.Assert.assertFalse(Privacy.isLocal("172.40.1.1"))
        org.junit.Assert.assertFalse(Privacy.isLocal("elpais.com"))
    }
    @Test fun listaDeRastreo() = org.junit.Assert.assertTrue("fbclid" in Privacy.STRIP && "utm_source" in Privacy.STRIP)
}

class SecurityTest {
    @Test fun soloWebsDesdeOtrasApps() {
        assertEquals("https://elpais.com", safeIncoming("https://elpais.com"))
        org.junit.Assert.assertNull(safeIncoming("file:///data/data/es.homelab.brisa/files/contrasenas.bin"))
        org.junit.Assert.assertNull(safeIncoming("javascript:alert(1)"))
        org.junit.Assert.assertNull(safeIncoming("about:config"))
        org.junit.Assert.assertNull(safeIncoming("resource://android/assets/"))
    }
    @Test fun direccionesNoVanAlBuscador() {
        org.junit.Assert.assertTrue(Settings.looksLikeAddress("192.168.1.50:8006"))
        org.junit.Assert.assertTrue(Settings.looksLikeAddress("nube.local"))
        org.junit.Assert.assertTrue(Settings.looksLikeAddress("elpais.com/espana"))
        org.junit.Assert.assertFalse(Settings.looksLikeAddress("recetas de ñoquis"))
    }
    @Test fun iconosNoTocanLaRedLocal() {
        org.junit.Assert.assertFalse(WebIcons.allowed("evil.com", "http://192.168.1.1/admin"))
        org.junit.Assert.assertFalse(WebIcons.allowed("evil.com", "file:///etc/hosts"))
        org.junit.Assert.assertTrue(WebIcons.allowed("192.168.1.50", "http://192.168.1.50/favicon.ico"))
        org.junit.Assert.assertTrue(WebIcons.allowed("elpais.com", "https://static.elpais.com/favicon.png"))
    }
}

class DnsPropioTest {
    @Test fun validos() {
        org.junit.Assert.assertTrue(Privacy.isValidDoh("https://dns.midominio.es/dns-query"))
        org.junit.Assert.assertTrue(Privacy.isValidDoh("https://dns.nextdns.io/abc123"))
        org.junit.Assert.assertTrue(Privacy.isValidDoh("https://adguard.casa.lan:8443/dns-query"))
    }
    @Test fun invalidos() {
        org.junit.Assert.assertFalse(Privacy.isValidDoh("http://dns.midominio.es/dns-query"))
        org.junit.Assert.assertFalse(Privacy.isValidDoh("192.168.1.2"))
        org.junit.Assert.assertFalse(Privacy.isValidDoh("https://"))
        org.junit.Assert.assertFalse(Privacy.isValidDoh("https://mi dns/x"))
    }
}

class PasswordsQuarantineTest {
    @Test fun archivoDanadoSeAparta() {
        val dir = java.nio.file.Files.createTempDirectory("brisa").toFile()
        val f = java.io.File(dir, "contrasenas.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        var parsed = false
        val (list, apartado) = Passwords.readOrQuarantine(f, { error("no se descifra") }, { parsed = true; emptyList() })
        org.junit.Assert.assertTrue(list.isEmpty())
        org.junit.Assert.assertFalse(parsed)
        org.junit.Assert.assertFalse("el original no debe quedarse para sobrescribirse", f.exists())
        org.junit.Assert.assertNotNull(apartado)
        org.junit.Assert.assertArrayEquals(byteArrayOf(1, 2, 3), apartado!!.readBytes())
    }
    @Test fun sinArchivoNoHayAviso() {
        val dir = java.nio.file.Files.createTempDirectory("brisa").toFile()
        val (list, apartado) = Passwords.readOrQuarantine(java.io.File(dir, "contrasenas.bin"), { it }, { emptyList() })
        org.junit.Assert.assertTrue(list.isEmpty()); org.junit.Assert.assertNull(apartado)
    }
}

class RedLocalTest {
    @Test fun ipv6Locales() {
        org.junit.Assert.assertTrue(Privacy.isLocal("[::1]"))
        org.junit.Assert.assertTrue(Privacy.isLocal("[fd00::1]:8080"))
        org.junit.Assert.assertTrue(Privacy.isLocal("[fe80::1]"))
        org.junit.Assert.assertFalse(Privacy.isLocal("[2a00:1450::1]"))
        org.junit.Assert.assertTrue(Privacy.isLocal("169.254.1.1"))
    }
    @Test fun direccionesIpLocales() {
        fun ip(s: String) = java.net.InetAddress.getByName(s)
        org.junit.Assert.assertTrue(Net.isLocalAddress(ip("192.168.1.1")))
        org.junit.Assert.assertTrue(Net.isLocalAddress(ip("10.0.0.5")))
        org.junit.Assert.assertTrue(Net.isLocalAddress(ip("100.100.1.1")))
        org.junit.Assert.assertTrue(Net.isLocalAddress(ip("127.0.0.1")))
        org.junit.Assert.assertTrue(Net.isLocalAddress(ip("fd12::1")))
        org.junit.Assert.assertTrue(Net.isLocalAddress(ip("fe80::1")))
        org.junit.Assert.assertTrue(Net.isLocalAddress(ip("::1")))
        org.junit.Assert.assertFalse(Net.isLocalAddress(ip("8.8.8.8")))
        org.junit.Assert.assertFalse(Net.isLocalAddress(ip("2a00:1450:4003::1")))
    }
}

class BarraLocalTest {
    @Test fun localhost() = assertEquals("http://localhost:3000", Tab.toUrl("localhost:3000"))
    @Test fun nombrePuerto() = assertEquals("http://proxmox:8006", Tab.toUrl("proxmox:8006"))
    @Test fun ipv6() = assertEquals("http://[fd00::1]:8080", Tab.toUrl("[fd00::1]:8080"))
    @Test fun nombreRuta() = assertEquals("http://nas/admin", Tab.toUrl("nas/admin"))
    @Test fun dominioLocal() = assertEquals("http://nube.lan", Tab.toUrl("nube.lan"))
    @Test fun busquedaIgual() = assertEquals("https://duckduckgo.com/?q=recetas+de+%C3%B1oquis", Tab.toUrl("recetas de ñoquis"))
    @Test fun palabraSueltaBusca() = assertEquals("https://duckduckgo.com/?q=proxmox", Tab.toUrl("proxmox"))
}

class PasswordMatchTest {
    private fun l(origin: String) = Passwords.Login("g-$origin", origin, null, null, "yo", "x", 0, 0)
    @Test fun httpsNoSeOfreceEnHttp() {
        val all = listOf(l("https://a.com"))
        assertEquals(0, Passwords.match(all, "a.com", "http://a.com/login").size)
        assertEquals(1, Passwords.match(all, "a.com", "https://a.com/login").size)
    }
    @Test fun homelabConPuerto() {
        val all = listOf(l("http://192.168.1.50:8006"))
        assertEquals(1, Passwords.match(all, "192.168.1.50", "http://192.168.1.50:8006/").size)
        assertEquals(0, Passwords.match(all, "192.168.1.50", "http://192.168.1.50:9000/").size)
    }
    @Test fun sinSubdominiosCruzados() {
        val all = listOf(l("https://a.com"))
        assertEquals(0, Passwords.match(all, "evil.a.com", "https://evil.a.com/").size)
    }
}

class EsquemasBarraTest {
    @Test fun fileSeBusca() = org.junit.Assert.assertTrue(Tab.toUrl("file:///etc/hosts").startsWith("https://duckduckgo.com/?q="))
    @Test fun intentSeBusca() = org.junit.Assert.assertTrue(Tab.toUrl("intent://x#Intent;end").startsWith("https://duckduckgo.com/?q="))
    @Test fun aboutBlank() = assertEquals("about:blank", Tab.toUrl("about:blank"))
    @Test fun httpsSigue() = assertEquals("https://elpais.com/", Tab.toUrl("https://elpais.com/"))
}

class PasswordFondoTest {
    private fun l(origin: String) = Passwords.Login("g-$origin", origin, null, null, "yo", "x", 0, 0)
    @Test fun sinPaginaNoSeOfrecenLasHttps() {
        assertEquals(0, Passwords.match(listOf(l("https://a.com")), "a.com", null).size)
        assertEquals(1, Passwords.match(listOf(l("http://192.168.1.50:8006")), "192.168.1.50", null).size)
    }

    // #31: los «Solo esta vez» guardados se retiran al arrancar; los demás permisos no se tocan
    @Test fun soloEstaVezSeRetiraAlArrancar() {
        val saved = setOf(Permissions.tempKey("mapa.com", 1), Permissions.tempKey("cam.es", 2))
        val gecko = listOf("mapa.com" to 1, "mapa.com" to 3, "otra.com" to 1, "cam.es" to 2)
        assertEquals(listOf("mapa.com" to 1, "cam.es" to 2), Permissions.stale(saved, gecko))
        assertEquals(0, Permissions.stale(emptySet(), gecko).size)
    }

    // Enlaces a apps: los web los abre Gecko; tg://, intent://… van a apps; file:// y content:// nunca
    @Test fun enlacesAApps() {
        assertEquals(null, ExternalApps.isExternal("https://youtube.com/watch?v=1"))
        assertEquals(null, ExternalApps.isExternal("about:blank"))
        assertEquals(true, ExternalApps.isExternal("tg://resolve?domain=x"))
        assertEquals(true, ExternalApps.isExternal("intent://scan/#Intent;scheme=zxing;end"))
        assertEquals(true, ExternalApps.isExternal("mailto:a@b.es"))
        assertEquals(false, ExternalApps.isExternal("file:///sdcard/x"))
        assertEquals(false, ExternalApps.isExternal("content://com.whatsapp.provider/x"))
    }
}

/** #16: enlaces de extensiones que se pueden pegar para instalar. */
class ExtensionLinkTest {
    @Test fun fichaAmo() = assertEquals("https://addons.mozilla.org/firefox/downloads/latest/darkreader/latest.xpi",
        Extensions.toInstallUrl("https://addons.mozilla.org/es-ES/android/addon/darkreader/"))
    @Test fun fichaAmoSinIdioma() = assertEquals("https://addons.mozilla.org/firefox/downloads/latest/sponsorblock/latest.xpi",
        Extensions.toInstallUrl("https://addons.mozilla.org/firefox/addon/sponsorblock/?utm_source=x"))
    @Test fun xpiDirecto() = assertEquals("https://example.org/ext.xpi", Extensions.toInstallUrl(" https://example.org/ext.xpi "))
    @Test fun noEsExtension() = org.junit.Assert.assertNull(Extensions.toInstallUrl("https://addons.mozilla.org/es-ES/android/"))
    @Test fun sinHttps() = org.junit.Assert.assertNull(Extensions.toInstallUrl("http://example.org/ext.xpi"))
    @Test fun respuestaXpi() {
        org.junit.Assert.assertTrue(Extensions.isXpi("application/x-xpinstall", "https://addons.mozilla.org/firefox/downloads/file/1/a"))
        org.junit.Assert.assertTrue(Extensions.isXpi(null, "https://addons.mozilla.org/firefox/downloads/file/1/a-1.0.xpi?src=x"))
        org.junit.Assert.assertFalse(Extensions.isXpi("application/pdf", "https://example.org/a.pdf"))
    }
}
