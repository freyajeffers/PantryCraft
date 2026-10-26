package com.jeffers.pantrycraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DatabaseKeyProviderUnitTest {
    @Test
    fun databaseKeyInvalidatedExceptionMessageAndCause() {
        val cause = IllegalStateException("Key corrupted in KeyStore")
        val exception = DatabaseKeyInvalidatedException(cause)

        assertEquals("The hardware-backed database key is no longer valid", exception.message)
        assertEquals(cause, exception.cause)
        assertNotNull(exception.toString())
    }
}
