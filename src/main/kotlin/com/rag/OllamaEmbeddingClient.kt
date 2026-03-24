package com.rag

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OllamaEmbeddingClient(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "nomic-embed-text"
) : EmbeddingClient {
    private val client = HttpClient(CIO) {
        expectSuccess = false
        installHttpLogging()
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class EmbedRequest(
        val model: String,
        val input: List<String>
    )

    @Serializable
    data class EmbedResponse(
        val embeddings: List<List<Double>>? = null
    )

    override fun getEmbeddings(texts: List<String>, task: EmbeddingTask): List<List<Double>> = runBlocking {
        if (texts.isEmpty()) return@runBlocking emptyList()

        val body = EmbedRequest(model = model, input = texts)
        val response = client.post("$baseUrl/api/embed") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(EmbedRequest.serializer(), body))
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Ollama embed failed: ${response.status}, body: ${response.bodyAsText()}")
        }

        val parsed = json.decodeFromString<EmbedResponse>(response.bodyAsText())
        parsed.embeddings ?: emptyList()
    }

    override fun close() {
        client.close()
    }
}
