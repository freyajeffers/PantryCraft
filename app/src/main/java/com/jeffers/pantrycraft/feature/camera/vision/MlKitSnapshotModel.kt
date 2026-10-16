package com.jeffers.pantrycraft.feature.camera.vision

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
        withContext(Dispatchers.Default) {
            suspendCancellableCoroutine { continuation ->
                val bitmap = BitmapFactory.decodeByteArray(webpBytes, 0, webpBytes.size)
                if (bitmap == null) {
                    continuation.resumeWithException(IllegalArgumentException("Invalid snapshot bytes"))
                    return@suspendCancellableCoroutine
                }
                objectDetector
                    .process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { objects ->
                        try {
                            val items = JSONArray()
                            objects.forEach { detected ->
                                val rawLabel = detected.labels.firstOrNull()?.text ?: "Food item"
                                val (itemName, category) = mapLabelToCategory(rawLabel)
                                val box = detected.boundingBox
                                items.put(
                                    JSONObject()
                                        .put("name", itemName)
                                        .put("category", category)
                                        .put("storage_location", defaultStorageForCategory(category))
                                        .put("quantity", 1.0)
                                        .put("unit", "COUNT")
                                        .put("confidence", detected.labels.firstOrNull()?.confidence ?: 0.5f)
                                        .put("estimated_shelf_life_days", defaultShelfLifeForCategory(category))
                                        .put("is_aromatic_or_seasoning", category == "SPICES" || category == "CONDIMENTS")
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
        }

    override fun close() = objectDetector.close()

    companion object {
        private fun mapLabelToCategory(label: String): Pair<String, String> {
            val lower = label.lowercase()
            return when {
                lower.contains("fruit") || lower.contains("vegetable") || lower.contains("produce") || lower.contains("plant") ->
                    Pair(label, "PRODUCE")
                lower.contains("dairy") || lower.contains("milk") || lower.contains("cheese") || lower.contains("yogurt") ->
                    Pair(label, "DAIRY")
                lower.contains("meat") ||
                    lower.contains("beef") ||
                    lower.contains("chicken") ||
                    lower.contains("pork") ||
                    lower.contains("fish") ||
                    lower.contains("seafood") ->
                    Pair(label, "MEAT")
                lower.contains("bread") || lower.contains("bakery") || lower.contains("pastry") ->
                    Pair(label, "BAKERY")
                lower.contains("beverage") || lower.contains("drink") || lower.contains("juice") || lower.contains("bottle") ->
                    Pair(label, "BEVERAGES")
                lower.contains("snack") || lower.contains("chip") || lower.contains("cookie") ->
                    Pair(label, "SNACKS")
                lower.contains("canned") || lower.contains("can") ->
                    Pair(label, "CANNED")
                lower.contains("frozen") ->
                    Pair(label, "FROZEN")
                lower.contains("grain") || lower.contains("cereal") || lower.contains("rice") || lower.contains("pasta") ->
                    Pair(label, "GRAINS")
                lower.contains("food") ->
                    Pair("Food item", "OTHER")
                else ->
                    Pair(label, "OTHER")
            }
        }

        private fun defaultStorageForCategory(category: String): String =
            when (category) {
                "PRODUCE", "DAIRY", "MEAT", "SEAFOOD" -> "FRIDGE"
                "FROZEN" -> "FREEZER"
                "BAKERY" -> "COUNTER"
                else -> "PANTRY"
            }

        private fun defaultShelfLifeForCategory(category: String): Int =
            when (category) {
                "PRODUCE" -> 7
                "DAIRY" -> 10
                "MEAT", "SEAFOOD" -> 3
                "BAKERY" -> 5
                "FROZEN" -> 90
                else -> 30
            }
    }
}
