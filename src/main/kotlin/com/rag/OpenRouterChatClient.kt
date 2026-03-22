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

class OpenRouterChatClient(
    private val apiKey: String,
    private val model: String = "openai/gpt-4o-mini"
) {
    private val baseUrl = "https://openrouter.ai/api/v1/chat/completions"

    private val client = HttpClient(CIO) {
        expectSuccess = false
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class ChatMessage(
        val role: String,
        val content: String
    )

    @Serializable
    data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>
    )

    @Serializable
    data class Choice(
        val message: ChatMessage? = null
    )

    @Serializable
    data class Usage(
        @SerialName("prompt_tokens") val promptTokens: Int = 0,
        @SerialName("completion_tokens") val completionTokens: Int = 0,
        @SerialName("total_tokens") val totalTokens: Int = 0
    )

    @Serializable
    data class ChatResponse(
        val choices: List<Choice> = emptyList(),
        val usage: Usage? = null
    )

    fun chat(messages: List<ChatMessage>): ChatResponse = runBlocking {
        val requestBody = ChatRequest(model = model, messages = messages)
        val response = client.post(baseUrl) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            header("HTTP-Referer", "https://github.com/rag-kotlin")
            setBody(Json.encodeToString(ChatRequest.serializer(), requestBody))
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("OpenRouter request failed: ${response.status}, body: ${response.bodyAsText()}")
        }

        json.decodeFromString<ChatResponse>(response.bodyAsText())
    }

    fun ask(userQuestion: String, systemPrompt: String? = null): String {
        val messages = mutableListOf<ChatMessage>()
        if (systemPrompt != null) {
            messages.add(ChatMessage(role = "system", content = systemPrompt))
        }
        messages.add(ChatMessage(role = "user", content = userQuestion))
        val response = chat(messages)
        return response.choices.firstOrNull()?.message?.content ?: "(пустой ответ)"
    }

    fun close() {
        client.close()
    }
}
