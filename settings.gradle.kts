pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "RetroGlass"
include(":app")

// Forked LibretroDroid (vendored clone of Swordfish90/LibretroDroid @ 0.14.0) built from
// source so the shader pipeline can be extended with custom multi-pass upscalers.
include(":libretrodroid")
project(":libretrodroid").projectDir = file("libretrodroid/libretrodroid")
