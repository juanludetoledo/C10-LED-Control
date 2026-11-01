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
        versionCode = 1
        versionName = "1.0"
    }
}
