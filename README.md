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

## 0.8.0 · contraseñas
- **Guardar y rellenar contraseñas** de Gecko (formularios y usuario/contraseña HTTP), con «Guardar / Nunca / Ahora no» y «Actualizar».
- **Cifradas** con una clave AES-256 del almacén de claves de Android (no sale del chip de seguridad); archivo `contrasenas.bin` dentro de la app, sin nube.
- **Ajustes → Contraseñas**: protegido con huella/PIN; buscar, ver, copiar (marcado como sensible), editar, borrar y lista «Nunca guardar en».
- Compatible con gestores externos (Bitwarden…) mediante el autorrelleno de Android.

## 0.9.0 · seguridad y privacidad
- **Solo HTTPS** (no / en privadas / siempre) con aviso y «Continuar sin seguridad» puntual; la red local queda fuera.
- **DNS cifrado** (DoH): automático o estricto; Quad9, Mullvad, Mullvad sin anuncios o Cloudflare. `.local`, `.lan`, `.home.arpa`… nunca pasan por él.
- **Protección contra huella digital** (también siempre en privadas).
- **Limpiar enlaces de rastreo**: `utm_*`, `fbclid`, `gclid`, `msclkid`, `igshid` y otros ~40.
- **Rechazar avisos de cookies** de los gestores más comunes (extensión interna; OneTrust, Didomi, Cookiebot, Sourcepoint, Quantcast, Usercentrics…).
- **Global Privacy Control** y **protección de IP en WebRTC**.
- **Borrar datos al salir** (opcional, desactivado por defecto).
- **Permisos por web** en Ajustes, con «Quitar».

## 0.9.1
- **Generador de contraseñas** en formularios de registro («Usar contraseña segura», 18 caracteres, `crypto.getRandomValues`).
- **Informe de privacidad**: rastreadores bloqueados por día y webs con más rastreo (7 días; datos solo en el móvil).
- **Escudo por web**: desactivar la protección de Gecko solo en una web que falle.
- **Pestañas privadas bloqueadas** con huella/PIN al volver a la app (opcional, activado por defecto).

## 0.9.2 · motor y herramientas al día
- **GeckoView 159** (antes 145): un año de parches de seguridad de Mozilla, más velocidad y compatibilidad.
- **Android Gradle Plugin 9.1** con Kotlin integrado, **Kotlin 2.4**, **Gradle 9.8**, compilación contra **Android 37** (`targetSdk` sigue en 35).
- Acciones de GitHub en Node 24 (`upload-artifact@v6`).
- **Aviso mensual** (`.github/workflows/gecko.yml`): si Mozilla publica una GeckoView nueva, abre un issue. Funciona desde `main` (a partir de la 1.0).
- Si una compilación falla, el registro completo queda en la rama `ci-log`.

## 0.9.3 · tablets
- **Diseño de tablet** (ancho mínimo ≥ 600 dp y ventana ancha): barra arriba con dirección centrada; las pestañas, con el mismo botón que en el móvil; sugerencias desplegadas hacia abajo. En pantalla dividida estrecha vuelve el diseño de móvil.
- **Webs de escritorio por defecto en tablet** (como el iPad), cambiable por web en «aA» y en Ajustes.
- **Inicio adaptado**: 5–8 favoritos por fila y contenido centrado.
- **Atajos de teclado**: Ctrl+T/W/L/F/R/D, Ctrl+Tab, Ctrl+1…9, Ctrl+Mayús+N, Alt+←/→, F5.
- Girar, plegar o redimensionar la ventana no recarga la página.

## 0.9.4 · animación de descargas
- El icono **vuela en arco** hasta un **botón de descargas con anillo de progreso** (móvil y tablet).
- Al terminar: ✓ con rebote y vibración suave; si falla, anillo rojo. El botón se oculta solo.
- Lista de descargas con entrada deslizante y barras suaves; sin mensajes de texto (salvo errores).
- Tablet: fuera la tira de pestañas; se usan como en el móvil.

## 0.9.5 · seguridad
- **Firma propia**: la clave ya no está en el repositorio; llega desde los secretos `KEYSTORE_BASE64` y `KEYSTORE_PASSWORD`. Sin ellos se compila y se prueba, pero no sale APK. *(Cambio de firma: hay que reinstalar una vez.)*
- **Sin copias de seguridad** de Android (ni nube ni traspaso a otro móvil).
- **Extensiones**: solo uBlock Origin se instala sin preguntar; las demás piden confirmación con sus permisos.
- **Otras apps solo pueden abrir webs** (`http`/`https`), nunca `file://`, `about:` o `javascript:`.
- **Preguntar antes de descargar** (activado por defecto).
- **Privadas sin rastro**: no guardan iconos y Brisa no sale en la miniatura de «Recientes» mientras haya alguna.
- Las direcciones e IPs (incluidas las del homelab) **no se envían** al buscador para sugerencias.
- Los iconos no pueden apuntar a la red local desde webs externas, sin redirecciones y con límite de 512 KB.
- `about:config` desactivado.

## Compilar
Ramas: **`dev`** para todo lo nuevo (Actions compila, pasa pruebas unitarias y lint y deja el APK como artefacto) → repaso de [PRUEBAS.md](PRUEBAS.md) en el Pixel → **`main`** → Release con etiqueta `vX.Y.Z` (creada desde GitHub → Releases → Draft a new release; Actions adjunta el APK).
Solo genera APK para arm64 (Pixel). El APK pesa ~70 MB por el motor Gecko.

## Hoja de ruta hasta la 1.0
- **1.0** · Repaso completo de PRUEBAS.md, paso de `dev` a `main`, Release.

## Después de la 1.0 (aparcado)
- **Sincronización** de favoritos, historial y pestañas con un servicio propio en el homelab (contenedor Docker en Proxmox/Portainer). Sin fecha: no se empieza hasta que se decida.
