package eu.legislation.formex.ingester.processor

import com.github.f4b6a3.uuid.UuidCreator
import eu.legislation.formex.ingester.CellarData
import eu.legislation.formex.ingester.StorageProperties
import eu.legislation.formex.ingester.XmlMetadata
import org.apache.commons.io.IOUtils
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Spring Batch ItemProcessor that downloads XML files from URIs and creates metadata records.
 *
 * Downloads XML files using streaming to handle large files efficiently,
 * computes SHA-1 hash during download (single-pass), and generates UUIDv5 identifiers.
 * All ID components (workId, expressionId, etc.) are now provided directly by the
 * CellarData from the SPARQL query, eliminating regex parsing.
 *
 * Files are stored permanently in the configured storage directory.
 */
@Component
class StreamingXmlDownloadProcessor(
    private val restClient: RestClient,
    private val storageProperties: StorageProperties,
) : ItemProcessor<CellarData, XmlMetadata> {
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
    }

    override fun process(item: CellarData): XmlMetadata {
        // Download and hash in single pass, generate ID based on content
        val tempFile = storageDir.resolve("${UUID.randomUUID()}.xml")
        val metrics = downloadWithMetrics(item.item, tempFile)

        // Rename file to use content-based ID for deduplication
        val permanentFile = storageDir.resolve("${metrics.id}.xml")
        Files.move(tempFile, permanentFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)

        return XmlMetadata(
            id = metrics.id,
            uri = item.item,
            fileSize = metrics.size,
            workId = UUID.fromString(item.workId),
            exprId = item.expressionId,
            manId = item.manifestationId,
            csId = item.contentStreamId,
            filePath = permanentFile.toString(),
        )
    }

    private fun downloadWithMetrics(
        uri: String,
        destination: Path,
    ): FileMetrics {
        val digest = MessageDigest.getInstance("SHA-1")
        var size = 0L

        restClient
            .get()
            .uri(uri)
            .exchange { _, response ->
                response.body.use { input: InputStream ->
                    DigestInputStream(input, digest).use { digestInput ->
                        Files.newOutputStream(destination).use { output ->
                            size = IOUtils.copyLarge(digestInput, output)
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
