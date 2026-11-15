plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.habit.tracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.habit.tracker"
        minSdk = 24
        targetSdk = 34
        // time based, so every new build is always newer than any build installed before (no "downgrade" conflict)
        versionCode = (System.currentTimeMillis() / 60000L).toInt()
        versionName = "2." + ((project.findProperty("vCode") as String?) ?: "1")
    }

    // One fixed signing key for every build, so a new APK always installs over the old one (no "package conflict").
    // If you add the GitHub secrets KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD they are used,
    // otherwise the debug.keystore stored in the project is used (same key as before).
    signingConfigs {
        create("fixed") {
            fun env(n: String) = System.getenv(n)?.takeIf { it.isNotBlank() }
            storeFile = env("KEYSTORE_FILE")?.let { file(it) } ?: rootProject.file("debug.keystore")
            storePassword = env("KEYSTORE_PASSWORD") ?: "android"
            keyAlias = env("KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = env("KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
            // a debuggable app runs much slower (laggy taps); same signing key, so updates still install over the old app
            isDebuggable = false
        }
        release {
            signingConfig = signingConfigs.getByName("fixed")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
}
