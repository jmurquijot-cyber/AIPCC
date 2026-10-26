
plugins {
    id("com.android.application")
}

val releaseStorePassword = System.getenv("AIPCC_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("AIPCC_KEY_ALIAS")
val releaseKeyPassword = System.getenv("AIPCC_KEY_PASSWORD")

android {
    namespace = "mx.escuelaministerio.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.aipcc.congregacion"
        minSdk = 26
        targetSdk = 36
        versionCode = 38
        versionName = "1.8.7"
    }

    signingConfigs {
        create("release") {
            storeFile = file("aipcc-release.jks")
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
