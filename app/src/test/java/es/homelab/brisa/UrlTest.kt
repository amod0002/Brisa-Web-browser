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
