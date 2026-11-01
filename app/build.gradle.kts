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
        versionCode = 15
        versionName = "0.9.4"
        // Solo arm64: el Pixel no necesita más y el APK pesa la mitad
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    signingConfigs {
        getByName("debug") { storeFile = rootProject.file("debug.keystore") }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
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
    // Motor Gecko (Firefox). Versión exacta: la comprueba cada mes .github/workflows/gecko.yml
    implementation("org.mozilla.geckoview:geckoview:159.0.20261022150525")
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.19.1")
    testImplementation("junit:junit:4.13.2")
}
