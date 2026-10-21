package com.jeffers.pantrycraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepositoryCsvParsingTest {
    @Test
    fun parseCsvLineSimpleCommaSeparatedValues() {
        val line = "Apples,2.5,KG,FRIDGE"
        val parsed = parseCsvLine(line)
        assertEquals(listOf("Apples", "2.5", "KG", "FRIDGE"), parsed)
    }

    @Test
    fun parseCsvLineQuotedValuesWithCommas() {
        val line = "\"Salt, Kosher\",1.0,BOX,PANTRY"
        val parsed = parseCsvLine(line)
        assertEquals(listOf("Salt, Kosher", "1.0", "BOX", "PANTRY"), parsed)
    }

    @Test
    fun parseCsvLineEscapedQuotesInsideQuotes() {
        val line = "\"Black Pepper \"\"Ground\"\"\",50,G,SPICES"
        val parsed = parseCsvLine(line)
        assertEquals(listOf("Black Pepper \"Ground\"", "50", "G", "SPICES"), parsed)
    }

    @Test
    fun parseCsvLineEmptyAndTrailingFields() {
        val line = "Milk,,L,"
        val parsed = parseCsvLine(line)
        assertEquals(listOf("Milk", "", "L", ""), parsed)
    }

    @Test
    fun optionalCsvValueRetrievesTrimmedOrNull() {
        val cols = listOf("Rice", "  ", "PANTRY")
        assertEquals("Rice", optionalCsvValue(cols, 0, available = true))
        assertNull(optionalCsvValue(cols, 1, available = true))
        assertEquals("PANTRY", optionalCsvValue(cols, 2, available = true))
        assertNull(optionalCsvValue(cols, 3, available = true))
        assertNull(optionalCsvValue(cols, 0, available = false))
    }

    @Test
    fun parseCsvLineHandlesEmptyInputAndConsecutiveCommas() {
        assertEquals(listOf(""), parseCsvLine(""))
        assertEquals(listOf("", "", "", ""), parseCsvLine(",,,"))
    }

    @Test
    fun parseCsvLineHandlesSingleQuotedValueAndUnclosedQuote() {
        assertEquals(listOf("Honey"), parseCsvLine("\"Honey\""))
        assertEquals(listOf("Raw Honey, unfiltered"), parseCsvLine("\"Raw Honey, unfiltered"))
    }
}
