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
