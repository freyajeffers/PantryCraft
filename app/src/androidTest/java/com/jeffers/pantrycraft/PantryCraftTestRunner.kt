package com.jeffers.pantrycraft

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.jeffers.pantrycraft.data.TestDatabaseCleanup

class PantryCraftTestRunner : AndroidJUnitRunner() {
    override fun onStart() {
        runCatching { TestDatabaseCleanup.cleanup(targetContext) }
        super.onStart()
    }

    override fun finish(
        resultCode: Int,
        results: Bundle,
    ) {
        runCatching { TestDatabaseCleanup.cleanup(targetContext) }
        super.finish(resultCode, results)
    }
}
