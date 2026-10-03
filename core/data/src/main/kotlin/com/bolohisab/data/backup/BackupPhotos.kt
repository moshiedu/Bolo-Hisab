package com.bolohisab.data.backup

import java.util.Base64

/**
 * Customer photos travel inside the backup as Base64 JPEG. The photos are already small (≤512 px,
 * see the app's CustomerPhotoStore), so a few hundred customers add only a few MB.
 *
 * A backup file comes from outside the app, so a photo is only accepted when it is a real JPEG
 * under [MAX_BYTES]; anything else is dropped and the customer keeps no photo.
 */
object BackupPhotos {
    /** Folder under filesDir; shared with the app's CustomerPhotoStore. */
    const val DIR = "customer_photos"

    const val MAX_BYTES = 2 * 1024 * 1024

    fun encode(jpeg: ByteArray): String = Base64.getEncoder().encodeToString(jpeg)

    /** The JPEG bytes, or null when the text is not a plausible JPEG photo. */
    fun decode(text: String?): ByteArray? {
        if (text.isNullOrEmpty() || text.length > MAX_BYTES * 4 / 3 + 4) return null
        val bytes = runCatching { Base64.getDecoder().decode(text) }.getOrNull() ?: return null
        val isJpeg = bytes.size in 4..MAX_BYTES &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        return if (isJpeg) bytes else null
    }
}
