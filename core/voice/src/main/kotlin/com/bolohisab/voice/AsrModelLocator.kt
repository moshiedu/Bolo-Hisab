package com.bolohisab.voice

import android.content.Context
import java.io.File

/** Where the Bangla model files are, and whether they can be loaded from assets or from disk. */
data class AsrModelFiles(
    val fromAssets: Boolean,
    val encoder: String,
    val decoder: String,
    val joiner: String,
    val tokens: String,
    /** Needed to encode hotwords into BPE pieces; absent means no hotword biasing. */
    val bpeVocab: String?,
)

/**
 * Finds the streaming Zipformer model. A model downloaded into app storage
 * (later: a Play Asset Delivery pack) wins over the copy bundled in assets.
 *
 * Expected files: encoder*.onnx, decoder*.onnx, joiner*.onnx, tokens.txt and
 * optionally *.vocab. int8 variants are preferred when both exist.
 */
class AsrModelLocator(private val context: Context) {

    fun locate(): AsrModelFiles? = fromDisk() ?: fromAssets()

    private fun fromDisk(): AsrModelFiles? {
        val dir = File(context.filesDir, DISK_DIR)
        val names = dir.list()?.toList() ?: return null
        return pick(names)?.let { m ->
            m.copy(
                fromAssets = false,
                encoder = File(dir, m.encoder).absolutePath,
                decoder = File(dir, m.decoder).absolutePath,
                joiner = File(dir, m.joiner).absolutePath,
                tokens = File(dir, m.tokens).absolutePath,
                bpeVocab = m.bpeVocab?.let { File(dir, it).absolutePath },
            )
        }
    }

    private fun fromAssets(): AsrModelFiles? {
        val names = runCatching { context.assets.list(ASSET_DIR)?.toList() }.getOrNull() ?: return null
        return pick(names)?.let { m ->
            m.copy(
                fromAssets = true,
                encoder = "$ASSET_DIR/${m.encoder}",
                decoder = "$ASSET_DIR/${m.decoder}",
                joiner = "$ASSET_DIR/${m.joiner}",
                tokens = "$ASSET_DIR/${m.tokens}",
                bpeVocab = m.bpeVocab?.let { "$ASSET_DIR/$it" },
            )
        }
    }

    private fun pick(names: List<String>): AsrModelFiles? {
        fun onnx(part: String) = names.filter { it.contains(part) && it.endsWith(".onnx") }
            .sortedByDescending { it.contains("int8") }
            .firstOrNull()
        val encoder = onnx("encoder") ?: return null
        val decoder = onnx("decoder") ?: return null
        val joiner = onnx("joiner") ?: return null
        val tokens = names.firstOrNull { it == "tokens.txt" } ?: return null
        val vocab = names.firstOrNull { it.endsWith(".vocab") }
        return AsrModelFiles(false, encoder, decoder, joiner, tokens, vocab)
    }

    companion object {
        const val ASSET_DIR = "models/asr-bn"
        const val DISK_DIR = "models/asr-bn"
    }
}
