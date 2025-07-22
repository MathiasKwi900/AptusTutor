package com.nexttechtitan.aptustutor.utils

import android.util.Log

// Using an object for a static utility
object JsonExtractionUtils {
    private const val TAG = "JsonExtractionUtils"

    /**
     * Extracts a JSON object string from a larger text block, tolerating markdown code fences.
     * e.g., ```json{...}``` or just {...}
     * This is more robust than simple prefix/suffix removal.
     */
    fun extractJsonObject(text: String): String? {
        // Regex to find a JSON object possibly wrapped in markdown code fences
        val markdownJsonRegex = "```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```".toRegex(RegexOption.DOT_MATCHES_ALL)
        var match = markdownJsonRegex.find(text)
        if (match != null && match.groupValues.size > 1) {
            return match.groupValues[1]
        }

        // Fallback to find the first raw JSON object if no markdown fence is found
        val rawJsonRegex = "\\{[\\s\\S]*\\}".toRegex()
        match = rawJsonRegex.find(text)
        if (match != null) {
            return match.value
        }

        Log.w(TAG, "No JSON object found in the response string: $text")
        return null // Return null to indicate failure
    }
}