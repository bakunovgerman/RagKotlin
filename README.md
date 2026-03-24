# RagKotlin

RAG-приложение на Kotlin: индексация `.text` документов (чанкинг + эмбеддинги через Ollama или Jina) и ответы на вопросы с помощью LLM (OpenRouter API, `openai/gpt-4o-mini`). Поддерживает сравнение качества retrieval при разных embedding-моделях.

## Требования

- JDK 17+
- [Ollama](https://ollama.com) с моделью `nomic-embed-text` (если используете `ollama`)
- API-ключ [OpenRouter](https://openrouter.ai)
- API-ключ [Jina AI](https://jina.ai) (если используете `jina` для эмбеддингов и Jina-reranker)

## Установка Ollama

### macOS / Linux

1. Установите Ollama:
   ```bash
   curl -fsSL https://ollama.com/install.sh | sh
   ```

2. Запустите сервер:
   ```bash
   ollama serve
   ```

3. Скачайте модель эмбеддингов:
   ```bash
   ollama pull nomic-embed-text
   ```

### Windows

1. Скачайте установщик с https://ollama.com/download.
2. Скачайте модель:
   ```bash
   ollama pull nomic-embed-text
   ```

## Настройка API-ключа

Создайте файл `local.properties` в корне проекта:

```properties
OPENROUTER_API_KEY=sk-or-v1-ваш-ключ
JINA_API_KEY=jina_ваш_ключ
```

## Запуск

### 1. Индексация документов

Поместите `.text` файлы в папку `texts/` и запустите:

```bash
./gradlew run --args='index [provider] [output_file]'
```

Где:
- `provider`: `ollama` или `jina` (по умолчанию `ollama`)
- `output_file`: опциональный путь к выходному JSON

Примеры:

```bash
./gradlew run --args='index ollama embeddings-ollama.json'
./gradlew run --args='index jina embeddings-jina.json'
```

Если `output_file` не указан:
- для `ollama` используется `embeddings.json`
- для `jina` используется `embeddings-jina-v3.json`

### 2. RAG-запрос

```bash
./gradlew run --args='ask [provider] [embeddings_file] Ваш вопрос'
```

Приложение выполнит:
1. Эмбеддинг вопроса через выбранный provider (`ollama`/`jina`)
2. Поиск топ-K релевантных чанков
3. Запрос к LLM **без RAG** (только вопрос)
4. Запрос к LLM **с RAG без реранкинга**
5. Реранкинг найденных чанков:
   - Jina Reranker v2
   - LLM Reranker
6. Вывод сравнительных ответов в лог

Примеры:

```bash
./gradlew run --args='ask Что такое RAG?'
./gradlew run --args='ask ollama embeddings-ollama.json Что такое RAG?'
./gradlew run --args='ask jina embeddings-jina.json Что такое RAG?'
```

### 3. Как сравнить `ollama` vs `jina` эмбеддинги

1. Постройте два индекса:
   - `./gradlew run --args='index ollama embeddings-ollama.json'`
   - `./gradlew run --args='index jina embeddings-jina.json'`
2. Запустите один и тот же вопрос в обоих режимах:
   - `./gradlew run --args='ask ollama embeddings-ollama.json Ваш вопрос'`
   - `./gradlew run --args='ask jina embeddings-jina.json Ваш вопрос'`
3. Сравните:
   - какие чанки попадают в топ выдачи
   - оценки реранкеров и отфильтрованный контекст
   - финальные ответы LLM

## Архитектура

| Компонент | Файл | Назначение |
|-----------|------|------------|
| Chunker | `Chunker.kt` | Разбивка текста на чанки (500–1000 токенов, перекрытие 75) |
| Embedding API | `EmbeddingClient.kt` | Общий интерфейс embedding-клиентов (`INDEX`/`QUERY` задачи) |
| Embedding (Ollama) | `OllamaEmbeddingClient.kt` | Эмбеддинги через Ollama API |
| Embedding (Jina) | `JinaEmbeddingClient.kt` | Эмбеддинги через Jina API (`jinaai/jina-embeddings-v3`) |
| Vector Search | `VectorSearch.kt` | Cosine similarity + top-K поиск |
| LLM Chat | `OpenRouterChatClient.kt` | Запросы к OpenRouter API (`openai/gpt-4o-mini`) |
| Rerankers | `JinaReranker.kt`, `LlmReranker.kt` | Дополнительный реранкинг найденных чанков |
| RAG Pipeline | `RagPipeline.kt` | Оркестрация: вопрос → retrieval → rerank → LLM |
| Data | `EmbeddingRecord.kt` | Модели данных для эмбеддингов |

## Структура `embeddings.json`

- `sourceFile` — имя исходного `.text` файла
- `chunkIndex` — индекс чанка
- `text` — текст чанка
- `embedding` — вектор эмбеддинга (массив чисел)
