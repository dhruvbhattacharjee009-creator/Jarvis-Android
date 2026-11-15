plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Stable signing configuration for GitHub Actions
val ciKeystoreFile = project.file("ci-debug.keystore")
val ciSigningPassword =
    System.getenv("MINIMAL_DEBUG_STORE_PASSWORD") ?: ""
val ciBuildNumber =
    System.getenv("GITHUB_RUN_NUMBER") ?: "local"

android {
    namespace = "com.jarvis.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jarvis.android"
        minSdk = 24
        targetSdk = 35
        versionCode = ciBuildNumber.toIntOrNull() ?: 1
        versionName = "0.1.$ciBuildNumber"
    }

    signingConfigs {
        create("ciDebug") {
            storeFile = ciKeystoreFile
            storePassword = ciSigningPassword
            keyAlias = "minimaldebug"
            keyPassword = ciSigningPassword
        }
    }

    buildTypes {
        getByName("debug") {
            if (ciKeystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("ciDebug")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlin {
        compilerOptions {
            jvmTarget =
                org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}



