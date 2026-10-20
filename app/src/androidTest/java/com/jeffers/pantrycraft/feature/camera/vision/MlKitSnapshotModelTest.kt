package com.jeffers.pantrycraft.feature.camera.vision

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class MlKitSnapshotModelTest {
    @Test
    fun analyzeRejectsInvalidBytesWithIllegalArgumentException() = runBlocking {
        val detector = ObjectDetection.getClient(ObjectDetectorOptions.DEFAULT_OPTIONS)
        val model = MlKitSnapshotModel(detector)

        try {
            val invalidBytes = byteArrayOf(1, 2, 3, 4, 5)
            val exception = assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    model.analyze(invalidBytes, "Detect pantry items")
                }
            }
            assertTrue(exception.message?.contains("Invalid snapshot bytes") == true)
        } finally {
            model.close()
        }
    }

    @Test
    fun analyzeProcessesValidBitmapSnapshotAndProducesContractJson() = runBlocking {
        val detector = ObjectDetection.getClient(ObjectDetectorOptions.DEFAULT_OPTIONS)
        val model = MlKitSnapshotModel(detector)

        try {
            val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.WEBP, 80, outputStream)
            val validWebpBytes = outputStream.toByteArray()

            val resultJson = model.analyze(validWebpBytes, "Detect pantry items")
            assertNotNull(resultJson)

            val parsed = JSONObject(resultJson)
            assertTrue(parsed.has("detected_items"))
            val items = parsed.getJSONArray("detected_items")
            assertNotNull(items)
        } finally {
            model.close()
        }
    }

    @Test
    fun closeClosesCleanly() {
        val detector = ObjectDetection.getClient(ObjectDetectorOptions.DEFAULT_OPTIONS)
        val model = MlKitSnapshotModel(detector)
        model.close()
    }
}
