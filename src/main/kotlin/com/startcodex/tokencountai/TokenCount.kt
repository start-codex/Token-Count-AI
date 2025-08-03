package com.startcodex.tokencountai

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingType

class TokenCount {
    companion object {
        private val encoding: Encoding? by lazy {
            try {
                Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE)
            } catch (e: Exception) {
                null
            }
        }

        private val commonSingleTokenWords = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "by", "for",
            "with", "to", "of", "is", "are", "was", "were", "be", "been", "have",
            "has", "had", "do", "does", "did", "will", "would", "could", "should",
            "i", "you", "he", "she", "it", "we", "they", "me", "him", "her", "us", "them",
            "this", "that", "these", "those", "my", "your", "his", "her", "its", "our", "their"
        )

        private val specialCharsRegex = "\\s{3,}".toRegex()

        @JvmStatic
        fun calculateTokens(text: String): Int {
            if (text.isBlank()) return 0

            return try {
                encoding?.countTokens(text) ?: calculateApproximateTokens(text)
            } catch (e: Exception) {
                calculateApproximateTokens(text)
            }
        }

        private fun calculateApproximateTokens(text: String): Int {
            val words = text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }

            return words.sumOf { word ->
                when {
                    word.lowercase() in commonSingleTokenWords -> 1
                    word.matches("\\d+".toRegex()) -> 1
                    word.matches("[.,;:!?()\\[\\]{}\"']".toRegex()) -> 1
                    word.length <= 2 -> 1
                    word.length <= 4 -> 1
                    word.length <= 7 -> 2
                    word.length <= 10 -> 3
                    word.length <= 15 -> 4
                    else -> (word.length / 4) + 1
                }
            } + countSpecialTokens(text)
        }

        private fun countSpecialTokens(text: String): Int {
            var specialTokens = 0

            specialTokens += text.count { it == '\n' } / 2
            specialTokens += text.count { it == '\t' }
            specialTokens += specialCharsRegex.findAll(text).count()

            return specialTokens
        }
    }
}