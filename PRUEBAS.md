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

## 8. Novedades 0.4
- [ ] Mantener pulsado un enlace: abrir en pestaña nueva (se queda en segundo plano), en privada, copiar y compartir.
- [ ] Mantener pulsada una imagen: abrir imagen y copiar su dirección.
- [ ] Un enlace que abre ventana nueva (target=_blank, p. ej. resultados de Google con «abrir en pestaña nueva») crea una pestaña al lado.
- [ ] Vídeo de YouTube a pantalla completa: se ocultan la barra y las del sistema; «atrás» sale de pantalla completa.
- [ ] Cerrar la app del todo y volver: las pestañas vuelven y «atrás» recuerda las páginas anteriores de cada una.
- [ ] «aA» → Descargas: progreso con MB, cancelar una descarga en curso, tocar una terminada la abre, «Limpiar lista».
- [ ] La lista de descargas se conserva al reiniciar la app.

## 9. Novedades 0.5 · ventanas de las webs y permisos
- [ ] Desplegable `<select>` (p. ej. elegir provincia en un formulario): sale la lista y se marca la opción.
- [ ] Selección múltiple y listas con grupos.
- [ ] `alert`, `confirm` y `prompt` (probar en una web de pruebas como «javascript alert test»).
- [ ] Campo de fecha y de hora: salen los selectores de Android.
- [ ] Subir un archivo (foto a un formulario, o subir a Cloudreve web): se abre el selector y se sube.
- [ ] Web con usuario/contraseña HTTP (router, Proxmox detrás de auth básica…): sale «Iniciar sesión».
- [ ] Ubicación (Google Maps web → mi ubicación): primero el permiso de Brisa, luego el de Android.
- [ ] Cámara/micrófono (webcamtests.com o Jitsi): pregunta, y con «Permitir» funciona.
- [ ] «No permitir» en cualquiera de ellos: la web sigue funcionando sin cerrarse.

## 10. Novedades 0.6 · cristal y correcciones
- [ ] Modo claro y oscuro (cambiarlo con la app abierta): todos los textos se leen — Inicio, Favoritos, pestañas, menús, historial, descargas.
- [ ] Pestaña privada: el texto que se escribe en la barra se ve (blanco sobre gris oscuro).
- [ ] Barra flotante de cristal: cápsula de dirección y cápsula de botones separadas; la página se ve por debajo.
- [ ] Al bajar, la barra se reduce a una cápsula pequeña con el dominio; al tocarla vuelve.
- [ ] La línea de carga aparece dentro de la cápsula de dirección.
- [ ] Tocar la barra: el campo sale vacío, listo para escribir; arriba aparece la página actual con un lápiz para editar su dirección.
- [ ] Los selectores de fecha/hora y los avisos del sistema siguen el tema claro/oscuro.
- [ ] En Inicio, los últimos favoritos no quedan tapados por la barra.

## 11. Novedades 0.7
- [ ] Escribir en la barra: además del historial, salen hasta 4 sugerencias del buscador; la flecha ↖ completa el texto sin buscar.
- [ ] Ajustes → Sugerencias de búsqueda apagado: solo sale el historial. En privadas nunca salen.
- [ ] Inicio: «Visitados con frecuencia» con las webs que más abres (no repite los favoritos).
- [ ] Mantener pulsada una pestaña en la cuadrícula → crear grupo «Homelab»; aparecen las pastillas «Todas / Homelab · 1».
- [ ] Mover otra pestaña al grupo, filtrar por grupo, «Quitar del grupo».
- [ ] Con un grupo seleccionado, «+» crea la pestaña dentro de ese grupo.
- [ ] Los grupos se mantienen al cerrar y abrir la app.
- [ ] Abrir pestaña nueva desde la cuadrícula o deslizando la barra: aparece al instante (pestaña precalentada).
- [ ] Scroll a 120 Hz en el Pixel (Ajustes de Android → Pantalla → Pantalla fluida activada).

