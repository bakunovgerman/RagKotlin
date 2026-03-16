package com.rag

import kotlinx.serialization.Serializable

@Serializable
data class EmbeddingRecord(
    val sourceFile: String,
    val chunkIndex: Int,
    val text: String,
    val embedding: List<Double>
)

@Serializable
data class EmbeddingsExport(
    val records: List<EmbeddingRecord>
)
