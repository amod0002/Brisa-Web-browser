plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "es.homelab.brisa"
    compileSdk = 37
    defaultConfig {
        applicationId = "es.homelab.brisa"
        minSdk = 26
        targetSdk = 35
        versionCode = 109
        versionName = "1.0.8.1"
        // Solo arm64: el Pixel no necesita más y el APK pesa la mitad
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    // Firma de publicación: la clave NO está en el repositorio. En GitHub llega desde los secretos
    // KEYSTORE_BASE64 y KEYSTORE_PASSWORD (ver .github/workflows/build.yml).
    signingConfigs {
        create("release") {
            val ks = System.getenv("BRISA_KEYSTORE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("BRISA_KEYSTORE_PASSWORD")
                keyAlias = "brisa"
                keyPassword = System.getenv("BRISA_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            if (System.getenv("BRISA_KEYSTORE") != null) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    // Motor Gecko (Firefox). Versión exacta: la comprueba cada día .github/workflows/gecko.yml
    implementation("org.mozilla.geckoview:geckoview:159.0.20261102144711")
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.19.1")
    testImplementation("junit:junit:4.13.2")
}
