package com.jeffers.pantrycraft.security

import android.os.Build
import java.io.File

/** Lightweight local compromise signal used to protect remote credentials. */
object DeviceIntegrity {
    private val rootIndicators =
        listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/system/app/Superuser.apk",
            "/system/app/Magisk.apk",
        )

    fun isCompromised(): Boolean =
        Build.TAGS?.contains("test-keys") == true ||
            rootIndicators.any { File(it).exists() }
}
