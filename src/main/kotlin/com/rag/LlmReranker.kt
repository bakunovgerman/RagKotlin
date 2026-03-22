package com.rag

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class LlmReranker(
    private val apiKey: String,
    private val model: String = "deepseek/deepseek-v3.2"
) : Reranker {

    override val name = "LLM Reranker ($model)"

    private val endpoint = "https://openrouter.ai/api/v1/chat/completions"
    private val client = HttpClient(CIO) { expectSuccess = false }
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ChatMessage(val role: String, val content: String)

    @Serializable
    private data class ChatRequest(val model: String, val messages: List<ChatMessage>)

    @Serializable
    private data class Choice(val message: ChatMessage? = null)

    @Serializable
    private data class ChatResponse(val choices: List<Choice> = emptyList())

    override fun rerank(query: String, documents: List<String>, topN: Int): List<RerankResult> = runBlocking {
        val docsBlock = documents.mapIndexed { i, doc ->
            "[${i + 1}] ${doc.take(1500)}"
        }.joinToString("\n\n")

        val systemPrompt = """
            |You are a relevance scoring assistant. Your task is to evaluate how relevant each document is to the given query.
            |For each document, assign a relevance score between 0.0 (completely irrelevant) and 1.0 (perfectly relevant).
            |Output ONLY a JSON array of numbers — one score per document, in the same order as the documents.
            |Example for 3 documents: [0.95, 0.2, 0.7]
            |Do not output any other text, explanations, or formatting — ONLY the JSON array.
        """.trimMargin()

        val userPrompt = """
            |Query: $query
            |
            |Documents:
            |$docsBlock
            |
            |Output the JSON array of ${documents.size} relevance scores:
        """.trimMargin()

        val requestBody = ChatRequest(
            model = model,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userPrompt)
            )
        )

        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            setBody(Json.encodeToString(ChatRequest.serializer(), requestBody))
        }

        val bodyText = response.bodyAsText()

        if (!response.status.isSuccess()) {
            System.err.println("[LlmReranker] Ошибка: ${response.status}, body: $bodyText")
            return@runBlocking documents.mapIndexed { i, text ->
                RerankResult(index = i, relevanceScore = 0.0, text = text)
            }
        }

        val chatResponse = json.decodeFromString<ChatResponse>(bodyText)
        val content = chatResponse.choices.firstOrNull()?.message?.content ?: "[]"

        val scores = parseScores(content, documents.size)

        documents.mapIndexed { i, text ->
            RerankResult(index = i, relevanceScore = scores.getOrElse(i) { 0.0 }, text = text)
        }.sortedByDescending { it.relevanceScore }.take(topN)
    }

    private fun parseScores(raw: String, expectedSize: Int): List<Double> {
        return try {
            val cleaned = raw.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val array = Json.parseToJsonElement(cleaned).jsonArray
            array.map { it.jsonPrimitive.content.toDouble() }
        } catch (e: Exception) {
            System.err.println("[LlmReranker] Не удалось распарсить скоры: $raw")
            List(expectedSize) { 0.0 }
        }
    }

    override fun close() {
        client.close()
    }
}