## 12. Novedades 0.7.1 · velocidad de carga
- [ ] Primer arranque tras instalar: a los pocos segundos sale «Bloqueador de anuncios activado»; «aA» muestra «uBlock Origin activo».
- [ ] Comparar con la 0.7.0 / Chrome: elpais.com, marca.com, 20minutos.es cargan más rápido y sin anuncios.
- [ ] Con 5+ pestañas abiertas, una web nueva carga igual de rápido que con una sola pestaña.
- [ ] Un vídeo en una pestaña de fondo se pausa al cambiar de pestaña y sigue al volver.
- [ ] Al reabrir la app con muchas pestañas: solo carga la visible; las demás cargan al entrar en ellas (con su «atrás» intacto).
- [ ] El icono de modo lectura sigue apareciendo en los artículos (puede tardar un segundo más).

## 13. Novedades 0.8 · contraseñas
- [ ] Iniciar sesión en una web (p. ej. Cloudreve, Immich o Portainer del homelab): sale «¿Guardar la contraseña de …?» con el usuario.
- [ ] «Guardar» → cerrar sesión → volver: el usuario y la contraseña se rellenan solos.
- [ ] Cambiar la contraseña en la web: sale «¿Actualizar…?».
- [ ] «Nunca» en una web: no vuelve a preguntar; aparece en Ajustes → Contraseñas → «Nunca guardar en», y se puede quitar.
- [ ] Dos cuentas en la misma web: al rellenar sale «Elegir cuenta».
- [ ] Ajustes → Contraseñas guardadas: pide huella/PIN; si se cancela, no se abre.
- [ ] Buscar, ver (ojo), copiar usuario y contraseña, editar y borrar (pide confirmar).
- [ ] Ajustes → «Ofrecer guardar contraseñas» apagado: no pregunta (lo guardado se sigue rellenando).
- [ ] Pestañas privadas: no ofrece guardar.
- [ ] Bitwarden u otro gestor (autorrelleno de Android) sigue funcionando en los formularios.

## 14. Novedades 0.9 · seguridad y privacidad
- [ ] Solo HTTPS «Siempre»: abrir `http://neverssl.com` → aviso «no tiene conexión segura»; «Volver» y «Continuar sin seguridad» funcionan.
- [ ] Después de continuar, otra web http vuelve a avisar (el permiso es solo para esa carga).
- [ ] Proxmox/router por IP (`192.168.x.x`) y nombres `.local` abren sin aviso.
- [ ] DNS cifrado «Automático» + Quad9: las webs cargan normal. Comprobar en `https://on.quad9.net` (dice «Yes»).
- [ ] DNS «Estricto»: sigue funcionando; probar también con Mullvad (sin anuncios).
- [ ] Huella digital: en `https://coveryourtracks.eff.org` el resultado mejora respecto a la desactivada.
- [ ] Enlace con `?utm_source=x&fbclid=y`: al abrirlo, la dirección queda limpia.
- [ ] Avisos de cookies: elpais.com, marca.com, 20minutos.es, xataka.com, as.com → se rechazan solos (o no aparecen). Anotar los que no.
- [ ] Apagar «Rechazar avisos de cookies»: vuelven a salir los avisos.
- [ ] GPC: en `https://global-privacy-control.glitch.me` sale detectado.
- [ ] Proteger IP en videollamadas: en `https://browserleaks.com/webrtc` no aparece la IP local (192.168.x.x). Jitsi sigue funcionando.
- [ ] Borrar datos al salir (activado): cerrar Brisa desde Recientes y volver → sin pestañas, sin historial y con las sesiones cerradas.
- [ ] Permisos por web: dar ubicación a maps.google.com → aparece en la lista; «Quitar» → vuelve a preguntar.

