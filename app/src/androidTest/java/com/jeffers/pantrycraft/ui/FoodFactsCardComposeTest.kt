package com.jeffers.pantrycraft.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.Repository
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodFactsCardComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun foodFactsCardReadyRendersSizeAndDelete() {
        var deleteClicked = false
        val props = FoodFactsProps(
            catalogReady = true,
            catalogFileSize = 5_000_000L,
            catalogDownloading = false,
            catalogDownloadedBytes = 0L,
            catalogTotalBytes = 0L,
            onStartDownload = {},
            onDownloadResult = {},
            onProgress = { _, _ -> },
            onDelete = { deleteClicked = true },
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                FoodFactsCard(
                    repository = Repository.getInstance(context),
                    scope = rememberCoroutineScope(),
                    props = props,
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("FoodFacts barcode catalog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ready for offline barcode scans · 4.8 MB").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertTrue(deleteClicked)
    }

    @Test
    fun foodFactsCardNotDownloadedRendersDownloadButton() {
        var downloadStarted = false
        val props = FoodFactsProps(
            catalogReady = false,
            catalogFileSize = null,
            catalogDownloading = false,
            catalogDownloadedBytes = 0L,
            catalogTotalBytes = 0L,
            onStartDownload = { downloadStarted = true },
            onDownloadResult = {},
            onProgress = { _, _ -> },
            onDelete = {},
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                FoodFactsCard(
                    repository = Repository.getInstance(context),
                    scope = rememberCoroutineScope(),
                    props = props,
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Not downloaded").assertIsDisplayed()
        composeTestRule.onNodeWithText("Download food catalog").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertTrue(downloadStarted)
    }

    @Test
    fun foodFactsCardDownloadingRendersDeterminateProgress() {
        val props = FoodFactsProps(
            catalogReady = false,
            catalogFileSize = null,
            catalogDownloading = true,
            catalogDownloadedBytes = 500L,
            catalogTotalBytes = 1000L,
            onStartDownload = {},
            onDownloadResult = {},
            onProgress = { _, _ -> },
            onDelete = {},
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                FoodFactsCard(
                    repository = Repository.getInstance(context),
                    scope = rememberCoroutineScope(),
                    props = props,
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Downloading 500 B of 1000 B").assertIsDisplayed()
        composeTestRule.onNodeWithText("Download food catalog").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun foodFactsCardDownloadingRendersIndeterminateProgress() {
        val props = FoodFactsProps(
            catalogReady = false,
            catalogFileSize = null,
            catalogDownloading = true,
            catalogDownloadedBytes = 2048L,
            catalogTotalBytes = 0L,
            onStartDownload = {},
            onDownloadResult = {},
            onProgress = { _, _ -> },
            onDelete = {},
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                FoodFactsCard(
                    repository = Repository.getInstance(context),
                    scope = rememberCoroutineScope(),
                    props = props,
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Downloading 2.0 KB").assertIsDisplayed()
        composeTestRule.onNodeWithText("Download food catalog").assertIsDisplayed().assertIsNotEnabled()
    }
}
