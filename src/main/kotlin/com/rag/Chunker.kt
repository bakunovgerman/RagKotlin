package com.rag

/**
 * Разбивает текст на чанки по приблизительному количеству токенов.
 * Используется эвристика: ~4 символа на токен (типично для английского/русского).
 */
object Chunker {

    private const val CHARS_PER_TOKEN = 4

    /**
     * @param text Исходный текст
     * @param minTokens Минимальный размер чанка в токенах (по умолчанию 500)
     * @param maxTokens Максимальный размер чанка в токенах (по умолчанию 1000)
     * @param overlapTokens Перекрытие между чанками в токенах (50-100)
     * @return Список текстовых чанков
     */
    fun chunk(
        text: String,
        minTokens: Int = 500,
        maxTokens: Int = 1000,
        overlapTokens: Int = 75
    ): List<String> {
        val minChars = minTokens * CHARS_PER_TOKEN
        val maxChars = maxTokens * CHARS_PER_TOKEN
        val overlapChars = overlapTokens * CHARS_PER_TOKEN
        val targetChars = (minChars + maxChars) / 2

        val normalized = text.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        var start = 0

        while (start < normalized.length) {
            var end = (start + targetChars).coerceAtMost(normalized.length)

            if (end < normalized.length) {
                val lastSpace = normalized.lastIndexOf(' ', startIndex = end - 1)
                if (lastSpace > start) {
                    end = lastSpace + 1
                }
                // Если по каким‑то причинам граница не сдвинулась, гарантируем прогресс
                if (end <= start) {
                    end = (start + targetChars).coerceAtMost(normalized.length)
                }
            }

            val chunk = normalized.substring(start, end).trim()
            if (chunk.isNotEmpty()) {
                chunks.add(chunk)
            }

            // Если дошли до конца текста – выходим
            if (end >= normalized.length) break

            // Сдвигаем старт с перекрытием, но обязательно вперёд хотя бы на 1 символ
            start = (end - overlapChars).coerceAtLeast(start + 1)
        }

        return chunks
    }
}