## 15. Novedades 0.9.1
- [ ] Registro en una web (formulario con «nueva contraseña» o dos campos de contraseña): al tocar el campo sale «Usar contraseña segura»; rellena los dos y se ve un momento.
- [ ] En formularios de inicio de sesión NO aparece.
- [ ] Al enviar el registro, Brisa ofrece guardar la contraseña generada.
- [ ] «aA» → tocar la fila del escudo: Informe de privacidad con total, barras por día y webs con más rastreadores.
- [ ] «Borrar informe» lo deja a cero. Las pestañas privadas no suman.
- [ ] Escudo por web: el interruptor en «aA» desactiva la protección solo en esa web (recarga; pone «Protección desactivada en …»). Otra web sigue protegida. Volver a activarlo.
- [ ] Pestañas privadas con bloqueo: abrir una privada, salir a la pantalla de inicio de Android y volver → «Pestañas privadas bloqueadas»; «Desbloquear» pide huella/PIN.
- [ ] La vista «Privada» de la cuadrícula también queda bloqueada; las normales no.
- [ ] Ajustes → «Bloquear pestañas privadas» apagado: no se bloquean.

## 16. Novedades 0.9.2 · Gecko 159
- [ ] Instalar encima de la 0.9.1: se conservan pestañas, favoritos, historial, contraseñas y ajustes.
- [ ] uBlock Origin sigue activo y los avisos de cookies se siguen rechazando.
- [ ] En «aA» → Ajustes aparece la versión 0.9.2.
- [ ] Repaso rápido: YouTube (vídeo y pantalla completa), Google Maps, un login del homelab, una descarga y el modo lectura.

## 17. Novedades 0.9.3 · tablet (probar en la tablet)
- [ ] Al abrir: barra arriba (atrás, adelante, dirección centrada, compartir, favorito, +, pestañas), sin tira de pestañas.
- [ ] Tocar la barra: se escribe y las sugerencias salen debajo, por encima de la página.
- [ ] El botón de pestañas abre la cuadrícula como en el móvil; «+» abre una nueva.
- [ ] Las webs salen en versión de escritorio (probar elpais.com, youtube.com, wikipedia.org). «aA» → versión móvil funciona en una web.
- [ ] Ajustes → «Webs de escritorio en la tablet» apagado: las pestañas nuevas salen en versión móvil.
- [ ] Inicio: más favoritos por fila y contenido centrado.
- [ ] Girar la tablet: no se recarga la página y el diseño se adapta.
- [ ] Pantalla dividida con otra app: si Brisa queda estrecha, pasa al diseño de móvil (barra abajo); al ensancharla vuelve el de tablet.
- [ ] Vídeo a pantalla completa: desaparece la barra; «atrás» sale.
- [ ] Menús (aA, historial, descargas, ajustes) no ocupan toda la pantalla.
- [ ] Con teclado: Ctrl+T, Ctrl+W, Ctrl+L, Ctrl+Tab / Ctrl+Mayús+Tab, Ctrl+1…9, Ctrl+F, Ctrl+R/F5, Ctrl+D, Ctrl+Mayús+N, Alt+← / Alt+→.
- [ ] En el Pixel todo sigue igual que antes (barra abajo).

## 18. Novedades 0.9.4 · animación de descargas (móvil y tablet)
- [ ] Al descargar algo, un icono vuela en arco desde el centro hasta el botón de descargas que aparece en la barra.
- [ ] El botón muestra un anillo que se llena con el progreso (si el tamaño es desconocido, gira).
- [ ] Al terminar: el anillo se completa, sale ✓ con un rebote y una vibración suave; unos segundos después el botón desaparece.
- [ ] Tocar el botón abre la lista de descargas; la descarga nueva entra deslizándose y su barra avanza suave.
- [ ] Descarga que falla (cortar el wifi a mitad): anillo rojo con «!», vibración distinta y aviso de error.
- [ ] Ya no salen los mensajes «Descargando…» / «Descargado».
- [ ] Dos descargas a la vez: el anillo muestra el progreso conjunto.

