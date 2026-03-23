package com.rag

import kotlin.math.sqrt

object VectorSearch {

    private val tokenRegex = Regex("[^\\p{L}\\p{Nd}]+")

    fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0.0) 0.0 else dot / denom
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(tokenRegex, " ").trim()

    private fun tokens(text: String): Set<String> =
        normalize(text).split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()

    private fun lexicalCoverage(queryTokens: Set<String>, textTokens: Set<String>): Double {
        if (queryTokens.isEmpty()) return 0.0
        val matched = queryTokens.count { it in textTokens }
        return matched.toDouble() / queryTokens.size.toDouble()
    }

    fun findTopK(
        queryText: String,
        queryEmbedding: List<Double>,
        records: List<EmbeddingRecord>,
        topK: Int = 3
    ): List<Pair<EmbeddingRecord, Double>> {
        val queryTokens = tokens(queryText)
        val normalizedQuery = normalize(queryText)
        val shortQuery = queryTokens.size <= 3

        return records
            .map { record ->
                val cosine = cosineSimilarity(queryEmbedding, record.embedding)
                val textTokens = tokens(record.text)
                val lexical = lexicalCoverage(queryTokens, textTokens)
                val normalizedText = normalize(record.text)

                // For short entity-like queries, rely more on lexical match.
                val baseScore = if (shortQuery) {
                    (cosine * 0.55) + (lexical * 0.45)
                } else {
                    (cosine * 0.8) + (lexical * 0.2)
                }

                val phraseBoost = if (normalizedQuery.length >= 4 && normalizedText.contains(normalizedQuery)) 0.2 else 0.0
                val allTokensBoost = if (queryTokens.isNotEmpty() && queryTokens.all { it in textTokens }) 0.15 else 0.0
                val finalScore = (baseScore + phraseBoost + allTokensBoost).coerceAtMost(1.0)

                record to finalScore
            }
            .sortedByDescending { it.second }
            .take(topK)
    }
}
