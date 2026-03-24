package com.rag

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Properties

private const val DEFAULT_TEXTS_DIR = "texts"
private const val DEFAULT_OUTPUT_FILE_OLLAMA = "embeddings.json"
private const val DEFAULT_OUTPUT_FILE_JINA = "embeddings-jina-v3.json"
private const val BATCH_SIZE = 32

enum class EmbeddingProvider {
    OLLAMA,
    JINA
}

fun loadProperties(): Properties {
    val file = File("local.properties")
    if (!file.exists()) {
        throw IllegalStateException(
            "Файл 'local.properties' не найден. Создайте его и добавьте необходимые ключи"
        )
    }
    val props = Properties()
    file.inputStream().use { props.load(it) }
    return props
}

fun loadApiKey(): String {
    return loadProperties().getProperty("OPENROUTER_API_KEY")
        ?: throw IllegalStateException("OPENROUTER_API_KEY не найден в local.properties")
}

fun loadJinaApiKey(): String {
    return loadProperties().getProperty("JINA_API_KEY")
        ?: throw IllegalStateException("JINA_API_KEY не найден в local.properties")
}

fun parseEmbeddingProvider(arg: String?): EmbeddingProvider {
    return when (arg?.trim()?.lowercase()) {
        null, "", "ollama" -> EmbeddingProvider.OLLAMA
        "jina", "jina-v3", "jinaai/jina-embeddings-v3" -> EmbeddingProvider.JINA
        else -> throw IllegalArgumentException("Неизвестный embedding provider: '$arg'")
    }
}

fun isProviderArg(arg: String?): Boolean {
    val normalized = arg?.trim()?.lowercase() ?: return false
    return normalized in setOf("ollama", "jina", "jina-v3", "jinaai/jina-embeddings-v3")
}

fun defaultEmbeddingsFile(provider: EmbeddingProvider): String {
    return when (provider) {
        EmbeddingProvider.OLLAMA -> DEFAULT_OUTPUT_FILE_OLLAMA
        EmbeddingProvider.JINA -> DEFAULT_OUTPUT_FILE_JINA
    }
}

fun createEmbeddingClient(provider: EmbeddingProvider): EmbeddingClient {
    return when (provider) {
        EmbeddingProvider.OLLAMA -> OllamaEmbeddingClient()
        EmbeddingProvider.JINA -> JinaEmbeddingClient(apiKey = loadJinaApiKey())
    }
}

fun indexDocuments(provider: EmbeddingProvider, outputFile: String = defaultEmbeddingsFile(provider)) {
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

    val client = createEmbeddingClient(provider)
    val records = mutableListOf<EmbeddingRecord>()
    val json = Json { prettyPrint = true }

    try {
        allChunks.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            val texts = batch.map { it.second }
            println("Эмбеддинги (${provider.name.lowercase()}): батч ${batchIndex + 1}, ${texts.size} чанков...")
            val embeddings = client.getEmbeddings(texts, EmbeddingTask.INDEX)
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
        val output = File(outputFile)
        output.writeText(json.encodeToString(export), Charsets.UTF_8)
        println("Сохранено ${records.size} записей в ${output.absolutePath}")
    } finally {
        client.close()
    }
}

fun askQuestion(
    question: String,
    provider: EmbeddingProvider,
    embeddingsFile: String = defaultEmbeddingsFile(provider)
) {
    val apiKey = loadApiKey()
    val embeddingClient = createEmbeddingClient(provider)
    val chatClient = OpenRouterChatClient(apiKey = apiKey)
    val pipeline = RagPipeline(embeddingsFile = embeddingsFile)

    val jinaApiKey = loadJinaApiKey()
    val jinaReranker = JinaReranker(jinaApiKey)
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
            val provider = if (isProviderArg(args.getOrNull(1))) {
                parseEmbeddingProvider(args.getOrNull(1))
            } else {
                EmbeddingProvider.OLLAMA
            }
            val outputFile = args.getOrNull(2) ?: defaultEmbeddingsFile(provider)
            println("=== Режим индексации (${provider.name.lowercase()}) ===")
            indexDocuments(provider = provider, outputFile = outputFile)
        }
        args[0] == "ask" -> {
            val hasProvider = isProviderArg(args.getOrNull(1))
            val provider = if (hasProvider) parseEmbeddingProvider(args.getOrNull(1)) else EmbeddingProvider.OLLAMA
            val outputFile = if (hasProvider) {
                args.getOrNull(2) ?: defaultEmbeddingsFile(provider)
            } else {
                defaultEmbeddingsFile(provider)
            }
            val question = if (hasProvider) args.drop(3).joinToString(" ") else args.drop(1).joinToString(" ")
            if (question.isBlank()) {
                System.err.println("Укажите вопрос: ./gradlew run --args='ask [provider] [embeddings_file] Ваш вопрос'")
                return
            }
            println("=== Режим RAG-запроса (${provider.name.lowercase()}, с реранкингом) ===")
            askQuestion(question = question, provider = provider, embeddingsFile = outputFile)
        }
        else -> {
            println("Использование:")
            println("  ./gradlew run --args='index [provider] [output_file]'")
            println("  ./gradlew run --args='ask [provider] [embeddings_file] Ваш вопрос'")
            println()
            println("provider: ollama | jina")
            println("defaults:")
            println("  ollama -> $DEFAULT_OUTPUT_FILE_OLLAMA")
            println("  jina   -> $DEFAULT_OUTPUT_FILE_JINA")
        }
    }
}
