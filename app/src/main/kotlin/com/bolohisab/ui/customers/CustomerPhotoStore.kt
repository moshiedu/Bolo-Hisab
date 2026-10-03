package com.bolohisab.ui.customers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.bolohisab.data.backup.BackupPhotos
import java.io.File
import java.util.UUID

/**
 * Copies a picked/captured customer photo into the app's private storage, downsampled and
 * EXIF-rotated, so it survives even if the original (a gallery image, a temp camera file) is
 * later deleted or its URI permission expires. Stored under `filesDir`, not cache — a photo
 * is user data, not a regenerable artefact like the cached PDF reports.
 */
object CustomerPhotoStore {
    private const val MAX_DIMENSION = 512
    private const val JPEG_QUALITY = 85
    private const val DIR = BackupPhotos.DIR

    fun save(context: Context, sourceUri: Uri): String {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val sampled = decodeSampled(context, sourceUri)
        val oriented = applyExifRotation(context, sourceUri, sampled)
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (oriented !== sampled) sampled.recycle()
        oriented.recycle()
        return file.absolutePath
    }

    fun delete(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }

    private fun decodeSampled(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > MAX_DIMENSION || bounds.outHeight / sample > MAX_DIMENSION) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, opts) ?: error("Could not decode image")
        }
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
