package com.jeffers.pantrycraft.feature.camera.vision

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
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

    @Test
    fun analyzeHandlesDetectorFailureByResumingWithException(): Unit = runBlocking {
        val failingDetector = java.lang.reflect.Proxy.newProxyInstance(
            com.google.mlkit.vision.objects.ObjectDetector::class.java.classLoader,
            arrayOf(com.google.mlkit.vision.objects.ObjectDetector::class.java),
        ) { _, method, _ ->
            if (method.name == "process") {
                com.google.android.gms.tasks.Tasks.forException<List<com.google.mlkit.vision.objects.DetectedObject>>(
                    IllegalStateException("Simulated detector error"),
                )
            } else null
        } as com.google.mlkit.vision.objects.ObjectDetector
        val model = MlKitSnapshotModel(failingDetector)

        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.WEBP, 80, stream)
        val bytes = stream.toByteArray()

        try {
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    model.analyze(bytes, "Detect")
                }
            }
        } finally {
            model.close()
        }
    }

    @Test
    fun analyzeWithDetectedObjectsPopulatesDetectedItems(): Unit = runBlocking {
        val label = com.google.mlkit.vision.objects.DetectedObject.Label("Fresh Fruit", 0.95f, 0)
        val detected = com.google.mlkit.vision.objects.DetectedObject(
            android.graphics.Rect(10, 20, 60, 80),
            1,
            listOf(label),
        )
        val successDetector = java.lang.reflect.Proxy.newProxyInstance(
            com.google.mlkit.vision.objects.ObjectDetector::class.java.classLoader,
            arrayOf(com.google.mlkit.vision.objects.ObjectDetector::class.java),
        ) { _, method, _ ->
            if (method.name == "process") {
                com.google.android.gms.tasks.Tasks.forResult(listOf(detected))
            } else null
        } as com.google.mlkit.vision.objects.ObjectDetector
        val model = MlKitSnapshotModel(successDetector)

        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.WEBP, 80, stream)
        val bytes = stream.toByteArray()

        try {
            val resultJson = model.analyze(bytes, "Detect")
            val parsed = JSONObject(resultJson)
            val items = parsed.getJSONArray("detected_items")
            assertEquals(1, items.length())
            val item = items.getJSONObject(0)
            assertEquals("Fresh Fruit", item.getString("name"))
            assertEquals("PRODUCE", item.getString("category"))
            assertEquals("FRIDGE", item.getString("storage_location"))
        } finally {
            model.close()
        }
    }
}
