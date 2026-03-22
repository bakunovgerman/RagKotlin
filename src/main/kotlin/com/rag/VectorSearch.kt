package com.rag

import kotlin.math.sqrt

object VectorSearch {

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

    fun findTopK(
        queryEmbedding: List<Double>,
        records: List<EmbeddingRecord>,
        topK: Int = 3
    ): List<Pair<EmbeddingRecord, Double>> {
        return records
            .map { record -> record to cosineSimilarity(queryEmbedding, record.embedding) }
            .sortedByDescending { it.second }
            .take(topK)
    }
}
