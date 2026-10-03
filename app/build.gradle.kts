import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.bolohisab"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bolohisab.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            // Budget phones in Bangladesh still include 32-bit ARM; x86_64 is for the emulator.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    androidResources {
        // Keep model files uncompressed so they are read straight from the APK.
        noCompress += listOf("onnx", "txt", "vocab")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core:nlu"))
    implementation(project(":core:data"))
    implementation(project(":core:voice"))
    implementation(files("libs/sherpa-onnx.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}

/**
 * Downloads the Bangla streaming Zipformer (~90 MB, Vosk-derived, published by sherpa-onnx)
 * into assets on the first build. Later this moves to a Play Asset Delivery pack.
 */
abstract class FetchAsrModel : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Internal abstract val workDir: DirectoryProperty
    @get:Inject abstract val fs: FileSystemOperations
    @get:Inject abstract val archives: ArchiveOperations

    @TaskAction
    fun fetch() {
        val out = outputDir.get().asFile
        if (File(out, "encoder.onnx").exists()) return
        val archive = workDir.file("asr-bn.tar.bz2").get().asFile
        archive.parentFile.mkdirs()
        if (!archive.exists()) {
            logger.lifecycle("Downloading Bangla ASR model (one time, ~90 MB)…")
            val part = File(archive.path + ".part")
            URI(url.get()).toURL().openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
            check(part.renameTo(archive)) { "Could not save $archive" }
        }
        fs.copy {
            from(archives.tarTree(archives.bzip2(archive)))
            include("*/encoder.onnx", "*/decoder.onnx", "*/joiner.onnx", "*/tokens.txt", "*/*.vocab")
            eachFile { path = name }
            includeEmptyDirs = false
            into(out)
        }
    }
}

val fetchAsrModel = tasks.register<FetchAsrModel>("fetchAsrModel") {
    group = "setup"
    url.set(
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
            "sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09.tar.bz2",
    )
    outputDir.set(layout.projectDirectory.dir("src/main/assets/models/asr-bn"))
    workDir.set(layout.buildDirectory.dir("asr-download"))
}

tasks.named("preBuild") { dependsOn(fetchAsrModel) }
