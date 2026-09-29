package com.carthing.data.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max

/** Stores attachment photos as downscaled JPEGs in app-private storage. */
class PhotoStore(private val context: Context) {
    val dir: File get() = File(context.filesDir, "attachments").apply { mkdirs() }
    private val cameraDir: File get() = File(context.cacheDir, "camera").apply { mkdirs() }

    fun file(fileName: String) = File(dir, fileName)

    /** A fresh URI the camera app can write a full-size photo to, before it's imported. */
    fun newCameraUri(): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", File(cameraDir, "capture-${UUID.randomUUID()}.jpg"))

    /**
     * Copies the image at [source] into the store: rotated upright, at most [MAX_SIDE] px on the
     * long side, JPEG. Returns the stored file name. Temporary camera files are removed afterwards.
     */
    fun import(source: Uri): String {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode always returns null, so check the stream itself, not the result.
        (resolver.openInputStream(source) ?: throw IOException("Can't open image")).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) throw IOException("Not an image")

        // Decode at the smallest power-of-two reduction that stays above MAX_SIDE, then scale exactly.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("Can't decode image")
        val rotation = resolver.openInputStream(source)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val upright = transform(decoded, rotation)

        val name = "${UUID.randomUUID()}.jpg"
        file(name).outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (upright !== decoded) upright.recycle()
        decoded.recycle()
        if (source.authority == "${context.packageName}.files") cameraDir.listFiles()?.forEach { it.delete() }
        return name
    }

    fun delete(fileName: String) { file(fileName).delete() }

    /** Writes raw bytes under [fileName], used when restoring a backup. */
    fun write(fileName: String, bytes: ByteArray) = file(fileName).writeBytes(bytes)

    /**
     * Deletes stored photos not in [referenced]. Files younger than [minAgeMillis] are kept, as they
     * may belong to a form that hasn't been saved yet.
     */
    fun sweep(referenced: Set<String>, nowMillis: Long = System.currentTimeMillis(), minAgeMillis: Long = ORPHAN_AGE_MILLIS): Int {
        var removed = 0
        dir.listFiles()?.forEach { f ->
            if (f.name !in referenced && nowMillis - f.lastModified() >= minAgeMillis && f.delete()) removed++
        }
        return removed
    }

    private fun transform(bitmap: Bitmap, rotation: Int): Bitmap {
        val scale = MAX_SIDE.toFloat() / max(bitmap.width, bitmap.height)
        if (rotation == 0 && scale >= 1f) return bitmap
        val m = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    companion object {
        const val MAX_SIDE = 2000
        const val JPEG_QUALITY = 85
        const val ORPHAN_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}
