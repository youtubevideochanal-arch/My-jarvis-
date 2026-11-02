plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.myagent.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.myagent.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "5.0"
    }

    signingConfigs {
        getByName("debug") {
            // Optional private key (GitHub Secrets se); na ho to repo wali debug key.
            val pk = file("private.keystore")
            if (pk.exists()) {
                storeFile = pk
                storePassword = System.getenv("KS_PASS") ?: "android"
                keyAlias = System.getenv("KS_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("KS_PASS") ?: "android"
            } else {
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    // Install-problem pakadne ke liye: gradle assembleDebug -Pmf=lite  ya  -Pmf=min  (kam components wali manifest)
    sourceSets {
        getByName("main") {
            val mf = (project.findProperty("mf") as String?) ?: "full"
            if (mf != "full") manifest.srcFile("manifests/AndroidManifest.$mf.xml")
        }
    }

    buildTypes {
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}