## 19. Novedades 0.9.5 · seguridad
- [ ] **Primera vez:** desinstalar la versión anterior e instalar esta (cambia la firma). Volver a iniciar sesión donde haga falta.
- [ ] Descargar un archivo: pregunta «¿Descargar este archivo?» con nombre, tamaño y web. «Cancelar» no descarga nada.
- [ ] Ajustes → «Preguntar antes de descargar» apagado: descarga directa con la animación.
- [ ] Con una pestaña privada abierta, la miniatura de Brisa en «Recientes» sale en blanco/gris.
- [ ] En privadas, los iconos de las webs no se guardan (abrir una web nueva en privada; en una normal su icono aún no está).
- [ ] Escribir `192.168.1.50` o `nube.local`: no salen sugerencias del buscador (sí las del historial).
- [ ] Una web que intente instalar una extensión (addons.mozilla.org → «Añadir»): pide confirmación con los permisos.
- [ ] Abrir desde otra app un enlace `https://…`: funciona. (Lo que no sea http/https se ignora.)
- [ ] `about:config` ya no abre.
- [ ] Ajustes de Android → Sistema → Copia de seguridad: Brisa no aparece/no copia datos.

## 20. Novedades 1.0.1 · DNS propio
- [ ] Ajustes → DNS cifrado «Automático»: aparece «Propio» con un campo de texto.
- [ ] Escribir `http://…` o una IP sola: se marca en rojo y «Propio» no se puede elegir.
- [ ] Escribir una dirección válida (p. ej. `https://dns.nextdns.io/<tu id>` o la de tu AdGuard) y elegir «Propio»: las webs cargan. En NextDNS/AdGuard aparecen las consultas.
- [ ] Cerrar y abrir Brisa: sigue elegido el DNS propio.
- [ ] Volver a Quad9: la dirección propia sigue guardada en el campo.
- [ ] «Estricto» + DNS propio: sale el aviso en rojo.

## 21. Novedades 1.0.2 · autorrelleno de Android (Bitwarden)
- [ ] Ajustes de Android → Contraseñas y autorrelleno → servicio: Bitwarden.
- [ ] En un login (p. ej. github.com/login): al tocar el usuario aparece la sugerencia de Bitwarden (encima del teclado o como desplegable).
- [ ] Elegir la cuenta: rellena usuario y contraseña.
- [ ] Al enviar un login nuevo, Bitwarden ofrece guardarlo.
- [ ] Con el servicio de autorrelleno en «Ninguno», el gestor de contraseñas propio de Brisa sigue funcionando.

## 22. Novedades 1.0.3 · issues #3–#11
- [ ] Contraseñas: siguen todas al instalar encima; se rellenan en `https://…` y en el homelab con puerto (`http://192.168.x.x:8006`).
- [ ] Una contraseña guardada en `https://web` NO se ofrece en `http://web` (probar con «Solo HTTPS» en «No»).
- [ ] Con una pestaña privada abierta no se pueden hacer capturas y Recientes sale en negro; cerrándola vuelven.
- [ ] Iconos y sugerencias siguen funcionando (ahora por el DNS cifrado).
- [ ] «Continuar sin seguridad» en una web http y, a la vez, otra pestaña abriendo otra web http: la segunda carga después y avisa.
- [ ] Barra: `localhost:3000`, `proxmox:8006`, `nas/admin`, `[fd00::1]:8080`, `nube.lan` abren como dirección; `proxmox` busca y ofrece «Ir a http://proxmox/».
- [ ] Descarga grande: sale notificación con progreso; salir de Brisa no la corta.
- [ ] Descarga desde una pestaña privada: no aparece en la lista al reabrir la app; la notificación no muestra el nombre.

