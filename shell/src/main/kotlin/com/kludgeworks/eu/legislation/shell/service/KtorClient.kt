package com.kludgeworks.eu.legislation.shell.service

import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kludgeworks.eu.legislation.util.DomainError
import com.kludgeworks.eu.legislation.util.Result
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

/**
 * Thin wrapper around Ktor’s HttpClient with Arrow-powered error handling.
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
        }                                          // :contentReference[oaicite:1]{index=1}

        install(Logging) { level = LogLevel.INFO }
    }

    /**
     * GET request that returns the body as text.
     */
    suspend fun get(url: String,
                    headers: Map<String, String> = emptyMap()
    ): Result<String> = either {
        catch({
            val resp = client.get(url) {
                headers { headers.forEach(::append) }
            }
            ensure(resp.status.isSuccess()) {
                DomainError.UnexpectedError(
                    "HTTP ${resp.status.value} ${resp.status.description}"
                )
            }
            resp.bodyAsText()
        }) { e ->
            logger.error("GET $url failed", e)
            raise(DomainError.UnexpectedError("HTTP GET failed: ${e.message}", e))
        }
    }

    /**
     * Streams the response body to [outputPath].
     */
    suspend fun download(url: String,
                         outputPath: Path,
                         headers: Map<String, String> = emptyMap()
    ): Result<Path> = either {
        catch({
            withContext(Dispatchers.IO) {
                Files.createDirectories(outputPath.parent)
                val resp = client.get(url) {
                    headers { headers.forEach(::append) }
                }
                ensure(resp.status.isSuccess()) {
                    DomainError.UnexpectedError(
                        "HTTP ${resp.status.value} ${resp.status.description}"
                    )
                }
                save(resp, outputPath)
            }
            outputPath
        }) { e ->
            logger.error("Download $url → $outputPath failed", e)
            raise(DomainError.UnexpectedError("Download failed: ${e.message}", e))
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
