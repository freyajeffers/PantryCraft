package com.jeffers.pantrycraft.feature.camera.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import kotlinx.coroutines.suspendCancellableCoroutine
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
    private val scratchDirectory = context.applicationContext.cacheDir.resolve("vision_scratch")

    suspend fun capture(): SnapshotFrame =
        suspendCancellableCoroutine { continuation ->
            scratchDirectory.mkdirs()
            val file = File.createTempFile("snapshot-", ".jpg", scratchDirectory)
            val output = ImageCapture.OutputFileOptions.Builder(file).build()
            imageCapture.takePicture(
                output,
                contextExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        try {
                            val bytes = compressForVision(file)
                            continuation.resume(SnapshotFrame(bytes))
                        } catch (error: Exception) {
                            continuation.resumeWithException(error)
                        } finally {
                            file.delete()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        continuation.resumeWithException(exception)
                    }
                },
            )
            continuation.invokeOnCancellation { file.delete() }
        }

    private fun compressForVision(file: File): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val source =
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
                    inPreferredConfig = Bitmap.Config.RGB_565
                },
            )
                ?: error("Camera returned an unreadable image")
        val scaled = scale(source)
        return try {
            ByteArrayOutputStream().use { output ->
                check(scaled.compress(Bitmap.CompressFormat.WEBP, 85, output)) {
                    "Could not encode snapshot"
                }
                output.toByteArray()
            }
        } finally {
            if (scaled !== source) scaled.recycle()
            source.recycle()
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
        private val contextExecutor =
            java.util.concurrent.Executors
                .newSingleThreadExecutor()
    }
}
