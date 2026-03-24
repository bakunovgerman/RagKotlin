package com.rag

enum class EmbeddingTask {
    INDEX,
    QUERY
}

interface EmbeddingClient {
    fun getEmbeddings(texts: List<String>, task: EmbeddingTask = EmbeddingTask.INDEX): List<List<Double>>
    fun getEmbedding(text: String, task: EmbeddingTask = EmbeddingTask.QUERY): List<Double> =
        getEmbeddings(listOf(text), task).singleOrNull() ?: emptyList()
    fun close()
}
