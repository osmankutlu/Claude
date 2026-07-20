plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.osmankutlu.zh_en_dict"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.osmankutlu.zh_en_dict"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        // Floor at 24 (Android 7): the home-screen widget draws vector
        // drawables in RemoteViews, which only load reliably in the launcher
        // process on API 24+.
        minSdk = maxOf(flutter.minSdkVersion, 24)
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // On-device Chinese OCR for the "screen lens" overlay feature (scans
    // whatever's under the draggable lens in any app, not just this one).
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    // GoogleApiAvailability: diagnostic-only, checks whether Google Play
    // services (which ML Kit's Task/callback machinery relies on even for
    // the on-device, no-download recognizer) is present and healthy.
    implementation("com.google.android.gms:play-services-base:18.4.0")
}

flutter {
    source = "../.."
}
