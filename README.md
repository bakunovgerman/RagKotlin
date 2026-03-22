# RagKotlin

RAG-приложение на Kotlin: индексация `.text` документов (чанкинг + эмбеддинги через Ollama) и ответы на вопросы с помощью LLM (OpenRouter API, `openai/gpt-4o-mini`). Логирует ответ модели с RAG-контекстом и без него для сравнения.

## Требования

- JDK 17+
- [Ollama](https://ollama.com) с моделью `nomic-embed-text`
- API-ключ [OpenRouter](https://openrouter.ai)

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
```

## Запуск

### 1. Индексация документов

Поместите `.text` файлы в папку `texts/` и запустите:

```bash
./gradlew run
```

Результат — файл `embeddings.json` с чанками и их эмбеддингами.

### 2. RAG-запрос

```bash
./gradlew run --args='ask Что такое RAG?'
```

Приложение выполнит:
1. Эмбеддинг вопроса через Ollama
2. Поиск топ-3 релевантных чанков (cosine similarity)
3. Запрос к LLM **без RAG** (только вопрос)
4. Запрос к LLM **с RAG** (вопрос + контекст из найденных чанков)
5. Вывод обоих ответов в лог для сравнения

## Архитектура

| Компонент | Файл | Назначение |
|-----------|------|------------|
| Chunker | `Chunker.kt` | Разбивка текста на чанки (500–1000 токенов, перекрытие 75) |
| Embedding | `OllamaEmbeddingClient.kt` | Получение эмбеддингов через Ollama API |
| Vector Search | `VectorSearch.kt` | Cosine similarity + top-K поиск |
| LLM Chat | `OpenRouterChatClient.kt` | Запросы к OpenRouter API (`openai/gpt-4o-mini`) |
| RAG Pipeline | `RagPipeline.kt` | Оркестрация: вопрос → поиск чанков → LLM |
| Data | `EmbeddingRecord.kt` | Модели данных для эмбеддингов |

## Структура `embeddings.json`

- `sourceFile` — имя исходного `.text` файла
- `chunkIndex` — индекс чанка
- `text` — текст чанка
- `embedding` — вектор эмбеддинга (массив чисел)
