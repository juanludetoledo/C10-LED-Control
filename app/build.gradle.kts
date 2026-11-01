plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.juanluis.c10ledcontrol"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.juanluis.c10ledcontrol"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1-diagnostic"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}
