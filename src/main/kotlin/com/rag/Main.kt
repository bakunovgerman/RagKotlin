package com.rag

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Properties

private const val DEFAULT_TEXTS_DIR = "texts"
private const val DEFAULT_OUTPUT_FILE = "embeddings.json"
private const val BATCH_SIZE = 32

fun loadApiKey(): String {
    val file = File("local.properties")
    if (!file.exists()) {
        throw IllegalStateException(
            "Файл 'local.properties' не найден. Создайте его и добавьте OPENROUTER_API_KEY=ваш_ключ"
        )
    }
    val props = Properties()
    file.inputStream().use { props.load(it) }
    return props.getProperty("OPENROUTER_API_KEY")
        ?: throw IllegalStateException("OPENROUTER_API_KEY не найден в local.properties")
}

fun indexDocuments() {
    val textsDir = File(DEFAULT_TEXTS_DIR)
    if (!textsDir.isDirectory) {
        System.err.println("Папка '$DEFAULT_TEXTS_DIR' не найдена. Создайте папку и поместите в неё .text файлы.")
        return
    }

    val textFiles = textsDir.listFiles { _, name -> name.endsWith(".text") }?.sortedBy { it.name } ?: emptyList()
    if (textFiles.isEmpty()) {
        System.err.println("В папке '$DEFAULT_TEXTS_DIR' нет .text файлов.")
        return
    }

    println("Найдено файлов: ${textFiles.size}")

    val chunker = Chunker
    val allChunks = mutableListOf<Pair<String, String>>()

    for (file in textFiles) {
        val content = file.readText(Charsets.UTF_8)
        val chunks = chunker.chunk(
            content,
            minTokens = 500,
            maxTokens = 1000,
            overlapTokens = 75
        )
        chunks.forEachIndexed { _, text ->
            allChunks.add(file.name to text)
        }
        println("  ${file.name}: ${chunks.size} чанков")
    }

    if (allChunks.isEmpty()) {
        System.err.println("Нет текста для эмбеддингов.")
        return
    }

    val client = OllamaEmbeddingClient()
    val records = mutableListOf<EmbeddingRecord>()
    val json = Json { prettyPrint = true }

    try {
        allChunks.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            val texts = batch.map { it.second }
            println("Эмбеддинги: батч ${batchIndex + 1}, ${texts.size} чанков...")
            val embeddings = client.getEmbeddings(texts)
            batch.forEachIndexed { i, (fileName, text) ->
                val embedding = embeddings.getOrNull(i) ?: emptyList<Double>()
                val globalIndex = batchIndex * BATCH_SIZE + i
                records.add(
                    EmbeddingRecord(
                        sourceFile = fileName,
                        chunkIndex = globalIndex,
                        text = text,
                        embedding = embedding
                    )
                )
            }
        }

        val export = EmbeddingsExport(records = records)
        val output = File(DEFAULT_OUTPUT_FILE)
        output.writeText(json.encodeToString(export), Charsets.UTF_8)
        println("Сохранено ${records.size} записей в ${output.absolutePath}")
    } finally {
        client.close()
    }
}

fun askQuestion(question: String) {
    val apiKey = loadApiKey()
    val embeddingClient = OllamaEmbeddingClient()
    val chatClient = OpenRouterChatClient(apiKey = apiKey)
    val pipeline = RagPipeline()

    val jinaReranker = JinaReranker(apiKey)
    val llmReranker = LlmReranker(apiKey)

    try {
        pipeline.answerWithRerankComparison(
            question = question,
            embeddingClient = embeddingClient,
            chatClient = chatClient,
            rerankers = listOf(jinaReranker, llmReranker)
        )
    } finally {
        embeddingClient.close()
        chatClient.close()
        jinaReranker.close()
        llmReranker.close()
    }
}

fun main(args: Array<String>) {
    when {
        args.isEmpty() || args[0] == "index" -> {
            println("=== Режим индексации ===")
            indexDocuments()
        }
        args[0] == "ask" -> {
            val question = args.drop(1).joinToString(" ")
            if (question.isBlank()) {
                System.err.println("Укажите вопрос: ./gradlew run --args='ask Ваш вопрос'")
                return
            }
            println("=== Режим RAG-запроса (с реранкингом) ===")
            askQuestion(question)
        }
        else -> {
            println("Использование:")
            println("  ./gradlew run                          — индексация документов")
            println("  ./gradlew run --args='index'           — индексация документов")
            println("  ./gradlew run --args='ask Ваш вопрос'  — задать вопрос (сравнение с/без реранкинга)")
        }
    }
}
