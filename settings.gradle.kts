pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BoloHisab"

include(":app")
include(":core:data")
include(":core:nlu")
include(":core:voice")
include(":asr_model")

// sherpa-onnx (offline speech engine) ships as an AAR on GitHub, not on Maven Central.
// Fetch it once into app/libs so a fresh clone builds with no manual steps.
val sherpaOnnxVersion = "1.12.33"
val sherpaAar = File(settingsDir, "app/libs/sherpa-onnx.aar")
if (!sherpaAar.exists()) {
    val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaOnnxVersion/sherpa-onnx-$sherpaOnnxVersion.aar"
    println("Downloading sherpa-onnx $sherpaOnnxVersion AAR (one time)…")
    sherpaAar.parentFile.mkdirs()
    val part = File(sherpaAar.path + ".part")
    java.net.URI(url).toURL().openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
    check(part.renameTo(sherpaAar)) { "Could not save $sherpaAar" }
}
