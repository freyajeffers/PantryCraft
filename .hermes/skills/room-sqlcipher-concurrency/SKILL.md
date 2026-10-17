---
name: room-sqlcipher-concurrency
description: Harden local-first Room SQLite persistence with SQLCipher encryption, Android KeyStore StrongBox keys, WAL concurrency, episodic memory tables, and migration tests.
---

# Room SQLCipher Storage Hardening & Concurrency

Standard operating procedures for configuring AES-256 encrypted SQLite databases using Room and SQLCipher on Android with support for agentic episodic memory.

## When to Use
- Provisioning encrypted local persistence for privacy-first applications.
- Deriving database passkeys using Android KeyStore (StrongBox / TEE).
- Configuring non-blocking SQLite concurrency for simultaneous UI reads, background agent ReAct tool calls, and voice writes.
- Hardening episodic reflection tables (`culinary_reflections`) against memory leaks.
- Handling `KeyPermanentlyInvalidatedException` gracefully without crash loops.

## Core Architectural Invariants
1. **Encrypted Passphrase Pattern:** Android KeyStore symmetric keys cannot export raw bytes. Generate a cryptographically secure 256-bit passphrase using `SecureRandom`. Encrypt this passphrase at rest with an Android KeyStore AES-256-GCM master key (configured with StrongBox on API 28+ with fallback to TEE KeyStore on API 23–27).
2. **Runtime Decryption & Memory Hygiene:** Decrypt the 256-bit passphrase into memory only when constructing `SupportFactory(passphraseBytes)` for Room.
3. **WAL Mode Concurrency:** Mandate Write-Ahead Logging (`setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)`) to allow background agentic tool calls and ingestion transactions to execute concurrently without locking out UI read queries.
4. **Busy Timeout Ceiling:** Set `PRAGMA busy_timeout = 5000;` to avoid `SQLiteDatabaseLockedException` during burst writes.
5. **Memory-Mapped I/O:** Set `PRAGMA mmap_size = 268435456;` (256 MB) to accelerate read scans during full-database meal synthesis.

## Implementation Pattern & Invalidation Recovery
```kotlin
object SecurityKeyManager {
    private const val KEY_ALIAS = "pantrycraft_db_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        // StrongBox Keymaster hardware isolation where supported (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                builder.setIsStrongBoxBacked(true)
            } catch (e: Exception) {
                // Fall back to standard TEE
            }
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    /**
     * Decrypts the stored database passphrase. Catches KeyPermanentlyInvalidatedException
     * if the user resets device lock screen credentials.
     */
    fun decryptDatabasePassphrase(context: Context): Result<ByteArray> {
        return try {
            val masterKey = getOrCreateMasterKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val encryptedBytes = loadEncryptedBlob(context)
            val iv = loadIv(context)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
            Result.success(cipher.doFinal(encryptedBytes))
        } catch (e: KeyPermanentlyInvalidatedException) {
            Result.failure(e)
        }
    }
}

object DatabaseFactory {
    fun createEncryptedDatabase(context: Context, passphrase: ByteArray): AppDatabase {
        val openHelperFactory = net.zetetic.database.sqlcipher.SupportFactory(passphrase)
        
        return Room.databaseBuilder(context, AppDatabase::class.java, "pantry_encrypted.db")
            .openHelperFactory(openHelperFactory)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA busy_timeout = 5000;")
                    db.execSQL("PRAGMA mmap_size = 268435456;")
                    db.execSQL("PRAGMA foreign_keys = ON;")
                }
            })
            .build()
    }
}
```