## 23. Novedades 1.0.5
- [ ] Estirar para recargar: en lo alto de una web, tirar hacia abajo → círculo con flecha que gira; al pasar el umbral se pone azul y al soltar recarga (indicador girando hasta que termina).
- [ ] No salta en Google Maps (mover el mapa hacia abajo), en un chat con scroll propio ni en un carrusel.
- [ ] En el Inicio no hace nada. En tablet, el indicador sale debajo de la barra.
- [ ] Ajustes → «Estirar hacia abajo para recargar» apagado: no hace nada.
- [ ] «aA» → uBlock Origin activo → abre su panel (ajustes, filtros, estadísticas) en una pestaña nueva.
- [ ] Informe de privacidad → botón «Estadísticas y filtros de uBlock Origin».
- [ ] Barra: `file:///sdcard` o `intent://…` se buscan; `about:blank` abre.
- [ ] Icono nuevo (brújula de viento) en el lanzador: ocupa la forma del lanzador, sin borde blanco.
- [ ] Con «Iconos temáticos» activados (Fondo de pantalla y estilo), el icono coge los colores del sistema.

## 24. Novedades 1.0.6 · issues #20–#29
- [ ] Mantener pulsada una imagen pequeña que cargue poco a poco (data:) → «Abrir imagen»: se ve la imagen, no una búsqueda.
- [ ] Guardar el panel de uBlock como favorito y abrirlo desde Inicio.
- [ ] En una privada, mantener pulsado un enlace → «Abrir en pestaña nueva» abre otra privada; «Abrir en pestaña normal» abre una normal.
- [ ] Privada con un vídeo sonando → salir a inicio → volver: no suena, la barra dice «Pestaña privada bloqueada» sin dominio; no se puede escribir en ella; el botón de pestañas sí funciona.
- [ ] En una privada, subir una foto a una web: al volver no pide huella y la foto se adjunta.
- [ ] «Borrar datos al salir» activado: al volver, no hay iconos de webs visitadas, el informe está a cero y la lista de descargas vacía.
- [ ] Una web con `alert()` en bucle: al segundo aviso sale la casilla «Impedir…»; marcándola se acaba. El título dice «<web> dice:».
- [ ] Dar permiso de notificaciones a una web de pruebas (p. ej. «web push test») y mandar una: llega a Android; tocarla abre la web.
- [ ] Ubicación en Google Maps → «Solo esta vez»: funciona; al irte a otra web y volver, vuelve a preguntar.
- [ ] Opciones de desarrollador → «Mostrar frecuencia de actualización»: con la página quieta baja de 120.
- [ ] Ajustes → «Vídeo protegido (DRM)» apagado: Netflix/Prime no reproducen; encendido sí.

## 25. Android
- [ ] Gesto «atrás»: cierra menú/teclado/cuadrícula y luego va a la página anterior.
- [ ] Un enlace desde otra app (WhatsApp, correo) se abre en una pestaña nueva.
- [ ] Compartir envía la URL.
- [ ] Tema claro/oscuro y colores de Material You.
- [ ] Girar el móvil no recarga la página.

## 26. Velocidad (comparar con Chrome)
- [ ] Abrir elpais.com, marca.com y youtube.com: sensación de carga igual o mejor.
- [ ] Scroll fluido en páginas largas, sin tirones.

## 25. Repaso rápido 1.0.4 · Gecko 159.0.20261102144711
- [ ] Instalar encima de la 1.0.3: se conservan pestañas, marcadores, historial y contraseñas.
- [ ] uBlock Origin sigue activo (sin anuncios en una web con publicidad).
- [ ] YouTube: un vídeo se reproduce y se puede poner a pantalla completa.
- [ ] Google Maps carga, se mueve y pide/usa la ubicación.
- [ ] Login de un servicio del homelab (p. ej. Proxmox): rellena y entra.
- [ ] Una descarga termina y se abre desde la lista.
- [ ] Modo lectura en un artículo de noticias.

## 25. Novedades 1.0.7 · issues #31–#34
- [ ] Dar ubicación «Solo esta vez» a una web → quitar Brisa de Recientes → abrirla y volver a esa web: vuelve a preguntar.
- [ ] Página con `alert("hola"); location.reload();` → desde el 2.º aviso sale la casilla; al marcarla no salen más aunque siga recargando. Ir a otra web: vuelven los diálogos.
- [ ] Abrir «Contraseñas guardadas» → la miniatura de Recientes sale en negro y no deja capturar.
- [ ] Con las contraseñas abiertas, salir a Inicio y volver: la lista se ha cerrado y pide huella/PIN.
- [ ] Recibir una notificación de una web → quitar Brisa de Recientes → tocar la notificación: abre Brisa con esa web.

