package com.jeffers.pantrycraft.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.FakeTestUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Proxy

class BackupPreferencesTest {
    private fun createInMemorySharedPreferences(): SharedPreferences {
        val store = mutableMapOf<String, Any?>()
        val pending = mutableMapOf<String, Any?>()

        val editor =
            Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java),
            ) { proxy, method, args ->
                when (method.name) {
                    "putString" -> {
                        pending[args[0] as String] = args[1]
                        proxy
                    }

                    "apply", "commit" -> {
                        store.putAll(pending)
                        pending.clear()
                        true
                    }

                    else -> {
                        proxy
                    }
                }
            } as SharedPreferences.Editor

        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "edit" -> {
                    editor
                }

                "getString" -> {
                    val key = args[0] as String
                    val def = args[1] as String?
                    store.getOrDefault(key, def)
                }

                else -> {
                    null
                }
            }
        } as SharedPreferences
    }

    private fun createMockContext(prefs: SharedPreferences): Context =
        object : ContextWrapper(null) {
            override fun getSharedPreferences(
                name: String?,
                mode: Int,
            ): SharedPreferences = prefs
        }

    @Test
    fun persistAndReadBackupUrisWhenNull() {
        val prefs = createInMemorySharedPreferences()
        val context = createMockContext(prefs)

        persistBackupUri(context, null)
        persistAutoBackupTreeUri(context, null)

        assertNull(persistedBackupUri(context))
        assertNull(persistedAutoBackupTreeUri(context))
    }

    @Test
    fun persistAndReadBackupUrisWhenNonNull() {
        val prefs = createInMemorySharedPreferences()
        val context = createMockContext(prefs)
        val uri = FakeTestUri("content://backup/my_backup.pcbk")

        persistBackupUri(context, uri)
        persistAutoBackupTreeUri(context, uri)

        assertEquals("content://backup/my_backup.pcbk", prefs.getString("last_exported_uri", null))
        assertEquals("content://backup/my_backup.pcbk", prefs.getString("auto_backup_tree_uri", null))
    }

    @Test
    fun persistedUrisParseStoredStrings() {
        val prefs = createInMemorySharedPreferences()
        val context = createMockContext(prefs)

        prefs
            .edit()
            .putString("last_exported_uri", "content://backup/last.pcbk")
            .putString("auto_backup_tree_uri", "content://backup/tree")
            .apply()

        // Triggers the ?.let(Uri::parse) lambdas
        persistedBackupUri(context)
        persistedAutoBackupTreeUri(context)

        assertEquals("content://backup/last.pcbk", prefs.getString("last_exported_uri", null))
        assertEquals("content://backup/tree", prefs.getString("auto_backup_tree_uri", null))
    }
}
