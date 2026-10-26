package com.jeffers.pantrycraft.feature.camera.vision

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageCapture
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class SnapshotCaptureServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val imageCapture: ImageCapture = ImageCapture.Builder().build()

    @Test
    fun calculateSampleSizeCalculatesCorrectPowersOfTwo() {
        val service = SnapshotCaptureService(context, imageCapture)
        val method = SnapshotCaptureService::class.java.getDeclaredMethod(
            "calculateSampleSize",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        val smallSample = method.invoke(service, 1000, 800) as Int
        assertEquals(1, smallSample)

        val largeSample = method.invoke(service, 5000, 4000) as Int
        assertEquals(4, largeSample)
    }

    @Test
    fun scaleRetainsSmallBitmapsAndDownsamplesLargeBitmaps() {
        val service = SnapshotCaptureService(context, imageCapture)
        val scaleMethod = SnapshotCaptureService::class.java.getDeclaredMethod(
            "scale",
            Bitmap::class.java,
        ).apply { isAccessible = true }

        val smallBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val unchanged = scaleMethod.invoke(service, smallBitmap) as Bitmap
        assertEquals(smallBitmap, unchanged)
        smallBitmap.recycle()

        val largeBitmap = Bitmap.createBitmap(3000, 1500, Bitmap.Config.ARGB_8888)
        val scaled = scaleMethod.invoke(service, largeBitmap) as Bitmap
        assertEquals(1536, scaled.width)
        assertEquals(768, scaled.height)
        largeBitmap.recycle()
        scaled.recycle()
    }

    @Test
    fun applyExifOrientationReturnsSourceOnNormalOrMissingFile() {
        val service = SnapshotCaptureService(context, imageCapture)
        val method = SnapshotCaptureService::class.java.getDeclaredMethod(
            "applyExifOrientation",
            String::class.java,
            Bitmap::class.java,
        ).apply { isAccessible = true }

        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val result = method.invoke(service, "/non/existent/file.jpg", source) as Bitmap
        assertEquals(source, result)
        source.recycle()
    }

    @Test
    fun applyExifOrientationRotatesAndFlips() {
        val service = SnapshotCaptureService(context, imageCapture)
        val method = SnapshotCaptureService::class.java.getDeclaredMethod(
            "applyExifOrientation",
            String::class.java,
            Bitmap::class.java,
        ).apply { isAccessible = true }

        val scratchDir = File(context.cacheDir, "exif-scratch").apply { mkdirs() }
        val tempFile = File.createTempFile("exif-", ".jpg", scratchDir)
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        FileOutputStream(tempFile).use { fos -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos) }

        val orientations = listOf(
            android.media.ExifInterface.ORIENTATION_ROTATE_90,
            android.media.ExifInterface.ORIENTATION_ROTATE_180,
            android.media.ExifInterface.ORIENTATION_ROTATE_270,
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL,
        )
        orientations.forEach { orientation ->
            val exif = android.media.ExifInterface(tempFile.absolutePath)
            exif.setAttribute(android.media.ExifInterface.TAG_ORIENTATION, orientation.toString())
            exif.saveAttributes()

            val oriented = method.invoke(service, tempFile.absolutePath, bitmap) as Bitmap
            assertNotNull(oriented)
            if (oriented !== bitmap) oriented.recycle()
        }
        bitmap.recycle()
        tempFile.delete()
        scratchDir.delete()
    }

    @Test
    fun compressForVisionDecodesAndEncodesToWebp() {
        val service = SnapshotCaptureService(context, imageCapture)
        val scratchDir = File(context.cacheDir, "test-scratch")
        scratchDir.mkdirs()
        val tempFile = File.createTempFile("test-snap-", ".jpg", scratchDir)

        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        FileOutputStream(tempFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        }
        bitmap.recycle()

        val compressMethod = SnapshotCaptureService::class.java.getDeclaredMethod(
            "compressForVision",
            File::class.java,
        ).apply { isAccessible = true }

        val webpBytes = compressMethod.invoke(service, tempFile) as ByteArray
        assertNotNull(webpBytes)
        assertTrue(webpBytes.isNotEmpty())

        tempFile.delete()
        scratchDir.delete()
    }
}
