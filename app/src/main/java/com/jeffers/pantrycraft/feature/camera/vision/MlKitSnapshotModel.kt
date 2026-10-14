package com.jeffers.pantrycraft.feature.camera.vision

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetector
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Offline fallback model that converts ML Kit object labels into the shared JSON contract. */
class MlKitSnapshotModel(
    private val objectDetector: ObjectDetector,
) : LocalVisionModel,
    AutoCloseable {
    override suspend fun analyze(
        webpBytes: ByteArray,
        prompt: String,
    ): String =
        suspendCancellableCoroutine { continuation ->
            val bitmap = BitmapFactory.decodeByteArray(webpBytes, 0, webpBytes.size)
            if (bitmap == null) {
                continuation.resumeWithException(IllegalArgumentException("Invalid snapshot bytes"))
                return@suspendCancellableCoroutine
            }
            objectDetector
                .process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(executor) { objects ->
                    try {
                        val items = JSONArray()
                        objects.forEach { detected ->
                            val label = detected.labels.firstOrNull()?.text ?: "Unknown food item"
                            val box = detected.boundingBox
                            items.put(
                                JSONObject()
                                    .put("name", label)
                                    .put("category", "OTHER")
                                    .put("storage_location", "PANTRY")
                                    .put("quantity", 1.0)
                                    .put("unit", "COUNT")
                                    .put("confidence", detected.labels.firstOrNull()?.confidence ?: 0.0f)
                                    .put("estimated_shelf_life_days", 0)
                                    .put("is_aromatic_or_seasoning", false)
                                    .put(
                                        "bounding_box",
                                        JSONArray()
                                            .put((box.top.toFloat() / bitmap.height).coerceIn(0f, 1f))
                                            .put((box.left.toFloat() / bitmap.width).coerceIn(0f, 1f))
                                            .put((box.bottom.toFloat() / bitmap.height).coerceIn(0f, 1f))
                                            .put((box.right.toFloat() / bitmap.width).coerceIn(0f, 1f)),
                                    ),
                            )
                        }
                        if (continuation.isActive) continuation.resume(JSONObject().put("detected_items", items).toString())
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } finally {
                        bitmap.recycle()
                    }
                }.addOnFailureListener { error ->
                    bitmap.recycle()
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }

    override fun close() = objectDetector.close()

    companion object {
        private val executor = Executors.newSingleThreadExecutor()
    }
}
