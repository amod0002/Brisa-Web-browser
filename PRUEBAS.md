# Lista de pruebas en el Pixel

Repasar antes de pasar una versión de `dev` a `main` y etiquetarla. Marca con [x] lo que funcione; si algo falla, anota la versión y qué pasó.

**Flujo:** cambios → rama `dev` (Actions compila, pasa pruebas unitarias y lint, y deja el APK como artefacto) → repaso de esta lista en el Pixel → `main` + etiqueta `vX.Y.Z` → Releases.

## 1. Arranque y barra
- [ ] La app abre en menos de 2 s y muestra el Inicio.
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

## 6. Android
- [ ] Gesto «atrás»: cierra menú/teclado/cuadrícula y luego va a la página anterior.
- [ ] Un enlace desde otra app (WhatsApp, correo) se abre en una pestaña nueva.
- [ ] Compartir envía la URL.
- [ ] Tema claro/oscuro y colores de Material You.
- [ ] Girar el móvil no recarga la página.

## 7. Velocidad (comparar con Chrome)
- [ ] Abrir elpais.com, marca.com y youtube.com: sensación de carga igual o mejor.
- [ ] Scroll fluido en páginas largas, sin tirones.
