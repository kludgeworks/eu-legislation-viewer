package com.kludgeworks.eu.legislation.shell.service

import arrow.core.raise.Raise
import com.kludgeworks.eu.legislation.util.DomainError
import com.kludgeworks.eu.legislation.util.catchingUnexpectedSuspend
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.outputStream
import arrow.core.raise.ensure

/**
 * Thin wrapper around Ktor's HttpClient with Raise API for error handling.
 *
 * This bean is a singleton (Spring @Component), so the expensive HttpClient
 * instance is reused application-wide.
 */
@Component
class KtorClient : Closeable {

    private val logger = LoggerFactory.getLogger(KtorClient::class.java)

    /** Single shared client instance with sane defaults */
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json() }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            socketTimeoutMillis  = 30_000
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }

        install(Logging) { level = LogLevel.INFO }
    }

    /**
     * GET request that returns the body as text.
     *
     * @param url The URL to get content from
     * @param headers Optional map of headers to include in the request
     * @return The response body as text
     */
    context(Raise<DomainError>)
    suspend fun get(url: String,
                    headers: Map<String, String> = emptyMap()
    ): String {
        return catchingUnexpectedSuspend("HTTP GET failed") {
            val resp = client.get(url) {
                headers { 
                    headers.forEach { (key, value) -> append(key, value) }
                }
            }
            ensure(resp.status.isSuccess()) {
                DomainError.UnexpectedError(
                    "HTTP ${resp.status.value} ${resp.status.description}"
                )
            }
            resp.bodyAsText()
        }
    }

    /**
     * Streams the response body to [outputPath].
     *
     * @param url The URL to download from
     * @param outputPath The path to save the downloaded content to
     * @param headers Optional map of headers to include in the request
     * @return The path to the downloaded file
     */
    context(Raise<DomainError>)
    suspend fun download(url: String,
                         outputPath: Path,
                         headers: Map<String, String> = emptyMap()
    ): Path {
        return catchingUnexpectedSuspend("Download failed for $url") {
            withContext(Dispatchers.IO) {
                Files.createDirectories(outputPath.parent)
                val resp = client.get(url) {
                    headers { 
                        headers.forEach { (key, value) -> append(key, value) }
                    }
                }
                ensure(resp.status.isSuccess()) {
                    DomainError.UnexpectedError(
                        "HTTP ${resp.status.value} ${resp.status.description}"
                    )
                }
                save(resp, outputPath)
            }
            outputPath
        }
    }

    private suspend fun save(resp: HttpResponse, target: Path) =
        withContext(Dispatchers.IO) {
            resp.bodyAsChannel()
                .toInputStream()
                .copyTo(target.outputStream())
        }

    /** Called automatically on JVM shutdown by Spring; safe to call manually. */
    override fun close() = client.close()
}
