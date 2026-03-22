package com.rag

import kotlinx.serialization.json.Json
import java.io.File

class RagPipeline(
    private val embeddingsFile: String = "embeddings.json",
    private val topK: Int = 3
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val records: List<EmbeddingRecord>

    init {
        val file = File(embeddingsFile)
        if (!file.exists()) {
            throw IllegalStateException("Файл '$embeddingsFile' не найден. Сначала выполните индексацию (генерацию эмбеддингов).")
        }
        val export = json.decodeFromString<EmbeddingsExport>(file.readText(Charsets.UTF_8))
        records = export.records
        println("[RAG] Загружено ${records.size} чанков из '$embeddingsFile'")
    }

    fun answerQuestion(
        question: String,
        embeddingClient: OllamaEmbeddingClient,
        chatClient: OpenRouterChatClient
    ) {
        println("\n${"=".repeat(80)}")
        println("[ВОПРОС] $question")
        println("=".repeat(80))

        val queryEmbedding = embeddingClient.getEmbedding(question)
        val topChunks = VectorSearch.findTopK(queryEmbedding, records, topK)

        println("\n[RAG] Найдено топ-$topK релевантных чанков:")
        topChunks.forEachIndexed { i, (record, score) ->
            println("  ${i + 1}. [${record.sourceFile}] score=%.4f | ${record.text.take(80)}...".format(score))
        }

        val context = topChunks.joinToString("\n\n---\n\n") { (record, _) ->
            "[Источник: ${record.sourceFile}]\n${record.text}"
        }

        val ragPrompt = buildString {
            appendLine("Ты — полезный ассистент. Отвечай на вопрос, используя ТОЛЬКО предоставленный контекст.")
            appendLine("Если в контексте нет ответа, так и скажи.")
            appendLine()
            appendLine("КОНТЕКСТ:")
            appendLine(context)
        }

        println("\n[LLM без RAG] Запрос к модели...")
        val answerWithoutRag = chatClient.ask(question)

        println("[LLM с RAG] Запрос к модели...")
        val answerWithRag = chatClient.ask(question, systemPrompt = ragPrompt)

        println("\n${"─".repeat(80)}")
        println("[ОТВЕТ БЕЗ RAG]:")
        println(answerWithoutRag)
        println()
        println("${"─".repeat(80)}")
        println("[ОТВЕТ С RAG]:")
        println(answerWithRag)
        println("${"─".repeat(80)}")
    }
}
