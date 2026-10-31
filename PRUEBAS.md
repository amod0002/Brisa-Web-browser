# Lista de pruebas en el Pixel

Repasar antes de pasar una versión de `dev` a `main` y etiquetarla. Marca con [x] lo que funcione; si algo falla, anota la versión y qué pasó.

**Flujo:** cambios → rama `dev` (Actions compila, pasa pruebas unitarias y lint, y deja el APK como artefacto) → repaso de esta lista en el Pixel → `main` + etiqueta `vX.Y.Z` → Releases.

## 1. Arranque y barra
- [ ] La app abre en menos de 2 s y muestra el Inicio.
- [ ] Tocar la barra abre el teclado y el campo de búsqueda (desde Inicio y desde una página).
- [ ] Escribir una dirección (`wikipedia.org`) la abre.
- [ ] Escribir una búsqueda (`recetas de ñoquis`) abre DuckDuckGo.
- [ ] Escribir una IP del homelab (`192.168.1.x:8006`) abre Proxmox (aviso de certificado si es autofirmado).
- [ ] El candado aparece en webs https; la barra de progreso avanza y desaparece.
- [ ] Recargar y detener funcionan.

## 2. Estilo Safari
- [ ] Al bajar por una página la barra se encoge al nombre del sitio; al subir vuelve.
- [ ] Tocar la barra encogida la despliega.
- [ ] Deslizar la barra a la izquierda/derecha cambia de pestaña; al final abre una nueva.
- [ ] El teclado no tapa la barra al escribir.

## 3. Pestañas
- [ ] El contador de pestañas abre la cuadrícula con miniaturas.
- [ ] Abrir, cambiar y cerrar pestañas; cerrar la última deja una de Inicio.
- [ ] Pestaña privada: barra oscura; no aparece en «pestañas» normales.
- [ ] Al cerrar y abrir la app se recuperan las pestañas normales (no las privadas).

## 4. Inicio y favoritos
- [ ] El marcador añade la página a Favoritos y se rellena.
- [ ] Tocar un favorito lo abre; mantener pulsado lo quita.

## 5. Menú «aA»
- [ ] Muestra el número de rastreadores bloqueados en una web con anuncios.
- [ ] Versión de escritorio / móvil recarga la página en ese modo.
- [ ] Nueva pestaña privada y Abrir en otra app.

## 6. Novedades 0.2
- [ ] Al escribir en la barra salen sugerencias del historial; tocar una la abre.
- [ ] Las pestañas privadas no guardan historial ni muestran sugerencias.
- [ ] «aA» → Historial: lista con fecha; abrir una entrada; «Borrar todo».
- [ ] «aA» → Buscar en la página: contador (3/12), flechas arriba/abajo, «atrás» u OK cierran.
- [ ] «aA» → Tamaño del texto: A− / A+ cambia la letra de las webs y se recuerda al reabrir.
- [ ] «aA» → Instalar uBlock Origin: aviso de instalado; después pone «uBlock Origin activo» y desaparecen anuncios (probar marca.com).
- [ ] Descargar un PDF y un ZIP: aviso «Descargando…» y aparecen en Descargas/Brisa.
- [ ] Descargar algo que necesita sesión (un archivo de Cloudreve con la sesión iniciada en Brisa).

## 7. Novedades 0.3
- [ ] En un artículo (elpais.com, una noticia) aparece el icono de lectura junto a «aA»; en portadas no.
- [ ] Modo lectura: texto limpio con título, autor y minutos de lectura; tema claro/oscuro según el móvil.
- [ ] Tocar otra vez el icono vuelve a la página original.
- [ ] Los favoritos, las sugerencias y las pestañas muestran el icono real de cada web (la primera vez puede tardar un momento).
- [ ] Webs del homelab sin icono: sale la inicial, sin errores.
- [ ] Ajustes → Buscador: cambiar a Google y buscar algo; volver a DuckDuckGo.
- [ ] Ajustes → Buscador propio: con %s se aplica; sin %s se marca en rojo y no cambia.
- [ ] Ajustes → Borrar historial, cookies y caché: se cierra la sesión de las webs y el historial queda vacío.

## 8. Android
- [ ] Gesto «atrás»: cierra menú/teclado/cuadrícula y luego va a la página anterior.
- [ ] Un enlace desde otra app (WhatsApp, correo) se abre en una pestaña nueva.
- [ ] Compartir envía la URL.
- [ ] Tema claro/oscuro y colores de Material You.
- [ ] Girar el móvil no recarga la página.

## 9. Velocidad (comparar con Chrome)
- [ ] Abrir elpais.com, marca.com y youtube.com: sensación de carga igual o mejor.
- [ ] Scroll fluido en páginas largas, sin tirones.
