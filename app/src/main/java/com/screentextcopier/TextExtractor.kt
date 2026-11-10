package com.screentextcopier

/**
 * Pure Kotlin utilities for extracting, filtering, deduplicating,
 * and formatting screen text and clipboard representations.
 */
object TextExtractor {

    /**
     * Cleans an individual string by trimming whitespace and returning null if empty.
     */
    fun cleanText(raw: CharSequence?): String? {
        val trimmed = raw?.toString()?.trim()
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }

    /**
     * Filters, cleans, and deduplicates pairs of (text, contentDescription),
     * preserving original appearance order.
     *
     * Rules:
     * 1. Blank or null strings are omitted.
     * 2. Non-empty [text] is added first if not already present.
     * 3. [contentDescription] is added only if distinct from [text] and not already in the collection.
     */
    fun processExtractedItems(rawItems: List<Pair<CharSequence?, CharSequence?>>): List<String> {
        val uniqueItems = LinkedHashSet<String>()

        for ((rawText, rawDesc) in rawItems) {
            val text = cleanText(rawText)
            if (text != null) {
                uniqueItems.add(text)
            }

            val desc = cleanText(rawDesc)
            if (desc != null && desc != text && !uniqueItems.contains(desc)) {
                uniqueItems.add(desc)
            }
        }

        return uniqueItems.toList()
    }

    /**
     * Formats extracted items into a single clipboard string separated by newlines.
     */
    fun formatClipboardText(items: List<String>): String {
        return items.joinToString("\n")
    }

    /**
     * Formats count message for toasts or status notifications.
     */
    fun formatCountMessage(count: Int): String {
        return when (count) {
            0 -> "Nie znaleziono tekstu na ekranie"
            1 -> "Skopiowano 1 linię tekstu do schowka"
            else -> "Skopiowano $count linii tekstu do schowka"
        }
    }

    /**
     * Returns a safe metadata summary (line count, character count)
     * without exposing sensitive raw contents.
     */
    fun computeTextMetadata(text: CharSequence?): Pair<Int, Int> {
        if (text.isNullOrEmpty()) return Pair(0, 0)
        val str = text.toString()
        val lineCount = str.lines().count { it.isNotBlank() }
        val charCount = str.length
        return Pair(lineCount, charCount)
    }
}
