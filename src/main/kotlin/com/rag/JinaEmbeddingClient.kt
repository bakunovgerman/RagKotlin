package com.rag

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class JinaEmbeddingClient(
    private val apiKey: String,
    private val model: String = "jina-embeddings-v3",
    private val endpoint: String = "https://api.jina.ai/v1/embeddings"
) : EmbeddingClient {
    private val client = HttpClient(CIO) {
        expectSuccess = false
        installHttpLogging()
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class EmbeddingRequest(
        val model: String,
        val input: List<String>,
        val task: String
    )

    @Serializable
    private data class EmbeddingData(
        val index: Int,
        val embedding: List<Double>
    )

    @Serializable
    private data class EmbeddingResponse(
        val data: List<EmbeddingData> = emptyList()
    )

    override fun getEmbeddings(texts: List<String>, task: EmbeddingTask): List<List<Double>> = runBlocking {
        if (texts.isEmpty()) return@runBlocking emptyList()
        val resolvedModel = if (model.startsWith("jinaai/")) model.removePrefix("jinaai/") else model

        val taskValue = when (task) {
            EmbeddingTask.INDEX -> "retrieval.passage"
            EmbeddingTask.QUERY -> "retrieval.query"
        }

        val requestBody = EmbeddingRequest(
            model = resolvedModel,
            input = texts,
            task = taskValue
        )

        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(Json.encodeToString(EmbeddingRequest.serializer(), requestBody))
        }

        val bodyText = response.bodyAsText()

        if (!response.status.isSuccess()) {
            throw RuntimeException("Jina embeddings failed: ${response.status}, body: $bodyText")
        }

        val parsed = json.decodeFromString<EmbeddingResponse>(bodyText)
        val byIndex = parsed.data.associateBy { it.index }
        texts.indices.map { i -> byIndex[i]?.embedding ?: emptyList() }
    }

    override fun close() {
        client.close()
    }
}
