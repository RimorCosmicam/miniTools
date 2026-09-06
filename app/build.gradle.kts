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
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        // Signed with the same key as debug so a release build is something you can actually
        // install, rather than an unsigned artifact nobody can put on a phone.
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.18.0")
    // Probe dependency. The blocklist, not the permission, is what stands between an app
    // holding WRITE_SECURE_SETTINGS and the per-display density setter.
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.compose.ui:ui:1.10.5")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.5")
    implementation("androidx.compose.material3:material3:1.4.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.10.5")
    testImplementation("junit:junit:4.13.2")
}
