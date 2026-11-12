# Novedades

Historial de versiones de Brisa, de la más reciente a la más antigua. Las descargas están en [Releases](../../releases).

## 1.1.1 · páginas de error y certificados del homelab (issue #61)
- **Ya no se queda en negro**: cuando una web no carga (certificado no válido, servidor apagado, sin conexión, puerto cerrado…), Brisa enseña una página que explica qué pasa, con «Volver» y, si tiene sentido, «Reintentar».
- **Certificados autofirmados** (Proxmox, el router, un NAS…): en «Avanzado» se ve para quién es el certificado, quién lo firma, hasta cuándo vale y su huella, y se puede **aceptar el riesgo y continuar**. En la red local Brisa lo recuerda; en internet, solo hasta cerrar Brisa. Si la web exige conexión segura siempre (HSTS), no se deja continuar.

## 1.1.0 · extensiones (issue #16, gracias @perritys)
- **Instalar extensiones**: desde addons.mozilla.org dentro de Brisa (el botón «Añadir a Firefox» funciona) o pegando el enlace de la extensión. Solo extensiones firmadas por Mozilla, y antes de instalar se enseñan sus permisos.
- **Pantalla de extensiones** (menú aA y Ajustes): activar, desactivar, permitir en pestañas privadas, opciones y desinstalar.
- **Su botón y su ventanita**: las extensiones con botón salen en el menú aA; al tocarlo se abre su ventanita o hacen su acción. También pueden abrir pestañas propias y su página de opciones.
- **Recomendadas**: uBlock Origin, Bitwarden, Dark Reader y SponsorBlock, que se instalan con un toque. El resto sale como «experimental».

