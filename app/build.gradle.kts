plugins {
    id("com.android.application")
}

android {
    namespace = "com.rdpsplit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rdpsplit"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Release signing: set CLEARRDP_KEYSTORE (path), CLEARRDP_KEYSTORE_PASSWORD,
    // CLEARRDP_KEY_ALIAS and CLEARRDP_KEY_PASSWORD; otherwise the debug key is used.
    val ksPath = System.getenv("CLEARRDP_KEYSTORE")
    signingConfigs {
        if (!ksPath.isNullOrEmpty() && file(ksPath).exists()) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("CLEARRDP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CLEARRDP_KEY_ALIAS")
                keyPassword = System.getenv("CLEARRDP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // must stay off: the FreeRDP native library calls Java methods by name
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
