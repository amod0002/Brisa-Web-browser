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

## 21. Android
- [ ] Gesto «atrás»: cierra menú/teclado/cuadrícula y luego va a la página anterior.
- [ ] Un enlace desde otra app (WhatsApp, correo) se abre en una pestaña nueva.
- [ ] Compartir envía la URL.
- [ ] Tema claro/oscuro y colores de Material You.
- [ ] Girar el móvil no recarga la página.

## 22. Velocidad (comparar con Chrome)
- [ ] Abrir elpais.com, marca.com y youtube.com: sensación de carga igual o mejor.
- [ ] Scroll fluido en páginas largas, sin tirones.
