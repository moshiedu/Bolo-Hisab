import java.net.URI
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.asset.pack)
}

/**
 * The Bangla speech model (~90 MB) as a Play Asset Delivery pack. "fast-follow": Play downloads it
 * right after the app is installed and stores it as plain files, which the recogniser needs for
 * hotwords — no second copy in app storage, and a ~90 MB smaller install.
 *
 * Debug builds also carry the model inside the APK (see app/build.gradle.kts), because packs are
 * only delivered to installs from Play (or `bundletool build-apks --local-testing`).
 */
assetPack {
    packName.set("asr_model")
    dynamicDelivery {
        deliveryType.set("fast-follow")
    }
}

/** Downloads the Bangla streaming Zipformer (Vosk-derived, published by sherpa-onnx) on the first build. */
abstract class FetchAsrModel : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Internal abstract val workDir: DirectoryProperty

    /** Where builds before the asset pack put the model; moved instead of downloaded again. */
    @get:Internal abstract val legacyDir: DirectoryProperty
    @get:Inject abstract val fs: FileSystemOperations
    @get:Inject abstract val archives: ArchiveOperations

    @TaskAction
    fun fetch() {
        val out = outputDir.get().asFile
        val legacy = legacyDir.get().asFile
        if (File(legacy, "encoder.onnx").exists() && !File(out, "encoder.onnx").exists()) {
            logger.lifecycle("Moving the downloaded speech model into the asr_model asset pack…")
            out.mkdirs()
            legacy.listFiles()?.forEach { f -> check(f.renameTo(File(out, f.name))) { "Could not move $f" } }
        }
        // Never leave a copy in the app's own assets: it would ship a second 90 MB in release.
        legacy.deleteRecursively()
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
    legacyDir.set(rootProject.layout.projectDirectory.dir("app/src/main/assets/models/asr-bn"))
}

// Every build task of the pack needs the model files first.
val notBuildTasks = setOf("fetchAsrModel", "clean", "help", "tasks", "dependencies", "projects", "properties")
tasks.matching { it.name !in notBuildTasks }.configureEach { dependsOn(fetchAsrModel) }
