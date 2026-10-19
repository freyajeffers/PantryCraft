package com.jeffers.pantrycraft.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RepositorySearchTest {
    @Test
    fun standardAlphanumericQueryPreserved() {
        assertEquals("fresh milk", sanitizeSearchQuery("fresh milk"))
        assertEquals("oatmeal 2", sanitizeSearchQuery("oatmeal 2"))
    }

    @Test
    fun specialCharactersReplacedWithSpaces() {
        assertEquals("apples bananas", sanitizeSearchQuery("apples, bananas!"))
        assertEquals("olive oil", sanitizeSearchQuery("olive-oil"))
        assertEquals("sugar free", sanitizeSearchQuery("sugar/free"))
    }

    @Test
    fun ftsReservedKeywordsFilteredOutCaseInsensitively() {
        assertEquals("", sanitizeSearchQuery("AND"))
        assertEquals("", sanitizeSearchQuery("or"))
        assertEquals("", sanitizeSearchQuery("NOT"))
        assertEquals("", sanitizeSearchQuery("Near"))
        assertEquals("", sanitizeSearchQuery("AND OR NOT NEAR"))
    }

    @Test
    fun reservedWordsMixedWithValidTokensFilteredCorrectly() {
        assertEquals("milk cookies", sanitizeSearchQuery("milk AND cookies"))
        assertEquals("coffee tea", sanitizeSearchQuery("coffee or tea"))
        assertEquals("apples oranges", sanitizeSearchQuery("apples NOT oranges"))
    }

    @Test
    fun sqlInjectionAttemptsSanitizedCleanly() {
        assertEquals("apples 1 1", sanitizeSearchQuery("apples' OR '1'='1"))
        assertEquals("banana DROP TABLE", sanitizeSearchQuery("banana; DROP TABLE"))
        assertEquals("item MATCH SELECT FROM items", sanitizeSearchQuery("item* MATCH (SELECT * FROM items)"))
    }

    @Test
    fun whitespaceAndPunctuationOnlyReturnsEmptyString() {
        assertEquals("", sanitizeSearchQuery("   "))
        assertEquals("", sanitizeSearchQuery("***"))
        assertEquals("", sanitizeSearchQuery("---"))
        assertEquals("", sanitizeSearchQuery("???"))
        assertEquals("", sanitizeSearchQuery(""))
    }
}
