import java.io.FileInputStream
import java.util.Properties

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
        // Play rejects an upload whose versionCode has not moved, so this must be bumped
        // for every release. There are no git tags yet either, which means a shipped APK
        // cannot be traced back to the commit and core versions that produced it.
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    packaging {
        jniLibs {
            // Cores are dlopen()ed by absolute path, so they must be extracted to disk.
            useLegacyPackaging = true
        }
    }

    // Release signing comes from keystore.properties, which is git-ignored and never
    // committed. Without it the release build stays unsigned rather than silently falling
    // back to the debug certificate - an AOSP-signed upload is rejected by Play, and it is
    // better to fail at build time than to discover that at submission.
    //
    // To set up:  cp keystore.properties.example keystore.properties  and fill it in.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) FileInputStream(f).use { load(it) }
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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
