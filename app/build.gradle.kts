plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.exercisetracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.exercisetracker"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }
    }

    // Release signing details come from ~/.gradle/gradle.properties, outside the repo, so no keys or
    // passwords are ever committed. Without them, release builds are left unsigned.
    val releaseStoreFile = providers.gradleProperty("RELEASE_STORE_FILE").orNull
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // MediaPipe
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    implementation("com.google.mediapipe:tasks-core:0.10.14")

    // CameraX
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Standard UI
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Background work (streak reminders)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Import the Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:34.10.0"))

    // Firebase SDKs
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // App Check: Play Integrity in release builds, the debug provider in debug builds (see AppCheckSetup.kt)
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    // Unit tests (src/test) run on the JVM, where Android's org.json is only a stub, so the real
    // library is added for the routine tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
