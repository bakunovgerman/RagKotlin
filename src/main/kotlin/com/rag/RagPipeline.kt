package com.rag

import kotlinx.serialization.json.Json
import java.io.File

data class RerankerAnswer(
    val rerankerName: String,
    val rerankResults: List<RerankResult>,
    val filteredResults: List<RerankResult>,
    val answer: String
)

class RagPipeline(
    embeddingsFile: String = "embeddings.json",
    private val topK: Int = 3,
    private val relevanceThreshold: Double = 0.5
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val records: List<EmbeddingRecord>

    init {
        val file = File(embeddingsFile)
        if (!file.exists()) {
            throw IllegalStateException("Файл '$embeddingsFile' не найден. Сначала выполните индексацию.")
        }
        val export = json.decodeFromString<EmbeddingsExport>(file.readText(Charsets.UTF_8))
        records = export.records
        println("[RAG] Загружено ${records.size} чанков из '$embeddingsFile'")
    }

    fun answerWithRerankComparison(
        question: String,
        embeddingClient: OllamaEmbeddingClient,
        chatClient: OpenRouterChatClient,
        rerankers: List<Reranker>
    ) {
        println("\n${"=".repeat(90)}")
        println("[ВОПРОС] $question")
        println("[ПОРОГ РЕЛЕВАНТНОСТИ] $relevanceThreshold")
        println("=".repeat(90))

        val queryEmbedding = embeddingClient.getEmbedding(question)
        val topChunks = VectorSearch.findTopK(queryEmbedding, records, topK)

        println("\n[ЭТАП 1: Векторный поиск] Топ-$topK чанков:")
        topChunks.forEachIndexed { i, (record, score) ->
            println("  ${i + 1}. [${record.sourceFile}] cosine=%.4f | ${record.text.take(80)}...".format(score))
        }

        val chunkTexts = topChunks.map { it.first.text }
        val chunkRecords = topChunks.map { it.first }

        println("\n[LLM без RAG] Запрос к модели...")
        val answerWithoutRag = chatClient.ask(question)

        val contextNoRerank = buildContext(chunkRecords, chunkTexts)
        println("[LLM с RAG, без реранкинга] Запрос к модели...")
        val answerRagNoRerank = chatClient.ask(question, systemPrompt = buildRagSystemPrompt(contextNoRerank))

        val rerankerAnswers = mutableListOf<RerankerAnswer>()

        for (reranker in rerankers) {
            println("\n[ЭТАП 2: Реранкинг — ${reranker.name}]")
            val rerankResults = reranker.rerank(question, chunkTexts, topK)

            println("  Результаты реранкинга:")
            rerankResults.forEachIndexed { i, r ->
                val source = chunkRecords[r.index].sourceFile
                println("    ${i + 1}. [$source] rerank_score=%.4f | ${r.text.take(70)}...".format(r.relevanceScore))
            }

            val filtered = rerankResults.filter { it.relevanceScore >= relevanceThreshold }
            val discarded = rerankResults.size - filtered.size

            println("  После фильтрации (порог >= $relevanceThreshold): ${filtered.size} оставлено, $discarded отброшено")

            val answer = if (filtered.isEmpty()) {
                println("  [!] Все чанки отфильтрованы — ответ без контекста")
                chatClient.ask(question)
            } else {
                val filteredRecords = filtered.map { chunkRecords[it.index] }
                val filteredTexts = filtered.map { it.text }
                val context = buildContext(filteredRecords, filteredTexts)
                println("  [LLM с RAG + ${reranker.name}] Запрос к модели...")
                chatClient.ask(question, systemPrompt = buildRagSystemPrompt(context))
            }

            rerankerAnswers.add(RerankerAnswer(reranker.name, rerankResults, filtered, answer))
        }

        printComparisonResults(answerWithoutRag, answerRagNoRerank, rerankerAnswers)
    }

    private fun buildContext(records: List<EmbeddingRecord>, texts: List<String>): String {
        return records.zip(texts).joinToString("\n\n---\n\n") { (record, text) ->
            "[Источник: ${record.sourceFile}]\n$text"
        }
    }

    private fun buildRagSystemPrompt(context: String): String = buildString {
        appendLine("Ты — полезный ассистент. Отвечай на вопрос, используя ТОЛЬКО предоставленный контекст.")
        appendLine("Если в контексте нет ответа, так и скажи.")
        appendLine()
        appendLine("КОНТЕКСТ:")
        appendLine(context)
    }

    private fun printComparisonResults(
        answerWithoutRag: String,
        answerRagNoRerank: String,
        rerankerAnswers: List<RerankerAnswer>
    ) {
        println("\n${"═".repeat(90)}")
        println("                        СРАВНЕНИЕ РЕЗУЛЬТАТОВ")
        println("${"═".repeat(90)}")

        println("\n┌${"─".repeat(88)}┐")
        println("│  1. ОТВЕТ БЕЗ RAG (базовая модель)")
        println("├${"─".repeat(88)}┤")
        answerWithoutRag.lines().forEach { println("│  $it") }
        println("└${"─".repeat(88)}┘")

        println("\n┌${"─".repeat(88)}┐")
        println("│  2. ОТВЕТ С RAG (без реранкинга, топ-$topK по cosine similarity)")
        println("├${"─".repeat(88)}┤")
        answerRagNoRerank.lines().forEach { println("│  $it") }
        println("└${"─".repeat(88)}┘")

        rerankerAnswers.forEachIndexed { i, ra ->
            println("\n┌${"─".repeat(88)}┐")
            println("│  ${i + 3}. ОТВЕТ С RAG + ${ra.rerankerName}")
            println("│     (отфильтровано до ${ra.filteredResults.size} чанков, порог: $relevanceThreshold)")
            println("├${"─".repeat(88)}┤")
            ra.answer.lines().forEach { println("│  $it") }
            println("└${"─".repeat(88)}┘")
        }

        println("\n${"═".repeat(90)}")
    }
}