## 1.0.11 · Gecko 160, miniaturas guardadas, sin parpadeo y cambios de red (issues #55, #57, #58, #59, gracias @perritys)
- **Motor Gecko 160** (#57): con los últimos parches de seguridad de Mozilla.
- **Miniaturas** (#58): se guardan reducidas en disco (solo las de pestañas normales, nunca las privadas), así que siguen ahí aunque Android cierre Brisa. Se hacen también al cambiar de pestaña y al salir, y no se guardan en blanco si la página aún no ha pintado o se está recargando.
- **Sin parpadeo** (#55): al abrir un enlace desde otra app ya no se ve la pestaña anterior; fondo neutro con la barra de progreso hasta que la página pinta.
- **Cambios de red** (#59): al quitar la VPN o pasar de Wi-Fi a datos, Brisa avisa a Gecko para que tire las conexiones muertas y reinicia el DNS cifrado. Si aun así se atasca, el menú aA tiene **«Reiniciar Brisa»**, que conserva las pestañas.

## 1.0.10 · menú de enlace fantasma y miniaturas que se quedan (issues #53 y #54, gracias @perritys)
- **Menú de enlace de otra pestaña** (#53): al cambiar de pestaña o volver a Brisa desde otra app ya no aparece el menú «Abrir en pestaña nueva / Copiar / Compartir» de un enlace de la pestaña anterior. Se cierra el toque a medias antes de cambiar de pestaña y solo se muestran menús de la pestaña que se está viendo.
- **Miniaturas de la cuadrícula** (#54): ya no se borran cada vez que Brisa pasa al fondo; solo cuando Android avisa de que falta memoria de verdad.

## 1.0.9 · enlaces que se abren en su app y más fluidez (issues #44–#51)
- **Abrir en la app**: los enlaces de YouTube, WhatsApp, Telegram, Maps… tocados desde otra web ofrecen «¿Abrir en YouTube?». También funcionan los enlaces `tg://`, `whatsapp://`, `intent://`, `mailto:`, `tel:`… (antes daban error). Ajustes: «Abrir enlaces en sus apps» y «Preguntar antes» (en privadas siempre pregunta).
- **Fluidez** (#47): el scroll y la carga ya no recomponen toda la interfaz; solo las barras cuando cambian.
- **Avisos de cookies** (#48): el rechazo automático ya no carga la CPU al abrir webs (búsqueda agrupada, 6 s, sin recorrer iframes de anuncios).
- **Borrar datos al salir**: se borra a los 5 minutos de salir de verdad (#51), también si se salió con «Compartir» o abriendo otra app (#49), y un enlace que trae de vuelta a Brisa ya no se pierde con el borrado (#50).
- **Pestañas caídas en segundo plano** (#44): se recuperan al volver, sin gastar red ni avisos mientras tanto.
- **Ventanas emergentes de inicio de sesión** (#45): Brisa deja que Gecko abra su sesión en vez de adelantarse.

## 1.0.8.1 · «Borrar datos al salir» que borra de verdad (issue #43, gracias @perritys)
- Con el ajuste activado, el menú aA tiene **«Salir y borrar datos»**: borra todo y cierra Brisa, también de Recientes.
- Pasar más de 5 minutos fuera de Brisa también cuenta como salir: al volver está todo borrado.
- El texto del ajuste explica cuándo se borra.

## 1.0.8 · arranque rápido y pestañas que no se caen (issues #36–#41, gracias @perritys)
- **Arranque** (#36): las pestañas guardadas no abren su sesión en el motor hasta que se miran; con 30 pestañas arranca como con 1.
- **Pantalla en negro** (#36): si el proceso de las webs se cae o Android lo cierra por memoria, la pestaña se recupera sola.
- **Memoria** (#36): las miniaturas de la cuadrícula ocupan un tercio de ancho y alto (≈1 MB en vez de 10) y se sueltan si falta memoria.
- **Una sola Brisa** (#37): los enlaces de otras apps se abren en la Brisa que ya está abierta; ya no se pierden pestañas.
- **Inicios de sesión** (#38): las ventanas de «Entrar con Google/Microsoft/GitHub» se cierran solas al terminar y se vuelve a la web original.
- **Notificaciones** (#39): tocarla lleva a la pestaña de esa web.
- **Permisos** (#40): las pestañas esperan a que se retiren los «Solo esta vez» de la vez anterior antes de cargar.
- **Gesto atrás** (#41): animación de «volver a inicio» de Android y Brisa nunca se cierra al salir con atrás, así que al volver no recarga todo.

## 1.0.7 · seguridad de permisos, diálogos y contraseñas (issues #31–#34, gracias @perritys)
- **Permisos** (#31): «Solo esta vez» ya no se queda para siempre si Android cierra Brisa: se apunta y se retira al volver a abrirla.
- **Diálogos** (#33): un `alert()` que recarga la página ya no esquiva «Impedir más diálogos»; solo se reinicia al ir a otra web.
- **Contraseñas** (#32): sin capturas ni miniatura en «Recientes» mientras se ven, y al salir de Brisa hay que desbloquearlas otra vez.
- **Notificaciones** (#34): si Android cerró Brisa, tocar una notificación abre la web que la mandó.

## 1.0.6 · privadas a prueba, permisos y notificaciones (issues #20–#29, gracias @perritys)
- **Privacidad** (#26): «Abrir imagen», favoritos e historial abren la dirección tal cual (`data:`, `blob:`, páginas de extensiones) y nunca la mandan al buscador.
- **Privadas** (#29): desde una privada, «Abrir en pestaña nueva» abre otra privada; pasar a normal es una opción explícita.
- **Privadas** (#27): bloqueadas de verdad: la barra no muestra el dominio y pide desbloquear, no se puede navegar ni usar «aA» o atajos, y no suena nada hasta la huella/PIN.
- **Privadas** (#28): subir un archivo, dar un permiso o compartir no las bloquea al volver (salvo más de 2 minutos fuera).
- **Borrar datos** (#20): también borra la caché de iconos, el informe de privacidad y la lista de descargas.
- **Diálogos** (#21): cada `alert()` dice de qué web viene y, desde el segundo, casilla «Impedir que esta página cree más diálogos».
- **Notificaciones de las webs** (#23): ahora llegan como notificaciones de Android (con Brisa abierta o en segundo plano).
- **Permisos** (#24): «Solo esta vez», que se retira al salir de la web o cerrar la pestaña.
- **Pantalla** (#22): ya no se fuerzan los 120 Hz; Android baja la frecuencia con la página quieta y ahorra batería.
- **DRM** (#25): interruptor «Vídeo protegido (DRM)» en Ajustes (activado por defecto).

## 1.0.5 · gestos, uBlock e icono nuevo (issues #13, #14, #17, #18)
- **Estirar para recargar** (#14): en lo alto de la página, tira hacia abajo. Solo si la web no ha usado el gesto (no salta en mapas, chats o carruseles). Se puede desactivar en Ajustes.
- **uBlock Origin** (#18): sus ajustes, filtros y estadísticas se abren desde «aA» y desde el informe de privacidad. El informe explica que, con uBlock activo, es normal ver pocos bloqueos de Gecko.
- **Icono nuevo** (#17): «brújula de viento», adaptativo (encaja con la forma de cada lanzador) y con capa monocroma para los iconos temáticos de Android 13+.
- **Detalles** (#13): las contraseñas pedidas desde pestañas de fondo no ofrecen las de `https` si no consta que la página lo es; la barra solo abre `http(s)` y `about:` escritos a mano; Gradle Wrapper 9.8.2.

## 1.0.4 · Gecko 159
- Motor GeckoView 159.0.20261102144711, con los últimos parches de seguridad de Mozilla.

## 1.0.3 · revisión de la comunidad (issues #3–#11, gracias @perritys)
- **Contraseñas** (#3): si no se pueden leer, el archivo se aparta (`contrasenas.bin.roto-<fecha>`) y se avisa en Ajustes; nunca se sobrescribe. Copia `.bak` antes de cada guardado.
- **Contraseñas** (#8): se emparejan por origen (esquema, servidor y puerto); una de `https` nunca se ofrece en `http` (salvo red local). Arreglado el relleno en webs con puerto (`192.168.1.50:8006`).
- **Privadas** (#4): `FLAG_SECURE` mientras haya alguna abierta: sin capturas ni miniatura en Recientes.
- **Red** (#5, #6): iconos y sugerencias con la red de Gecko (DNS cifrado, anónimas, sin redirecciones). Se comprueban las IPs resueltas para frenar el *DNS rebinding*; IPv6 local incluida. Sin tráfico en claro fuera de Gecko.
- **Solo HTTPS** (#7): «Continuar sin seguridad» vale solo para esa pestaña, se cierra al terminar su carga o a los 15 s, y las demás pestañas no abren `http` mientras tanto.
- **Barra** (#9): `localhost:3000`, `proxmox:8006`, `nas/admin`, IPv6 (`[fd00::1]:8080`) y `.lan`/`.local`/`.home.arpa` son direcciones. Una palabra suelta ofrece «Ir a http://…/».
- **Descargas** (#10): servicio en primer plano con notificación (no se cortan al salir); Android 8-9 guarda el flujo de Gecko con su sesión; las de pestañas privadas no quedan en la lista.
- **Mantenimiento** (#11): `MainActivity.kt` dividido por pantallas, Gradle fijado (9.8.2), historial guardado en segundo plano y más pruebas (cuarentena de contraseñas, emparejado por origen, IPv6, IPs locales, barra).

## 1.0.2
- **Autorrelleno de Android** (issue #2): la vista de Gecko participa de forma explícita en el autorrelleno del sistema, para que gestores como Bitwarden, 1Password o KeePassDX ofrezcan rellenar usuario y contraseña.

## 1.0.1
- **DNS propio** (issue #1): en DNS cifrado, opción «Propio» con la dirección DoH de tu servidor (AdGuard Home, Pi-hole con DoH, NextDNS…). Se valida que sea `https://` y avisa si lo usas en modo estricto.

## 1.0.0 · primera versión estable
Reúne todo lo de la 0.1 a la 0.9.5: navegador Gecko 159 con estilo Safari y cristal, barra inferior (móvil) o superior (tablet), pestañas con grupos y privadas bloqueadas, favoritos y frecuentes, historial y sugerencias, descargas animadas que preguntan antes, modo lectura, contraseñas cifradas con generador, uBlock Origin, y la capa de seguridad y privacidad (solo HTTPS, DNS cifrado, huella digital, enlaces limpios, cookies rechazadas, GPC, WebRTC, permisos por web, informe y escudo por web).

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

## 0.9.4 · animación de descargas
- El icono **vuela en arco** hasta un **botón de descargas con anillo de progreso** (móvil y tablet).
- Al terminar: ✓ con rebote y vibración suave; si falla, anillo rojo. El botón se oculta solo.
- Lista de descargas con entrada deslizante y barras suaves; sin mensajes de texto (salvo errores).
- Tablet: fuera la tira de pestañas; se usan como en el móvil.

## 0.9.3 · tablets
- **Diseño de tablet** (ancho mínimo ≥ 600 dp y ventana ancha): barra arriba con dirección centrada; las pestañas, con el mismo botón que en el móvil; sugerencias desplegadas hacia abajo. En pantalla dividida estrecha vuelve el diseño de móvil.
- **Webs de escritorio por defecto en tablet** (como el iPad), cambiable por web en «aA» y en Ajustes.
- **Inicio adaptado**: 5–8 favoritos por fila y contenido centrado.
- **Atajos de teclado**: Ctrl+T/W/L/F/R/D, Ctrl+Tab, Ctrl+1…9, Ctrl+Mayús+N, Alt+←/→, F5.
- Girar, plegar o redimensionar la ventana no recarga la página.

## 0.9.2 · motor y herramientas al día
- **GeckoView 159** (antes 145): un año de parches de seguridad de Mozilla, más velocidad y compatibilidad.
- **Android Gradle Plugin 9.1** con Kotlin integrado, **Kotlin 2.4**, **Gradle 9.8**, compilación contra **Android 37** (`targetSdk` sigue en 35).
- Acciones de GitHub en Node 24 (`upload-artifact@v6`).
- **Aviso diario** (`.github/workflows/gecko.yml`): si Mozilla publica una GeckoView nueva, abre un issue. Funciona desde `main` (a partir de la 1.0).
- Si una compilación falla, el registro completo queda en la rama `ci-log`.

## 0.9.1
- **Generador de contraseñas** en formularios de registro («Usar contraseña segura», 18 caracteres, `crypto.getRandomValues`).
- **Informe de privacidad**: rastreadores bloqueados por día y webs con más rastreo (7 días; datos solo en el móvil).
- **Escudo por web**: desactivar la protección de Gecko solo en una web que falle.
- **Pestañas privadas bloqueadas** con huella/PIN al volver a la app (opcional, activado por defecto).

## 0.9.0 · seguridad y privacidad
- **Solo HTTPS** (no / en privadas / siempre) con aviso y «Continuar sin seguridad» puntual; la red local queda fuera.
- **DNS cifrado** (DoH): automático o estricto; Quad9, Mullvad, Mullvad sin anuncios o Cloudflare. `.local`, `.lan`, `.home.arpa`… nunca pasan por él.
- **Protección contra huella digital** (también siempre en privadas).
- **Limpiar enlaces de rastreo**: `utm_*`, `fbclid`, `gclid`, `msclkid`, `igshid` y otros ~40.
- **Rechazar avisos de cookies** de los gestores más comunes (extensión interna; OneTrust, Didomi, Cookiebot, Sourcepoint, Quantcast, Usercentrics…).
- **Global Privacy Control** y **protección de IP en WebRTC**.
- **Borrar datos al salir** (opcional, desactivado por defecto).
- **Permisos por web** en Ajustes, con «Quitar».

## 0.8.0 · contraseñas
- **Guardar y rellenar contraseñas** de Gecko (formularios y usuario/contraseña HTTP), con «Guardar / Nunca / Ahora no» y «Actualizar».
- **Cifradas** con una clave AES-256 del almacén de claves de Android (no sale del chip de seguridad); archivo `contrasenas.bin` dentro de la app, sin nube.
- **Ajustes → Contraseñas**: protegido con huella/PIN; buscar, ver, copiar (marcado como sensible), editar, borrar y lista «Nunca guardar en».
- Compatible con gestores externos (Bitwarden…) mediante el autorrelleno de Android.

## 0.7.1 · velocidad de carga
- **uBlock Origin se instala solo** en el primer arranque (una vez; se puede quitar).
- **Solo la pestaña visible trabaja**: las de fondo se pausan y no compiten por red ni procesador.
- **Restauración diferida**: al reabrir la app solo carga la pestaña visible.
- La comprobación de modo lectura espera a que el móvil esté libre.

## 0.7.0
- **Sugerencias del buscador** al escribir (DuckDuckGo; se pueden desactivar), con ↖ para completar.
- **Visitados con frecuencia** en el Inicio.
- **Grupos de pestañas**: mantener pulsada una pestaña para agruparla; pastillas para filtrar; se guardan.
- **Velocidad**: pestaña nueva precalentada y pantalla a la frecuencia máxima (90/120 Hz).

## 0.6.0
- **Estilo cristal (liquid glass)**: barra flotante en dos cápsulas translúcidas con brillo en el borde, cápsula mínima al hacer scroll y sugerencias en cristal. Android no deja desenfocar lo que pinta Gecko debajo, así que es translucidez calibrada, no desenfoque real.
- **Colores claro/oscuro corregidos**: textos que salían en negro en modo oscuro, texto invisible en la barra privada y diálogos del sistema que no seguían el tema.
- **Tocar la barra la deja vacía** para escribir; la página actual aparece arriba con un lápiz para editar su dirección.

## 0.5.0
- **Ventanas de las webs**: desplegables (incluidos múltiples y con grupos), avisos y confirmaciones, texto, fecha y hora, **subir archivos** y **usuario/contraseña HTTP**.
- **Permisos** con diálogo propio: ubicación, notificaciones, cámara y micrófono (y después el permiso de Android). El sonido automático se bloquea; los vídeos mudos sí se reproducen.

## 0.4.0
- **Mantener pulsado** un enlace o imagen: pestaña nueva o privada, copiar, compartir, abrir imagen.
- Los enlaces que abren **ventana nueva** crean una pestaña al lado.
- **Vídeo a pantalla completa** de verdad (sin barras); «atrás» sale.
- Las pestañas se **restauran con su historial** atrás/adelante (`pestanas.json`).
- **Gestor de descargas** en «aA»: progreso, cancelar, abrir, limpiar lista.

## 0.3.0
- **Modo lectura**: icono junto a «aA» en los artículos; texto limpio con título, autor y tiempo de lectura (Readability de Mozilla, Apache 2.0).
- **Iconos reales** de cada web en favoritos, sugerencias y pestañas (los indica la propia página; caché de 7 días).
- **Ajustes**: buscador (DuckDuckGo, Google, Startpage, Brave, Ecosia o uno propio como SearXNG con `%s`) y borrar historial, cookies y caché.
- Extensión interna en `app/src/main/assets/extensions/brisa/` que comunica cada página con la app.

## 0.2.0
- **Sugerencias** del historial al escribir en la barra; **Historial** con fechas y «Borrar todo» (las privadas no guardan nada).
- **Descargas** a `Descargas/Brisa`, conservando la sesión de la web (Android 10+).
- **Buscar en la página** con contador y flechas.
- **Tamaño del texto** de las webs (80 – 160 %), se recuerda.
- **uBlock Origin** de un toque desde «aA» (se descarga de addons.mozilla.org).

## 0.1.0
- Barra de direcciones **abajo** que se encoge al bajar por la página y vuelve al subir.
- **Desliza la barra** a izquierda/derecha para cambiar de pestaña (al final, abre una nueva).
- **Cuadrícula de pestañas** con miniaturas, pestañas **privadas** y contador.
- **Inicio** con favoritos (botón marcador para añadir; mantener pulsado para quitar).
- Menú **«aA»**: rastreadores bloqueados, versión de escritorio, pestaña privada, abrir en otra app.
- Protección **estricta** contra rastreadores, cookies de terceros aisladas, Safe Browsing.
- Escribe una dirección, una IP del homelab (`192.168.1.50:8006`) o una búsqueda (DuckDuckGo).
- Se abre desde enlaces de otras apps y recuerda las pestañas al cerrarla.
