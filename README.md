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

## 0.6.0
- **Estilo cristal (liquid glass)**: barra flotante en dos cápsulas translúcidas con brillo en el borde, cápsula mínima al hacer scroll y sugerencias en cristal. Android no deja desenfocar lo que pinta Gecko debajo, así que es translucidez calibrada, no desenfoque real.
- **Colores claro/oscuro corregidos**: textos que salían en negro en modo oscuro, texto invisible en la barra privada y diálogos del sistema que no seguían el tema.
- **Tocar la barra la deja vacía** para escribir; la página actual aparece arriba con un lápiz para editar su dirección.

## 0.7.0
- **Sugerencias del buscador** al escribir (DuckDuckGo; se pueden desactivar), con ↖ para completar.
- **Visitados con frecuencia** en el Inicio.
- **Grupos de pestañas**: mantener pulsada una pestaña para agruparla; pastillas para filtrar; se guardan.
- **Velocidad**: pestaña nueva precalentada y pantalla a la frecuencia máxima (90/120 Hz).

## 0.7.1 · velocidad de carga
- **uBlock Origin se instala solo** en el primer arranque (una vez; se puede quitar).
- **Solo la pestaña visible trabaja**: las de fondo se pausan y no compiten por red ni procesador.
- **Restauración diferida**: al reabrir la app solo carga la pestaña visible.
- La comprobación de modo lectura espera a que el móvil esté libre.

## Compilar
Ramas: **`dev`** para todo lo nuevo (Actions compila, pasa pruebas unitarias y lint y deja el APK como artefacto) → repaso de [PRUEBAS.md](PRUEBAS.md) en el Pixel → **`main`** → Release con etiqueta `vX.Y.Z` (creada desde GitHub → Releases → Draft a new release; Actions adjunta el APK).
Solo genera APK para arm64 (Pixel). El APK pesa ~70 MB por el motor Gecko.

## Hoja de ruta hasta la 1.0
- **0.8** · Guardar y rellenar contraseñas (cifradas con el almacén de claves de Android; compatible con Bitwarden/autorrelleno).
- **0.9** · Permisos por web en Ajustes y sincronización opcional de favoritos/historial con un servicio en el homelab (Proxmox).
- **1.0** · Repaso completo de PRUEBAS.md, paso de `dev` a `main`, Release.
