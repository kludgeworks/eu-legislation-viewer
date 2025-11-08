package eu.legislation.formex.ingester.processor

import com.github.f4b6a3.uuid.UuidCreator
import eu.legislation.formex.ingester.CellarData
import eu.legislation.formex.ingester.XmlMetadata
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Spring Batch ItemProcessor that downloads XML files from URIs and creates metadata records.
 *
 * Downloads XML files using streaming (64KB buffer) to handle large files efficiently,
 * computes SHA-1 hash during download (single-pass), and generates UUIDv5 identifiers.
 * All ID components (workId, expressionId, etc.) are now provided directly by the
 * CellarData from the SPARQL query, eliminating regex parsing.
 */
@Component
class StreamingXmlDownloadProcessor(
    private val restClient: RestClient
) : ItemProcessor<CellarData, XmlMetadata> {

    private val tempDir: Path = Files.createTempDirectory("formex-xml-")

    companion object {
        private const val CELLAR_NAMESPACE = "http://publications.europa.eu/resource/cellar"
        private const val BUFFER_SIZE = 65536 // 64KB buffer for large files
    }

    override fun process(item: CellarData): XmlMetadata {
        // Download and hash in single pass
        val tempFile = tempDir.resolve("${UUID.randomUUID()}.xml")
        val metrics = downloadWithMetrics(item.item, tempFile)

        return XmlMetadata(
            id = metrics.id,
            uri = item.item,
            fileSize = metrics.size,
            workId = UUID.fromString(item.workId),
            exprId = item.expressionId,
            manId = item.manifestationId,
            csId = item.contentStreamId,
            tempFilePath = tempFile.toString()
        )
    }

    private fun downloadWithMetrics(uri: String, destination: Path): FileMetrics {
        val digest = MessageDigest.getInstance("SHA-1")
        var size = 0L

        restClient.get()
            .uri(uri)
            .exchange { _, response ->
                response.body.use { input: InputStream ->
                    DigestInputStream(input, digest).use { digestInput ->
                        Files.newOutputStream(destination).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytesRead = digestInput.read(buffer)
                            while (bytesRead != -1) {
                                output.write(buffer, 0, bytesRead)
                                size += bytesRead
                                bytesRead = digestInput.read(buffer)
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

    private data class FileMetrics(val id: UUID, val size: Long)
}
