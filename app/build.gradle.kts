plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.icangotoanywhere.clearrdp"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.icangotoanywhere.clearrdp"
        minSdk = 29
        targetSdk = 35
        versionCode = 4
        versionName = "1.3"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Release signing always uses the fixed key committed in keystore/clearrdp-release.p12,
    // so every build — local or GitHub Actions — carries the same signature and installs
    // over the previous version. Every release so far (1.0–1.3) was signed with this key:
    // certificate SHA-256 33:BF:16:75:…:2B:08:30. Do not replace it, or users would have to
    // uninstall before updating.
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/clearrdp-release.p12")
            storeType = "pkcs12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            // same key as release, so a debug build can be installed over a release and back
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            // must stay off: the FreeRDP native library calls Java methods by name
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