## 26. Novedades 1.0.8 · issues #36–#41
- [ ] Con muchas pestañas guardadas (20-30), cerrar Brisa de Recientes y abrirla: arranca rápido. En la cuadrícula, tocar una pestaña dormida: carga con su historial atrás/adelante.
- [ ] Volver atrás hasta salir de Brisa con el gesto: animación suave a inicio. Al volver, no recarga todas las pestañas ni parpadea en negro.
- [ ] Con Brisa abierta, abrir un enlace desde Telegram o Gmail: en Recientes solo hay una Brisa y el enlace sale como pestaña nueva. Atrás desde ahí vuelve a la app de origen sin cerrar Brisa.
- [ ] «Entrar con Google» (o GitHub) en una web que use ventana emergente: al terminar, la ventana se cierra sola y se vuelve a la web.
- [ ] Dos pestañas, notificación de la que no se ve → tocarla: Brisa se abre en esa pestaña.
- [ ] Abrir 15-20 pestañas, abrir la cuadrícula varias veces: Brisa no se cierra sola.

## 27. Novedades 1.0.8.1 · issue #43
- [ ] Activar «Borrar datos al salir», iniciar sesión en una web → menú aA → «Salir y borrar datos»: Brisa se cierra y desaparece de Recientes. Al abrirla: sesión cerrada, sin historial ni pestañas.
- [ ] Con el ajuste activado, salir a Inicio más de 5 minutos y volver: todo borrado. Con menos de 5 minutos, sigue igual.
- [ ] Con el ajuste desactivado, el menú aA no muestra «Salir y borrar datos».

## 28. Novedades 1.0.9 · enlaces a apps e issues #44–#51
- [ ] En una búsqueda, tocar un enlace de youtube.com: pregunta «¿Abrir en YouTube?». «Abrir» va a la app; «Seguir en Brisa» lo carga aquí.
- [ ] Un enlace de grupo de WhatsApp (chat.whatsapp.com/…) y uno de Telegram (t.me/…): ofrece abrir la app y, al aceptar, entra al grupo.
- [ ] Escribir youtube.com en la barra: se queda en Brisa, sin preguntar. Navegar dentro de youtube.com: tampoco pregunta.
- [ ] Desactivar «Preguntar antes»: los enlaces van a la app directamente (en privada sigue preguntando). Desactivar «Abrir enlaces en sus apps»: todo se queda en Brisa.
- [ ] Enlace mailto: o tel: en una web: abre el correo o el teléfono.
- [ ] Scroll en una página larga: fluido, la barra se encoge y vuelve igual que antes. Estirar para recargar sigue funcionando.
- [ ] Web de noticias con aviso de cookies: se rechaza solo, igual que antes.
- [ ] «Borrar datos al salir» activado: compartir una página a otra app y quedarse 6 minutos: al volver, todo borrado.
- [ ] Mismo caso, pero volviendo con un enlace desde Telegram: Brisa abre ese enlace (con lo demás borrado).
- [ ] «Entrar con Google» en ventana emergente: se abre y se cierra sola al terminar.

## 29. Novedades 1.0.10 · issues #53 y #54
- [ ] Cambiar de pestaña muchas veces (desde la cuadrícula, deslizando la barra y con atajos de teclado): nunca aparece un menú de enlace que no se haya pedido.
- [ ] Desde otra app (feed de Google, Telegram) abrir varias noticias seguidas en Brisa: no sale ningún menú de enlace de la pestaña anterior.
- [ ] Mantener pulsado un enlace sigue sacando su menú, con el enlace correcto (también en privada).
- [ ] Abrir 4 pestañas, pasar por todas con la cuadrícula, salir de Brisa 10 minutos y volver: las 4 conservan su miniatura.

