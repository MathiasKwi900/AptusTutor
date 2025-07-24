package com.nexttechtitan.aptustutor.utils

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.lang.Exception

// Using an object for a static utility
object JsonExtractionUtils {
    private const val TAG = "AptusTutorDebug"

    /**
     * Extracts and validates a JSON object or array string from a larger text block. This function
     * is specifically designed to be robust against common formatting issues in LLM responses,
     * such as missing markdown fences or extraneous text.
     *
     * The extraction strategy is as follows:
     * 1.  **Priority 1: Markdown-Fenced Code Block:** It first searches for a JSON object or
     * array enclosed in markdown code fences (e.g., ```json{...}``` or ```[...]```).
     * The regex used is non-greedy to prevent over-matching in case of malformed responses.
     *
     * 2.  **Priority 2: Brace/Bracket Matching Fallback:** If no markdown block is found, it
     * falls back to finding the first opening brace `{` or bracket `[` and intelligently
     * scans for its corresponding closing partner by counting nested structures. This
     * correctly extracts a JSON structure even if it's embedded in other text.
     *
     * 3.  **Priority 3: Final Validation:** The extracted string is then validated to ensure it is
     * syntactically correct JSON before being returned. This is the final safeguard against
     * returning incomplete or invalid data.
     *
     * **Known Limitation:** The brace/bracket-matching fallback does not account for braces or
     * brackets that appear inside string literals within the JSON (e.g., `{"key": "value with {a brace}"}`).
     * This is a complex parsing challenge, and for most controlled LLM prompts, this is an
     * acceptable trade-off. The markdown extraction (Priority 1) is the preferred and more
     * reliable method.
     *
     * @param text The raw text response from the LLM.
     * @return A valid JSON object or array string if one can be found and parsed, otherwise null.
     */
    fun extractJsonObject(text: String): String? {
        // Priority 1: Attempt to find JSON within a markdown code block.
        // The regex is non-greedy (.*?) to capture the shortest possible match between the fences.
        // It looks for either a JSON object `({...})` or an array `([...])`.
        val markdownRegex = "```(?:json)?\\s*(\\{.*?\\}|\\s*\\[.*?\\])\\s*```".toRegex(RegexOption.DOT_MATCHES_ALL)
        val markdownMatch = markdownRegex.find(text)
        if (markdownMatch != null) {
            // Group 1 contains the captured JSON string. Trim it for hygiene.
            val potentialJson = markdownMatch.groupValues[1].trim()
            if (isValidJson(potentialJson)) {
                Log.d(TAG, "Successfully extracted and validated JSON from markdown block.")
                return potentialJson
            } else {
                Log.w(TAG, "Found markdown block, but content was not valid JSON: $potentialJson")
            }
        }

        // Priority 2: Fallback to intelligent brace/bracket matching.
        // Find the starting character of the first potential JSON object or array.
        val firstBraceIndex = text.indexOf('{')
        val firstBracketIndex = text.indexOf('[')

        var startIndex = -1
        var startChar = ' '
        var endChar = ' '

        // Determine which structure appears first, if any.
        if (firstBraceIndex != -1 && (firstBraceIndex < firstBracketIndex || firstBracketIndex == -1)) {
            startIndex = firstBraceIndex
            startChar = '{'
            endChar = '}'
        } else if (firstBracketIndex != -1) {
            startIndex = firstBracketIndex
            startChar = '['
            endChar = ']'
        }

        // If we found a potential start character...
        if (startIndex != -1) {
            var balance = 0
            var endIndex = -1

            // Scan the string to find the matching closing character.
            for (i in startIndex until text.length) {
                when (text[i]) {
                    startChar -> balance++
                    endChar -> balance--
                }
                // When balance is 0, we've found the end of the structure.
                if (balance == 0) {
                    endIndex = i
                    break
                }
            }

            if (endIndex != -1) {
                val potentialJson = text.substring(startIndex, endIndex + 1)
                if (isValidJson(potentialJson)) {
                    Log.d(TAG, "Successfully extracted and validated JSON using fallback logic.")
                    return potentialJson
                } else {
                    // This log is crucial for debugging edge cases like braces in strings.
                    Log.w(TAG, "Fallback extracted a string, but it was not valid JSON: $potentialJson")
                }
            }
        }

        Log.e(TAG, "Failed to find any valid JSON in the response: $text")
        return null
    }

    /**
     * Checks if a given string is a valid JSON object or a valid JSON array.
     *
     * @param jsonString The string to validate.
     * @return True if the string can be parsed, false otherwise.
     */
    private fun isValidJson(jsonString: String): Boolean {
        if (jsonString.isBlank()) return false
        return try {
            // Try parsing as an object first, as it's a common case.
            JSONObject(jsonString)
            true
        } catch (e: Exception) {
            // If object parsing fails, try parsing as an array.
            try {
                JSONArray(jsonString)
                true
            } catch (e2: Exception) {
                // If both fail, it's not valid JSON.
                false
            }
        }
    }
}