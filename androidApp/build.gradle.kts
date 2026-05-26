import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinMultiplatform)
    // Firebase plugins are applied conditionally below; google-services.json
    // is not committed (see .gitignore), so they must not run without it.
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.firebaseCrashlytics) apply false
}

// The Google Services / Crashlytics plugins fail at configuration time when
// google-services.json is absent (e.g. CI and fresh open-source checkouts).
// Apply them only when a developer has supplied their own Firebase config.
if (project.file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) load(localPropsFile.inputStream())
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(project(":shared"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)

            implementation(libs.activity.compose)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)

            implementation(libs.koin.android)
            implementation(libs.koin.compose)

            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.porcupine.android)
            implementation(libs.onnxruntime.android)
            implementation(libs.mediapipe.genai)
        }
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
}

android {
    namespace = "com.androidclaw.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.androidclaw.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // API key should only be set in debug builds, loaded from local.properties
        // Release builds should NOT contain API keys - users supply at runtime
        buildConfigField("String", "DEFAULT_API_KEY", "\"\"")
    }

    signingConfigs {
        create("release") {
            val keystorePath = localProperties.getProperty("release.keystore.path", "")
            if (keystorePath.isNotEmpty()) {
                storeFile = file(keystorePath)
                storePassword = localProperties.getProperty("release.keystore.password", "")
                keyAlias = localProperties.getProperty("release.key.alias", "")
                keyPassword = localProperties.getProperty("release.key.password", "")
            } else {
                // Fall back to debug keystore for local release builds
                storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            // Only load API key for debug builds from local.properties
            // Keep it out of release APKs to prevent accidental leaks
            buildConfigField("String", "DEFAULT_API_KEY", "\"${localProperties.getProperty("anthropic.api.key", "")}\"")
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            // Release APK does NOT contain API key - loaded at runtime
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // NDK build for whisper.cpp -- enabled when external/whisper.cpp is present
    // externalNativeBuild {
    //     cmake {
    //         path = file("src/main/cpp/CMakeLists.txt")
    //     }
    // }
    //
    // ndkVersion = "27.0.12077973"

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // 16 KB page size compliance for Google Play (required Nov 2025+)
        jniLibs {
            useLegacyPackaging = false
        }
    }
}
