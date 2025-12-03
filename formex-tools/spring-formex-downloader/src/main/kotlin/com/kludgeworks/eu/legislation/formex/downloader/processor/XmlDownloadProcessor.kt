package com.kludgeworks.eu.legislation.formex.downloader.processor

import com.github.f4b6a3.uuid.UuidCreator
import com.kludgeworks.eu.legislation.formex.downloader.config.StorageProperties
import com.kludgeworks.eu.legislation.formex.downloader.domain.CellarData
import com.kludgeworks.eu.legislation.formex.downloader.domain.XmlMetadata
import com.kludgeworks.eu.legislation.formex.downloader.util.CellarUriParser
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Exception thrown when a downloaded file is empty (0 bytes).
 * This exception is retryable by Spring Batch, allowing the download to be attempted again.
 */
class EmptyFileDownloadException(message: String) : Exception(message)

/**
 * Exception thrown when HTTP request fails with non-success status code.
 * This exception is retryable by Spring Batch for transient errors (5xx),
 * but not for permanent errors (4xx) which should be skipped immediately.
 */
class HttpDownloadException(
    val statusCode: Int,
    message: String,
) : Exception(message)

/**
 * Camel processor that downloads XML files from URIs and creates metadata records.
 *
 * Downloads XML files using streaming to handle large files efficiently,
 * computes SHA-1 hash during download (single-pass), and generates UUIDv5 identifiers.
 * IDs are supplied by the SPARQL query so no regex parsing is required.
 *
 * Files are stored in a hierarchical directory structure based on the URI path:
 *   {storageDir}/publications.europa.eu/resource/cellar/{manifestation}/{docId}.xml
 *
 * Example:
 *   Input:  http://publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1
 *   Stored: ./formex-xml-files/publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1.xml
 */
@Component
class XmlDownloadProcessor(
    private val xmlDownloadRestClient: RestClient,
    private val storageProperties: StorageProperties,
) : Processor {
    private val storageDir: Path =
        Paths.get(storageProperties.directory).also {
            Files.createDirectories(it)
        }

    companion object {
        // Create a UUID namespace for Cellar using UUIDv5 based on the URL namespace
        private val CELLAR_NAMESPACE =
            UuidCreator.getNameBasedSha1(
                UuidCreator.NAMESPACE_URL,
                "http://publications.europa.eu/resource/cellar",
            )

        // Standard buffer size for I/O operations (8KB)
        private const val BUFFER_SIZE = 8192
    }

    override fun process(exchange: Exchange) {
        val item: CellarData = exchange.`in`.getBody(CellarData::class.java)

        logger.info { "Download: Starting download in thread ${Thread.currentThread().name} for URI: ${item.item}" }

        val metadata =
            run {

                val parsedUri = CellarUriParser.parse(item.item)
                val relativePath = parsedUri.toRelativePath()

                val permanentFile = storageDir.resolve(relativePath)
                val parentDir = permanentFile.parent
                Files.createDirectories(parentDir)

                val tempFile = permanentFile.parent.resolve("${UUID.randomUUID()}.xml")
                val metrics = downloadWithMetrics(item.item, tempFile)

                if (metrics.size == 0L) {
                    Files.deleteIfExists(tempFile)
                    throw EmptyFileDownloadException("Downloaded file is empty (0 bytes) for URI: ${item.item}")
                }

                logger.info {
                    "Download: Completed download - ID=${metrics.id}, URI=${item.item}, " +
                        "Size=${metrics.size}, TempFile=$tempFile"
                }

                Files.move(tempFile, permanentFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)

                val result =
                    XmlMetadata(
                        id = metrics.id,
                        uri = item.item,
                        fileSize = metrics.size,
                        workId = UUID.fromString(item.workId),
                        exprId = item.expressionId,
                        manId = item.manifestationId,
                        csId = item.contentStreamId,
                        filePath = relativePath,
                    )

                logger.info {
                    "Download: Created metadata - ID=${result.id}, URI=${result.uri}, " +
                        "FilePath=${result.filePath}, Thread=${Thread.currentThread().name}"
                }
                result
            }

        exchange.message.body = metadata
    }

    private fun downloadWithMetrics(
        uri: String,
        destination: Path,
    ): FileMetrics {
        val digest = MessageDigest.getInstance("SHA-1")
        var size = 0L

        xmlDownloadRestClient
            .get()
            .uri(uri)
            .exchange { _, response ->
                // Check HTTP status code before processing response
                val statusCode = response.statusCode.value()
                if (statusCode !in 200..299) {
                    throw HttpDownloadException(
                        statusCode,
                        "HTTP $statusCode error for URI: $uri",
                    )
                }

                response.body.use { input: InputStream ->
                    DigestInputStream(input, digest).use { digestInput ->
                        Files.newOutputStream(destination).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytesRead: Int
                            while (digestInput.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                size += bytesRead
                            }
                        }
                    }
                }
                null
            }

        // Generate UUIDv5 from file content hash
        val hash = digest.digest()
        val id = UuidCreator.getNameBasedSha1(CELLAR_NAMESPACE, hash)

        return FileMetrics(id, size)
    }

    private data class FileMetrics(
        val id: UUID,
        val size: Long,
    )
}
