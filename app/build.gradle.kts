plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.rimor.minitools"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rimor.minitools"
        // The gesture zones name the display they sit on, and the cover display is only
        // addressable that way from 30 onwards.
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Two builds from one source. Rotation is the only tool with a cost — it breaks the
    // switcher's layout until repaired — so there is a build without it, where the code is not
    // merely switched off but absent, and nothing can turn it on by accident.
    flavorDimensions += "tools"
    productFlavors {
        create("full") {
            dimension = "tools"
            buildConfigField("boolean", "HAS_ROTATION", "true")
        }
        create("norotate") {
            dimension = "tools"
            buildConfigField("boolean", "HAS_ROTATION", "false")
            versionNameSuffix = "-norotate"
        }
    }

    // The release key never lives in the repository. CI writes it out of a secret; a machine
    // without that secret falls back to the debug key, so cloning and building still works.
    val releaseStore = System.getenv("MINITOOLS_KEYSTORE")?.let(::file)?.takeIf { it.exists() }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = System.getenv("MINITOOLS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MINITOOLS_KEY_ALIAS")
                keyPassword = System.getenv("MINITOOLS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed either way, so a release build is always something you can actually install
            // rather than an unsigned artifact nobody can put on a phone.
            signingConfig = signingConfigs.getByName(if (releaseStore != null) "release" else "debug")
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.18.0")
    // The per-display density setter is hidden API. The permission is grantable; the blocklist
    // is the part that needs lifting, and this is maintained against exactly that.
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.compose.ui:ui:1.10.5")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.5")
    implementation("androidx.compose.material3:material3:1.4.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.10.5")
    testImplementation("junit:junit:4.13.2")
}
