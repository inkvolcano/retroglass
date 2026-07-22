plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nvanloo.retroglass"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nvanloo.retroglass"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // Two distributions of the same app:
    //  - sideload (default): whole-storage ROM scanning via MANAGE_EXTERNAL_STORAGE.
    //    GitHub Releases / F-Droid.
    //  - play: that permission is restricted to app categories that do not include
    //    emulators, so this flavour removes it and routes "scan storage" to the SAF
    //    folder picker instead. One extra tap (pick the folder once); imports copy
    //    into the library rather than referencing in place.
    flavorDimensions += "distribution"
    productFlavors {
        create("sideload") {
            dimension = "distribution"
            isDefault = true
        }
        create("play") {
            dimension = "distribution"
            // Distinct suffix so both can be installed side by side while testing.
            applicationIdSuffix = ".play"
            buildConfigField("boolean", "ALL_FILES_SCAN", "false")
        }
    }
    defaultConfig {
        buildConfigField("boolean", "ALL_FILES_SCAN", "true")
    }
    buildFeatures {
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Cores are dlopen()ed by absolute path, so they must be extracted to disk.
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
    // Local fork (vendored at 0.14.0) — extends the shader pipeline with custom passes.
    implementation(project(":libretrodroid"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    // 7-Zip / archive extraction for packed ROM sets (xz supplies the LZMA codec).
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.9")

    // The filter composer and the shader stages are plain Kotlin over ShaderConfig, so their
    // contract can be tested on the JVM without a device or Robolectric.
    testImplementation("junit:junit:4.13.2")
}

android {
    testOptions {
        // Console's enum constants call Color.parseColor at class-init, so the stubbed
        // android.jar has to answer rather than throw. Nothing under test depends on the
        // value - only on which Console constant comes back.
        unitTests.isReturnDefaultValues = true
    }
}
