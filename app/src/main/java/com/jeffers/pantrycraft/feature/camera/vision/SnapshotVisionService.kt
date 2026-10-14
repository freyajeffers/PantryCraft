package com.jeffers.pantrycraft.feature.camera.vision

/** Local model boundary; implementations must not persist or upload frame bytes. */
fun interface LocalVisionModel {
    suspend fun analyze(
        webpBytes: ByteArray,
        prompt: String,
    ): String
}

class SnapshotVisionService(
    private val model: LocalVisionModel,
    private val parser: VisionOutputParser = VisionOutputParser(),
) {
    suspend fun analyze(frame: SnapshotFrame): List<ParsedVisionItem> =
        try {
            parser.parse(model.analyze(frame.webpBytes, PROMPT))
        } finally {
            frame.close()
        }

    companion object {
        val PROMPT =
            """
            Identify all food ingredients in this shelf image. Return ONLY JSON with a detected_items array.
            Each item must contain name, category, storage_location, quantity, unit, confidence,
            estimated_shelf_life_days, is_aromatic_or_seasoning, bounding_box [ymin,xmin,ymax,xmax],
            and condition_notes. Coordinates must be normalized between 0.0 and 1.0.
            """.trimIndent()
    }
}
