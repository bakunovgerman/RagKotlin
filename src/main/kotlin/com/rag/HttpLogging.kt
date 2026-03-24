package com.rag

import io.ktor.client.*
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.logging.*

var httpLoggingEnabled = true

private object HttpLogger : Logger {
    override fun log(message: String) {
        println("[HTTP] $message")
    }
}

fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installHttpLogging() {
    if (!httpLoggingEnabled) return
    install(Logging) {
        logger = HttpLogger
        level = LogLevel.ALL
    }
}
