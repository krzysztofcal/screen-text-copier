package com.screentextcopier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextExtractorTest {

    @Test
    fun testEmptyInputReturnsEmptyList() {
        val result = TextExtractor.processExtractedItems(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun testBlankAndNullItemsAreFilteredOut() {
        val input = listOf(
            null to null,
            "" to "   ",
            "   " to null,
            "\n\t" to "  "
        )
        val result = TextExtractor.processExtractedItems(input)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testHasUsableTextValidation() {
        assertFalse(TextExtractor.hasUsableText(null, null))
        assertFalse(TextExtractor.hasUsableText("", ""))
        assertFalse(TextExtractor.hasUsableText("   ", "\n\t  "))
        assertTrue(TextExtractor.hasUsableText("Hello", null))
        assertTrue(TextExtractor.hasUsableText(null, "Icon description"))
        assertTrue(TextExtractor.hasUsableText("  Trimmed  ", "Description"))
    }

    @Test
    fun testAccessibilityFallbackRegressionPrevented() {
        // Scenario: Active window has only empty/container nodes (e.g. FrameLayouts, ViewGroups)
        val emptyRootNodes = listOf(
            null to null,
            "   " to null,
            "" to "",
            null to "   "
        )

        // Filter usable items as done in the service
        val usableRootItems = emptyRootNodes.filter { (text, desc) ->
            TextExtractor.hasUsableText(text, desc)
        }

        // Must be empty so the service correctly triggers the fallback to secondary windows!
        assertTrue(usableRootItems.isEmpty())

        // Simulated secondary window content
        val secondaryWindowNodes = listOf(
            "Dialog Title" to null,
            "Confirm" to "Click to proceed"
        )
        val usableSecondaryItems = secondaryWindowNodes.filter { (text, desc) ->
            TextExtractor.hasUsableText(text, desc)
        }

        // Processing should now succeed with secondary window content
        val processed = TextExtractor.processExtractedItems(usableSecondaryItems)
        assertEquals(listOf("Dialog Title", "Confirm", "Click to proceed"), processed)
    }

    @Test
    fun testWhitespaceTrimmingAndDeduplicationPreservingOrder() {
        val input = listOf(
            "  First item  " to null,
            "Second item" to null,
            "First item" to null,       // duplicate
            "  Second item  " to null,   // duplicate with whitespace
            "Third item" to null
        )
        val result = TextExtractor.processExtractedItems(input)
        assertEquals(listOf("First item", "Second item", "Third item"), result)
    }

    @Test
    fun testContentDescriptionAddedWhenDistinct() {
        val input = listOf(
            "Header" to "Header description",
            "Button" to "Submit form"
        )
        val result = TextExtractor.processExtractedItems(input)
        assertEquals(listOf("Header", "Header description", "Button", "Submit form"), result)
    }

    @Test
    fun testContentDescriptionIgnoredWhenMatchesTextOrAlreadyPresent() {
        val input = listOf(
            "Settings" to "Settings",              // identical
            "Search" to "Search",                  // identical
            "Profile" to "Settings"                // already in list from first item
        )
        val result = TextExtractor.processExtractedItems(input)
        assertEquals(listOf("Settings", "Search", "Profile"), result)
    }

    @Test
    fun testFormatClipboardTextJoinsWithNewline() {
        val items = listOf("Line 1", "Line 2", "Line 3")
        val formatted = TextExtractor.formatClipboardText(items)
        assertEquals("Line 1\nLine 2\nLine 3", formatted)
    }

    @Test
    fun testFormatCountMessage() {
        assertEquals("No accessible text found on screen", TextExtractor.formatCountMessage(0))
        assertEquals("Copied 1 line of text to clipboard", TextExtractor.formatCountMessage(1))
        assertEquals("Copied 5 lines of text to clipboard", TextExtractor.formatCountMessage(5))
    }

    @Test
    fun testComputeTextMetadata() {
        val sample = "First line\nSecond line\n\nThird line"
        val (lines, chars) = TextExtractor.computeTextMetadata(sample)
        assertEquals(3, lines)
        assertEquals(sample.length, chars)

        val (emptyLines, emptyChars) = TextExtractor.computeTextMetadata("")
        assertEquals(0, emptyLines)
        assertEquals(0, emptyChars)
    }
}
