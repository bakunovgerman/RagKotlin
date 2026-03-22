package com.rag

data class RerankResult(
    val index: Int,
    val relevanceScore: Double,
    val text: String
)

interface Reranker {
    val name: String
    fun rerank(query: String, documents: List<String>, topN: Int = 5): List<RerankResult>
    fun close()
}
