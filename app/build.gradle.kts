plugins {
    id("com.android.application")
}

android {
    namespace = "mx.escuelaministerio.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "mx.escuelaministerio.app.estable"
        minSdk = 26
        targetSdk = 36
        versionCode = 35
        versionName = "1.8.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
