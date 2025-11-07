package eu.legislation.formex.ingester.processor

import com.github.f4b6a3.uuid.UuidCreator
import eu.legislation.formex.ingester.model.SparqlUri
import eu.legislation.formex.ingester.model.XmlMetadata
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.regex.Pattern

@Component
class StreamingXmlDownloadProcessor(
    private val restTemplate: RestTemplate,
    @Value("\${temp.dir}") tempDirPath: String
) : ItemProcessor<SparqlUri, XmlMetadata> {

    private val tempDir: Path = Files.createDirectories(Paths.get(tempDirPath))

    companion object {
        private const val CELLAR_NAMESPACE = "http://publications.europa.eu/resource/cellar"
        private const val BUFFER_SIZE = 65536 // 64KB buffer for large files
    }

    override fun process(item: SparqlUri): XmlMetadata {
        val uri = item.uri

        // Extract ID components from URI
        val components = extractIdComponents(uri)

        // Download and hash in single pass
        val tempFile = tempDir.resolve("${UUID.randomUUID()}.xml")
        val metrics = downloadWithMetrics(uri, tempFile)

        return XmlMetadata(
            id = metrics.id,
            uri = uri,
            fileSize = metrics.size,
            workId = components.workId,
            exprId = components.exprId,
            manId = components.manId,
            csId = components.csId,
            tempFilePath = tempFile.toString()
        )
    }

    private fun extractIdComponents(uri: String): IdComponents {
        // Pattern: .../work_id.expr_id.man_id/cs_id
        val pattern = Pattern.compile(".*/([^/]+)\\.([^.]+)\\.([^/]+)/(.+)$")
        val matcher = pattern.matcher(uri)

        return if (matcher.find()) {
            IdComponents(
                workId = UUID.fromString(matcher.group(1)),
                exprId = matcher.group(2),
                manId = matcher.group(3),
                csId = matcher.group(4)
            )
        } else {
            IdComponents(null, null, null, null)
        }
    }

    private fun downloadWithMetrics(uri: String, destination: Path): FileMetrics {
        val digest = MessageDigest.getInstance("SHA-1")
        var size = 0L

        restTemplate.execute(java.net.URI(uri), HttpMethod.GET, null, { response ->
            response.body.use { input: InputStream ->
                DigestInputStream(input, digest).use { digestInput: DigestInputStream ->
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
        })

        // Generate UUIDv5 from file content hash
        val hash = digest.digest()
        val id = UuidCreator.getNameBasedSha1(CELLAR_NAMESPACE, hash)

        return FileMetrics(id, size)
    }

    private data class IdComponents(
        val workId: UUID?,
        val exprId: String?,
        val manId: String?,
        val csId: String?
    )

    private data class FileMetrics(val id: UUID, val size: Long)
}
