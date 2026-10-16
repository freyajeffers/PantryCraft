package com.jeffers.pantrycraft.feature.camera.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Arrays
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SnapshotFrame(
    val webpBytes: ByteArray,
) {
    fun close() {
        Arrays.fill(webpBytes, 0)
    }
}

class SnapshotCaptureService(
    context: Context,
    private val imageCapture: ImageCapture,
) {
    private val appContext = context.applicationContext
    private val scratchDirectory = appContext.cacheDir.resolve("vision_scratch")

    suspend fun capture(): SnapshotFrame =
        withContext(Dispatchers.Default) {
            val file = captureToFile()
            try {
                val bytes = compressForVision(file)
                SnapshotFrame(bytes)
            } finally {
                file.delete()
            }
        }

    private suspend fun captureToFile(): File =
        suspendCancellableCoroutine { continuation ->
            scratchDirectory.mkdirs()
            val file = File.createTempFile("snapshot-", ".jpg", scratchDirectory)
            val output = ImageCapture.OutputFileOptions.Builder(file).build()
            imageCapture.takePicture(
                output,
                ContextCompat.getMainExecutor(appContext),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (continuation.isActive) continuation.resume(file) else file.delete()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                },
            )
            continuation.invokeOnCancellation { file.delete() }
        }

    private fun compressForVision(file: File): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        val source =
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                },
            ) ?: error("Camera returned an unreadable image")

        val oriented = applyExifOrientation(file.absolutePath, source)
        val scaled = scale(oriented)
        return try {
            ByteArrayOutputStream().use { output ->
                check(scaled.compress(Bitmap.CompressFormat.WEBP, 85, output)) {
                    "Could not encode snapshot"
                }
                output.toByteArray()
            }
        } finally {
            if (scaled !== oriented && scaled !== source) scaled.recycle()
            if (oriented !== source) oriented.recycle()
            source.recycle()
        }
    }

    private fun applyExifOrientation(
        filePath: String,
        source: Bitmap,
    ): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            val orientation =
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return source
            }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } catch (_: Exception) {
            source
        }
    }

    private fun scale(source: Bitmap): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= MAX_DIMENSION) return source
        val scale = MAX_DIMENSION.toFloat() / largest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt(),
            (source.height * scale).toInt(),
            true,
        )
    }

    private fun calculateSampleSize(
        width: Int,
        height: Int,
    ): Int {
        var sample = 1
        while (maxOf(width / sample, height / sample) > MAX_DECODE_DIMENSION) sample *= 2
        return sample
    }

    companion object {
        private const val MAX_DIMENSION = 1536
        private const val MAX_DECODE_DIMENSION = 2048
    }
}
