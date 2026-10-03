package com.bolohisab.voice

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Copies the speech model bundled in the APK into app storage. Only a model on disk can use
 * hotwords (the recogniser needs the BPE vocabulary as a real file), so this is what turns on
 * biasing towards the shop's customers and goods — at the cost of a second copy of the model
 * (~90 MB). Opt-in from Settings; removing it falls back to the bundled copy.
 *
 * Later a Play Asset Delivery pack can put the model on disk directly, with no duplicate.
 */
class AsrModelInstaller(private val context: Context) {

    sealed interface Result {
        data object Installed : Result
        data class NotEnoughSpace(val neededBytes: Long, val freeBytes: Long) : Result
        data object NoBundledModel : Result
        data class Failed(val message: String) : Result
    }

    private val target get() = File(context.filesDir, AsrModelLocator.DISK_DIR)

    fun isInstalled(): Boolean = AsrModelLocator(context).locateOnDisk() != null

    /** Size of the bundled model files, or 0 when the APK carries none. */
    fun bundledBytes(): Long = bundledFiles().sumOf { name ->
        runCatching { context.assets.openFd("${AsrModelLocator.ASSET_DIR}/$name").use { it.length } }
            .getOrElse { runCatching { context.assets.open("${AsrModelLocator.ASSET_DIR}/$name").use { s -> s.skip(Long.MAX_VALUE) } }.getOrDefault(0L) }
    }

    suspend fun install(): Result = withContext(Dispatchers.IO) {
        val files = bundledFiles()
        if (files.isEmpty()) return@withContext Result.NoBundledModel
        val needed = bundledBytes() + SPARE_BYTES
        val free = StatFs(context.filesDir.path).availableBytes
        if (free < needed) return@withContext Result.NotEnoughSpace(needed, free)

        // Copy into a temp folder and rename at the end, so a half-copied model is never picked up.
        val temp = File(target.parentFile, target.name + ".part")
        try {
            temp.deleteRecursively()
            if (!temp.mkdirs()) throw IOException("Could not create $temp")
            for (name in files) {
                context.assets.open("${AsrModelLocator.ASSET_DIR}/$name").use { input ->
                    File(temp, name).outputStream().use { input.copyTo(it, 1 shl 16) }
                }
            }
            target.deleteRecursively()
            if (!temp.renameTo(target)) throw IOException("Could not move $temp to $target")
            Result.Installed
        } catch (e: IOException) {
            temp.deleteRecursively()
            Result.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    suspend fun remove() = withContext(Dispatchers.IO) { target.deleteRecursively(); Unit }

    private fun bundledFiles(): List<String> =
        runCatching { context.assets.list(AsrModelLocator.ASSET_DIR)?.toList() }.getOrNull().orEmpty()

    private companion object {
        /** Room left over after the copy, so the ledger database never runs out of space. */
        const val SPARE_BYTES = 200L * 1024 * 1024
    }
}
