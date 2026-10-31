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

## Compilar
Igual que Cloudreve: repositorio privado en GitHub, sube el proyecto y descarga el APK de **Actions → Build APK**. Con `git tag v0.1.0 && git push --tags` se publica en Releases.
Solo genera APK para arm64 (Pixel). El APK pesa ~70 MB por el motor Gecko.

## Siguiente (0.2)
Extensiones (uBlock Origin), modo lectura, historial y sugerencias, descargas, tamaño de texto, búsqueda en la página.
