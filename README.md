# RagKotlin

Приложение на Kotlin и Ktor для построения эмбеддингов из набора `.text` файлов: чтение текстов, разбивка на чанки (500–1000 токенов, перекрытие 50–100 токенов), получение эмбеддингов через Ollama (модель `nomic-embed-text`) и сохранение результата в JSON.

## Требования

- JDK 17+
- [Ollama](https://ollama.com) с установленной моделью `nomic-embed-text`

## Установка Ollama и модели

### macOS / Linux

1. Скачайте и установите Ollama с официального сайта:
   - **macOS:** https://ollama.com/download — скачайте установщик и откройте его, либо в терминале:
   ```bash
   curl -fsSL https://ollama.com/install.sh | sh
   ```
   - **Linux:** тот же скрипт или пакет из репозитория (см. [документацию](https://github.com/ollama/ollama/blob/main/docs/linux.md)).

2. Запустите Ollama (на macOS после установки она обычно уже запущена):
   ```bash
   ollama serve
   ```
   По умолчанию сервер слушает `http://localhost:11434`.

3. Установите модель эмбеддингов:
   ```bash
   ollama pull nomic-embed-text
   ```

4. Проверка:
   ```bash
   curl http://localhost:11434/api/embed -d '{"model":"nomic-embed-text","input":"test"}'
   ```
   Должен вернуться JSON с полем `embeddings`.

### Windows

1. Скачайте установщик с https://ollama.com/download и установите Ollama.
2. Откройте терминал и выполните:
   ```bash
   ollama pull nomic-embed-text
   ```

## Запуск приложения

1. Положите 3 (или любое количество) `.text` файлов в папку `texts/` в корне проекта.
2. Убедитесь, что Ollama запущена и модель `nomic-embed-text` скачана.
3. Соберите и запустите (нужен установленный [Gradle](https://gradle.org/install/) или сгенерируйте wrapper: `gradle wrapper`):
   ```bash
   ./gradlew run
   ```
   Или, если Gradle установлен глобально:
   ```bash
   gradle run
   ```
   Только сборка:
   ```bash
   ./gradlew build
   ```

4. Результат будет записан в файл `embeddings.json` в корне проекта.

## Структура вывода

В `embeddings.json` сохраняется объект с массивом записей вида:

- `sourceFile` — имя исходного `.text` файла
- `chunkIndex` — индекс чанка
- `text` — текст чанка
- `embedding` — вектор эмбеддинга (массив чисел)

Эти данные можно использовать для поиска по сходству или как контекст в RAG-пайплайне.
