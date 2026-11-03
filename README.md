<div align="center">

<img src="docs/logo.svg" width="96" alt="Logo de Brisa">

# Brisa

**Un navegador para Android con el motor de Firefox y la sencillez de Safari.**
Ligero, privado por defecto y pensado también para el homelab.

[![Compilación](https://github.com/amod0002/Brisa-Web-browser/actions/workflows/build.yml/badge.svg?branch=main)](../../actions/workflows/build.yml)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Motor Gecko](https://img.shields.io/badge/motor-Gecko-FF7139?logo=firefoxbrowser&logoColor=white)
![Kotlin · Compose](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)

[**Descargar**](../../releases/latest) · [Novedades](CHANGELOG.md) · [Lista de pruebas](PRUEBAS.md)

</div>

---

## Por qué Brisa

La mayoría de navegadores de Android usan Chromium. Brisa usa **Gecko** (GeckoView, el motor de Firefox) con una interfaz inspirada en **Safari**: una barra flotante abajo, gestos para cambiar de pestaña y nada que estorbe a la página. Bloquea anuncios y rastreadores desde el primer arranque, no manda nada a la nube y entiende las direcciones de tu red local como lo que son.

## Lo que incluye

### Interfaz
- **Barra inferior de cristal** que se encoge al bajar por la página. Deslízala a los lados para cambiar de pestaña.
- **Pestañas** en cuadrícula con miniaturas, **grupos** y pestañas **privadas** bloqueadas con huella o PIN.
- **Inicio** con favoritos y webs visitadas con frecuencia.
- **Modo lectura**, buscar en la página, tamaño del texto y vídeo a pantalla completa.
- **Descargas** con animación, anillo de progreso y notificación. No se cortan al salir de la app.
- **Tablet**: barra arriba, webs de escritorio por defecto y atajos de teclado (Ctrl+T, Ctrl+L, Ctrl+Tab…).
- Tema claro y oscuro con Material You.

### Privacidad
- **uBlock Origin** instalado desde el primer arranque.
- Protección **estricta** contra rastreadores y contra la **huella digital**.
- **Solo HTTPS** (siempre, solo en privadas o desactivado).
- **DNS cifrado** (DoH) con Quad9, Mullvad, Cloudflare o tu propio servidor.
- **Enlaces limpios** (sin `utm_*`, `fbclid`, `gclid`…) y **avisos de cookies rechazados** solos.
- **Global Privacy Control**, protección de IP en WebRTC y permisos por web.
- **Informe de privacidad** con los rastreadores bloqueados. Los datos no salen del móvil.

### Contraseñas
- Se guardan **cifradas** con una clave AES-256 del chip de seguridad de Android, sin nube.
- Generador de contraseñas seguras en los formularios de registro.
- Compatible con **Bitwarden, 1Password o KeePassDX** mediante el autorrelleno de Android.

### Para el homelab
- La barra reconoce `192.168.1.50:8006`, `proxmox:8006`, `nas/admin`, IPv6 y los dominios `.lan`, `.local` y `.home.arpa` como direcciones, no como búsquedas.
- Las direcciones de la red local **no se envían** al buscador ni pasan por el DNS cifrado.
- Buscador propio (SearXNG o cualquiera con `%s`) y DNS propio (AdGuard Home, Pi-hole…).
- Contraseñas emparejadas también por puerto, para tener varios servicios en la misma IP.

## Instalar

1. Descarga el APK de la [última versión](../../releases/latest).
2. Ábrelo en el móvil y permite la instalación desde esa fuente si Android lo pide.
3. Las actualizaciones se instalan encima y conservan tus datos.

> Requiere **Android 8.0 o superior** en un móvil **arm64** (todos los actuales). El APK pesa unos 70 MB porque incluye el motor Gecko completo.

## Novedades

**1.0.3**: revisión de la comunidad. Contraseñas más robustas y emparejadas por origen, privadas sin capturas, protección contra *DNS rebinding*, barra que entiende más direcciones locales y descargas en segundo plano.

El historial completo está en [CHANGELOG.md](CHANGELOG.md).

## Compilar

Hace falta **JDK 17** y **Gradle 9.8.2**.

```sh
gradle :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Para un APK firmado, define `BRISA_KEYSTORE` (ruta al `.jks`) y `BRISA_KEYSTORE_PASSWORD`. Sin ellas se compila y se pasan las pruebas, pero no sale APK. En GitHub Actions la firma llega desde los secretos `KEYSTORE_BASE64` y `KEYSTORE_PASSWORD`.

### Flujo de trabajo

```
dev  ──►  Actions (pruebas, lint y APK)  ──►  PRUEBAS.md en el móvil  ──►  main  ──►  etiqueta vX.Y.Z  ──►  Release
```

- Todo lo nuevo entra por **`dev`**. Actions deja el APK como artefacto de la ejecución.
- Al crear una Release con etiqueta `vX.Y.Z` desde `main`, Actions compila y adjunta el APK.
- Cada día, el workflow **Comprobar Gecko** abre un aviso si Mozilla publica una GeckoView nueva.
- Si una compilación falla, el registro completo queda en la rama `ci-log`.

### Estructura

```
app/src/main/java/es/homelab/brisa/   Código de la app (una pantalla o tema por archivo)
app/src/main/assets/extensions/brisa/ Extensión interna: modo lectura, cookies, contraseñas
app/src/test/                         Pruebas unitarias
.github/workflows/                    Compilación y aviso de Gecko
```

## Hoja de ruta

- [x] **1.0**: primera versión estable.
- [ ] **Sincronización** de favoritos, historial y pestañas con un servicio propio en el homelab (aparcado, sin fecha).

## Créditos

- [GeckoView](https://mozilla.github.io/geckoview/) de Mozilla (MPL 2.0).
- [Readability](https://github.com/mozilla/readability) de Mozilla para el modo lectura (Apache 2.0).
- [uBlock Origin](https://github.com/gorhill/uBlock) de Raymond Hill (GPLv3), descargado de addons.mozilla.org.
- Gracias a quienes han abierto issues y revisado el código.
