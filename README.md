# Brisa

Navegador Android con el motor **Gecko** (GeckoView, el de Firefox) y una interfaz al estilo **Safari**. Kotlin + Jetpack Compose + Material You.

## 0.1.0
- Barra de direcciones **abajo** que se encoge al bajar por la página y vuelve al subir.
- **Desliza la barra** a izquierda/derecha para cambiar de pestaña (al final, abre una nueva).
- **Cuadrícula de pestañas** con miniaturas, pestañas **privadas** y contador.
- **Inicio** con favoritos (botón marcador para añadir; mantener pulsado para quitar).
- Menú **«aA»**: rastreadores bloqueados, versión de escritorio, pestaña privada, abrir en otra app.
- Protección **estricta** contra rastreadores, cookies de terceros aisladas, Safe Browsing.
- Escribe una dirección, una IP del homelab (`192.168.1.50:8006`) o una búsqueda (DuckDuckGo).
- Se abre desde enlaces de otras apps y recuerda las pestañas al cerrarla.

## 0.2.0
- **Sugerencias** del historial al escribir en la barra; **Historial** con fechas y «Borrar todo» (las privadas no guardan nada).
- **Descargas** a `Descargas/Brisa`, conservando la sesión de la web (Android 10+).
- **Buscar en la página** con contador y flechas.
- **Tamaño del texto** de las webs (80 – 160 %), se recuerda.
- **uBlock Origin** de un toque desde «aA» (se descarga de addons.mozilla.org).

## 0.3.0
- **Modo lectura**: icono junto a «aA» en los artículos; texto limpio con título, autor y tiempo de lectura (Readability de Mozilla, Apache 2.0).
- **Iconos reales** de cada web en favoritos, sugerencias y pestañas (los indica la propia página; caché de 7 días).
- **Ajustes**: buscador (DuckDuckGo, Google, Startpage, Brave, Ecosia o uno propio como SearXNG con `%s`) y borrar historial, cookies y caché.
- Extensión interna en `app/src/main/assets/extensions/brisa/` que comunica cada página con la app.

## 0.4.0
- **Mantener pulsado** un enlace o imagen: pestaña nueva o privada, copiar, compartir, abrir imagen.
- Los enlaces que abren **ventana nueva** crean una pestaña al lado.
- **Vídeo a pantalla completa** de verdad (sin barras); «atrás» sale.
- Las pestañas se **restauran con su historial** atrás/adelante (`pestanas.json`).
- **Gestor de descargas** en «aA»: progreso, cancelar, abrir, limpiar lista.

## 0.5.0
- **Ventanas de las webs**: desplegables (incluidos múltiples y con grupos), avisos y confirmaciones, texto, fecha y hora, **subir archivos** y **usuario/contraseña HTTP**.
- **Permisos** con diálogo propio: ubicación, notificaciones, cámara y micrófono (y después el permiso de Android). El sonido automático se bloquea; los vídeos mudos sí se reproducen.

## Compilar
Ramas: **`dev`** para todo lo nuevo (Actions compila, pasa pruebas unitarias y lint y deja el APK como artefacto) → repaso de [PRUEBAS.md](PRUEBAS.md) en el Pixel → **`main`** → Release con etiqueta `vX.Y.Z` (creada desde GitHub → Releases → Draft a new release; Actions adjunta el APK).
Solo genera APK para arm64 (Pixel). El APK pesa ~70 MB por el motor Gecko.

## Siguiente (0.6)
Guardar y rellenar contraseñas, página de inicio con «Visitados con frecuencia» y ajustes de permisos por web.
