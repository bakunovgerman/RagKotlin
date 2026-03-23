package com.rag

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class JinaReranker(private val apiKey: String) : Reranker {

    override val name = "Jina Reranker v2 (multilingual)"

    private val endpoint = "https://api.jina.ai/v1/rerank"
    private val model = "jina-reranker-v2-base-multilingual"

    private val client = HttpClient(CIO) {
        expectSuccess = false
        installHttpLogging()
    }
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class RerankRequest(
        val model: String,
        val query: String,
        val documents: List<String>,
        @SerialName("top_n") val topN: Int
    )

    @Serializable
    private data class RerankResponseResult(
        val index: Int,
        @SerialName("relevance_score") val relevanceScore: Double,
        val document: String? = null
    )

    @Serializable
    private data class RerankResponse(
        val results: List<RerankResponseResult> = emptyList()
    )

    override fun rerank(query: String, documents: List<String>, topN: Int): List<RerankResult> = runBlocking {
        val requestBody = RerankRequest(
            model = model,
            query = query,
            documents = documents,
            topN = topN.coerceAtMost(documents.size)
        )

        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            setBody(Json.encodeToString(RerankRequest.serializer(), requestBody))
        }

        val bodyText = response.bodyAsText()

        if (!response.status.isSuccess()) {
            System.err.println("[JinaReranker] Ошибка: ${response.status}, body: $bodyText")
            return@runBlocking documents.mapIndexed { i, text ->
                RerankResult(index = i, relevanceScore = 0.0, text = text)
            }
        }

        val parsed = json.decodeFromString<RerankResponse>(bodyText)

        parsed.results.map { r ->
            RerankResult(
                index = r.index,
                relevanceScore = r.relevanceScore,
                text = r.document ?: documents[r.index]
            )
        }.sortedByDescending { it.relevanceScore }
    }

    override fun close() {
        client.close()
    }
}