## 30. Novedades 1.0.11 · Gecko 160 e issues #55, #58, #59
- [ ] Repaso rápido de Gecko 160: instalar encima de la 1.0.10 conservando datos, uBlock activo, YouTube, Maps, un login del homelab, una descarga y modo lectura.
- [ ] Con Brisa en segundo plano mostrando Inicio u otra web, abrir un enlace desde Telegram: no se ve la pantalla anterior; fondo neutro con la barra de progreso y después la página.
- [ ] Cambiar entre pestañas ya cargadas: no hay parpadeos nuevos.
- [ ] Abrir 4 pestañas y pasar por todas, cerrar Brisa desde Recientes y volver a abrirla: las 4 tienen miniatura.
- [ ] Abrir la cuadrícula mientras la pestaña actual recarga: conserva la miniatura anterior, no sale en blanco.
- [ ] Una pestaña privada: con Brisa cerrada y reabierta no queda ninguna miniatura suya (ni la pestaña).
- [ ] Navegar con la VPN, desconectarla y seguir navegando: las páginas cargan en pocos segundos sin forzar la detención (con el DNS cifrado en «Automático» y en «Estricto» con tu DoH propio).
- [ ] Pasar de Wi-Fi a datos y al revés: sigue cargando.
- [ ] Menú aA → «Reiniciar Brisa»: se cierra y vuelve a abrirse sola con las mismas pestañas.

## 31. Novedades 1.1.0 · extensiones (issue #16)
- [ ] Menú aA → Extensiones: uBlock Origin sale como instalada y «Recomendada». Su botón aparece en el menú aA y abre su ventanita (con el interruptor de la web actual).
- [ ] Instalar Bitwarden desde «Recomendadas»: enseña los permisos antes de instalar. Su botón abre su ventanita; iniciar sesión (también con un Vaultwarden del homelab) y rellenar un formulario.
- [ ] Dark Reader: su botón abre su ventanita y el modo oscuro se aplica a las webs.
- [ ] SponsorBlock: en YouTube salta los patrocinios. Su página de bienvenida se abre como pestaña.
- [ ] «Buscar en addons.mozilla.org» → una extensión cualquiera → «Añadir a Firefox»: pregunta los permisos y se instala, marcada como «Experimental».
- [ ] Pegar el enlace de la ficha de una extensión y pulsar «Instalar»: se instala. Un enlace que no es de extensión da un aviso claro.
- [ ] Desactivar una extensión: su botón desaparece del menú aA. Activarla: vuelve.
- [ ] «Permitir en pestañas privadas»: con él apagado, la extensión no actúa en privadas; encendido, sí.
- [ ] «Opciones» abre la página de opciones de la extensión. «Desinstalar» pide confirmación y la quita.
- [ ] Cerrar y abrir Brisa: las extensiones siguen instaladas y sus botones en el menú aA.

## 32. Novedades 1.1.1 · páginas de error (issue #61)
- [ ] Proxmox por https://IP:8006 (certificado autofirmado): sale «Certificado no verificado en tu red» con «Avanzado» abierto, los datos del certificado y la huella. «Aceptar el riesgo y continuar» abre Proxmox.
- [ ] Cerrar Brisa y volver a entrar en Proxmox: ya no pregunta (en la red local se recuerda).
- [ ] Lo mismo en una pestaña privada: pregunta y, al aceptar, entra; al cerrar Brisa se olvida.
- [ ] Una web de internet con certificado caducado (p. ej. expired.badssl.com): «Este sitio no es seguro»; al aceptar entra, y tras cerrar Brisa vuelve a preguntar.
- [ ] Un nombre que no existe (p. ej. noexiste.lan) y un puerto cerrado de un servidor del homelab: página de error clara con «Reintentar», nada de pantalla negra.
- [ ] Modo avión: «Sin conexión»; al volver la red, «Reintentar» carga la página.
- [ ] «Solo HTTPS» activado y una web http://: sale la ventana de siempre y detrás la página explicándolo (no negro).
- [ ] Tema oscuro: la página de error se ve bien.
