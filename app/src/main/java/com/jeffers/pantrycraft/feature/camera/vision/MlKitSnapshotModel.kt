package com.jeffers.pantrycraft.feature.camera.vision

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
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
                                items.put(makeDetectedItem(detected, bitmap))
                            }
                            if (continuation.isActive) {
                                continuation.resume(
                                    JSONObject().put("detected_items", items).toString(),
                                )
                            }
                        } catch (error: JSONException) {
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
            val category =
                CATEGORY_RULES
                    .firstOrNull { (keywords, _) ->
                        keywords.any(lower::contains)
                    }?.second ?: OTHER_CATEGORY
            val displayName = if (category == OTHER_CATEGORY && lower.contains("food")) DEFAULT_ITEM_NAME else label
            return displayName to category
        }

        private fun defaultStorageForCategory(category: String): String =
            when (category) {
                "PRODUCE", "DAIRY", "MEAT", "SEAFOOD" -> FRIDGE
                "FROZEN" -> "FREEZER"
                "BAKERY" -> "COUNTER"
                else -> PANTRY
            }

        private fun defaultShelfLifeForCategory(category: String): Int =
            when (category) {
                "PRODUCE" -> PRODUCE_SHELF_LIFE_DAYS
                "DAIRY" -> DAIRY_SHELF_LIFE_DAYS
                "MEAT", "SEAFOOD" -> MEAT_SHELF_LIFE_DAYS
                "BAKERY" -> BAKERY_SHELF_LIFE_DAYS
                "FROZEN" -> FROZEN_SHELF_LIFE_DAYS
                else -> DEFAULT_SHELF_LIFE_DAYS
            }

        private const val DEFAULT_CONFIDENCE = 0.5f
        private const val DEFAULT_ITEM_NAME = "Food item"
        private const val OTHER_CATEGORY = "OTHER"
        private const val PANTRY = "PANTRY"
        private const val FRIDGE = "FRIDGE"
        private const val PRODUCE_SHELF_LIFE_DAYS = 7
        private const val DAIRY_SHELF_LIFE_DAYS = 10
        private const val MEAT_SHELF_LIFE_DAYS = 3
        private const val BAKERY_SHELF_LIFE_DAYS = 5
        private const val FROZEN_SHELF_LIFE_DAYS = 90
        private const val DEFAULT_SHELF_LIFE_DAYS = 30
        private val CATEGORY_RULES =
            listOf(
                listOf("fruit", "vegetable", "produce", "plant") to "PRODUCE",
                listOf("dairy", "milk", "cheese", "yogurt") to "DAIRY",
                listOf("meat", "beef", "chicken", "pork", "fish", "seafood") to "MEAT",
                listOf("bread", "bakery", "pastry") to "BAKERY",
                listOf("beverage", "drink", "juice", "bottle") to "BEVERAGES",
                listOf("snack", "chip", "cookie") to "SNACKS",
                listOf("canned", "can") to "CANNED",
                listOf("frozen") to "FROZEN",
                listOf("grain", "cereal", "rice", "pasta") to "GRAINS",
            )

        private fun makeDetectedItem(
            detected: com.google.mlkit.vision.objects.DetectedObject,
            bitmap: android.graphics.Bitmap,
        ): JSONObject {
            val rawLabel = detected.labels.firstOrNull()?.text ?: DEFAULT_ITEM_NAME
            val (itemName, category) = mapLabelToCategory(rawLabel)
            val box = detected.boundingBox
            val bounding =
                JSONArray()
                    .put((box.top.toFloat() / bitmap.height).coerceIn(0f, 1f))
                    .put((box.left.toFloat() / bitmap.width).coerceIn(0f, 1f))
                    .put((box.bottom.toFloat() / bitmap.height).coerceIn(0f, 1f))
                    .put((box.right.toFloat() / bitmap.width).coerceIn(0f, 1f))

            return JSONObject()
                .put("name", itemName)
                .put("category", category)
                .put("storage_location", defaultStorageForCategory(category))
                .put("quantity", 1.0)
                .put("unit", "COUNT")
                .put("confidence", detected.labels.firstOrNull()?.confidence ?: DEFAULT_CONFIDENCE)
                .put("estimated_shelf_life_days", defaultShelfLifeForCategory(category))
                .put("is_aromatic_or_seasoning", category == "SPICES" || category == "CONDIMENTS")
                .put("bounding_box", bounding)
        }
    }
}
