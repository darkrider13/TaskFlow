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

    buildTypes {
        debug {
            // a debuggable app runs much slower (laggy taps); same signing key, so updates still install over the old app
            isDebuggable = false
        }
        release {
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
