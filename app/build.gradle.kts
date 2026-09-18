plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "stanissay.wear.board"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "stanissay.wear.board"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        base.archivesName = "WearBoard-" + defaultConfig.versionName
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.wear.compose:compose-material:1.7.0-rc01")
    implementation("androidx.wear.compose:compose-foundation:1.7.0-rc01")
    implementation("androidx.wear.compose:compose-material:1.6.2")
    implementation("androidx.wear.compose:compose-foundation:1.6.2")
    implementation("androidx.wear:wear-tooling-preview:1.0.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.compose.runtime:runtime:1.12.1")
    implementation("androidx.wear:wear:1.4.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.wear.compose:compose-material3:1.6.2")
}