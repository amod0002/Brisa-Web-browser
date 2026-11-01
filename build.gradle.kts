plugins {
    id("com.android.application") version "9.1.0" apply false
    // AGP 9 compila Kotlin por sí mismo; solo hace falta el compilador de Compose (misma versión que Kotlin)
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}
