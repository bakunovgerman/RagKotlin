package com.rag

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private const val DEFAULT_TEXTS_DIR = "texts"
private const val DEFAULT_OUTPUT_FILE = "embeddings.json"
private const val BATCH_SIZE = 32

fun main() {
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
        chunks.forEachIndexed { index, text ->
            allChunks.add("${file.name}" to text)
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
