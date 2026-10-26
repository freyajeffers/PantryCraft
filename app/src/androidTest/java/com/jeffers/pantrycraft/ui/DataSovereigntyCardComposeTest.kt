package com.jeffers.pantrycraft.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.Repository
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataSovereigntyCardComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dataSovereigntyCardRendersButtonsAndInvokesCallbacks() {
        var exportJsonCalled = false
        var exportCsvCalled = false
        var importJsonCalled = false
        var importCsvCalled = false

        val props = DataSovereigntyProps(
            onExportJson = { exportJsonCalled = true },
            onExportCsv = { exportCsvCalled = true },
            onImportJson = { importJsonCalled = true },
            onImportCsv = { importCsvCalled = true },
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                DataSovereigntyCard(props = props)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Data Sovereignty & Portability").assertIsDisplayed()

        composeTestRule.onNodeWithText("Export JSON").performClick()
        assertTrue(exportJsonCalled)

        composeTestRule.onNodeWithText("Export CSV").performClick()
        assertTrue(exportCsvCalled)

        composeTestRule.onNodeWithText("Import JSON").performClick()
        assertTrue(importJsonCalled)

        composeTestRule.onNodeWithText("Import CSV").performClick()
        assertTrue(importCsvCalled)
    }

    @Test
    fun rememberDataSovereigntyPropsInstantiatesProperly() {
        var props: DataSovereigntyProps? = null

        composeTestRule.setContent {
            PantryCraftTheme {
                val context = LocalContext.current
                val repository = Repository.getInstance(context)
                val scope = rememberCoroutineScope()
                props = rememberDataSovereigntyProps(context, repository, scope)
            }
        }
        composeTestRule.waitForIdle()

        assertNotNull(props)
        assertNotNull(props?.onExportJson)
        assertNotNull(props?.onExportCsv)
        assertNotNull(props?.onImportJson)
        assertNotNull(props?.onImportCsv)
    }
}
