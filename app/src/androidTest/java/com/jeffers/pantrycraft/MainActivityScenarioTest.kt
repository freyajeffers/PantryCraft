package com.jeffers.pantrycraft

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.ExpirationAlertWorker
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityScenarioTest {

    @Test
    fun mainActivityLaunchesSuccessfully() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            assertNotNull(activity)
            activity.finish()
        }
        scenario.close()
    }

    @Test
    fun mainActivityLaunchesWithExpirationAlertsEnabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = ExpirationAlertWorker.isEnabled(context)
        ExpirationAlertWorker.setEnabled(context, true)
        try {
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { activity ->
                assertNotNull(activity)
                activity.finish()
            }
            scenario.close()
        } finally {
            ExpirationAlertWorker.setEnabled(context, original)
        }
    }
}
