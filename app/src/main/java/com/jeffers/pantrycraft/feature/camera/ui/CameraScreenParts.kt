package com.jeffers.pantrycraft.feature.camera.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.jeffers.pantrycraft.feature.camera.vision.MlKitSnapshotModel
import com.jeffers.pantrycraft.feature.camera.vision.ModelRouter
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotVisionService

@Composable
internal fun rememberLabelPaint(): Paint =
    remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = LABEL_TEXT_SIZE
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
    }

@Composable
internal fun rememberBadgePaint(): Paint =
    remember {
        Paint().apply {
            color =
                android.graphics.Color.argb(
                    BADGE_ALPHA,
                    BADGE_COLOR_RED,
                    BADGE_COLOR_GREEN,
                    BADGE_COLOR_BLUE,
                )
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }

@Composable
internal fun rememberSnapshotVisionService(
    context: Context,
    objectDetector: ObjectDetector? = null,
): SnapshotVisionService =
    remember(context, objectDetector) {
        val detector =
            objectDetector
                ?: ObjectDetection.getClient(ObjectDetectorOptions.DEFAULT_OPTIONS)
        SnapshotVisionService(
            ModelRouter(
                context = context.applicationContext,
                localFallbackModel = MlKitSnapshotModel(detector),
            ),
            closeModel = false,
        )
    }
