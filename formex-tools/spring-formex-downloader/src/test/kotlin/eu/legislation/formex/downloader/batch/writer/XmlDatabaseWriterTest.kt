package eu.legislation.formex.downloader.batch.writer

import eu.legislation.formex.downloader.batch.XmlDatabaseWriter
import eu.legislation.formex.downloader.config.StorageProperties
import eu.legislation.formex.downloader.domain.model.XmlMetadata
import eu.legislation.formex.downloader.domain.table.FormexDocuments
import eu.legislation.formex.downloader.support.TempDirsPropertySupport
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class XmlDatabaseWriterTest : TempDirsPropertySupport() {
    @Autowired
    private lateinit var xmlDatabaseWriter: XmlDatabaseWriter

    @Autowired
    private lateinit var database: Database

    @Autowired
    private lateinit var storageProperties: StorageProperties

    @BeforeEach
    fun setup() {
        // Clear test data from database before each test using Exposed
        transaction(database) {
            FormexDocuments.deleteAll()
        }
    }

    @AfterEach
    fun cleanup() {
        // Clear test data from database after each test using Exposed
        transaction(database) {
            FormexDocuments.deleteAll()
        }
    }

    @Test
    fun `should write multiple items in batch and verify all records`() {
        // Given: Create 2 test items with XML files
        val metadata1 =
            createTestXmlMetadata(
                xmlContent = "<doc1>Content 1</doc1>",
                uri = "http://example.org/doc1",
                fileSize = 100L,
                workId = UUID.randomUUID(),
                exprId = "expr-1",
                manId = "man-1",
                csId = "cs-1",
            )

        val metadata2 =
            createTestXmlMetadata(
                xmlContent = "<doc2>Content 2</doc2>",
                uri = "http://example.org/doc2",
                fileSize = 200L,
                workId = UUID.randomUUID(),
                exprId = "expr-2",
                manId = "man-2",
                csId = "cs-2",
            )

        val chunk = Chunk(listOf(metadata1, metadata2))

        // When: Write batch to database
        xmlDatabaseWriter.write(chunk)

        // Then: Verify both records exist in database using Exposed
        val count =
            transaction(database) {
                FormexDocuments.selectAll().count()
            }
        Assertions.assertEquals(2, count, "Should have 2 records in database")

        // Verify first record using Exposed
        val record1 =
            transaction(database) {
                FormexDocuments
                    .selectAll()
                    .where { FormexDocuments.id eq metadata1.id.toString() }
                    .single()
            }
        Assertions.assertEquals(metadata1.id.toString(), record1[FormexDocuments.id])
        Assertions.assertEquals(metadata1.filePath, record1[FormexDocuments.filePath])
        Assertions.assertEquals(100, record1[FormexDocuments.fileSize])
        Assertions.assertEquals("http://example.org/doc1", record1[FormexDocuments.uri])
        Assertions.assertEquals(metadata1.workId.toString(), record1[FormexDocuments.workId])
        Assertions.assertEquals("expr-1", record1[FormexDocuments.exprId])
        Assertions.assertEquals("man-1", record1[FormexDocuments.manId])
        Assertions.assertEquals("cs-1", record1[FormexDocuments.csId])

        // Verify second record using Exposed
        val record2 =
            transaction(database) {
                FormexDocuments
                    .selectAll()
                    .where { FormexDocuments.id eq metadata2.id.toString() }
                    .single()
            }
        Assertions.assertEquals(metadata2.id.toString(), record2[FormexDocuments.id])
        Assertions.assertEquals(metadata2.filePath, record2[FormexDocuments.filePath])
        Assertions.assertEquals(200, record2[FormexDocuments.fileSize])
        Assertions.assertEquals("http://example.org/doc2", record2[FormexDocuments.uri])
        Assertions.assertEquals(metadata2.workId.toString(), record2[FormexDocuments.workId])
        Assertions.assertEquals("expr-2", record2[FormexDocuments.exprId])
        Assertions.assertEquals("man-2", record2[FormexDocuments.manId])
        Assertions.assertEquals("cs-2", record2[FormexDocuments.csId])
    }

    @Test
    fun `should store file path in database`() {
        // Given: Create file with specific XML content
        val expectedXmlContent =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <FORMEX_PLUS>
                <HEADER>Test Header</HEADER>
                <BODY>
                    <SECTION>Test Section Content</SECTION>
                </BODY>
            </FORMEX_PLUS>
            """.trimIndent()

        val metadata =
            createTestXmlMetadata(
                xmlContent = expectedXmlContent,
                uri = "http://example.org/test-xml",
                fileSize = expectedXmlContent.length.toLong(),
                workId = UUID.randomUUID(),
                exprId = "test-expr",
                manId = "test-man",
                csId = "test-cs",
            )

        val chunk = Chunk(listOf(metadata))
        val storageDir = Paths.get(storageProperties.directory)

        // When: Write to database
        xmlDatabaseWriter.write(chunk)

        // Then: Verify file path is stored in database using Exposed
        val storedFilePath =
            transaction(database) {
                FormexDocuments
                    .selectAll()
                    .where { FormexDocuments.id eq metadata.id.toString() }
                    .single()[FormexDocuments.filePath]
            }

        Assertions.assertEquals(metadata.filePath, storedFilePath, "File path should be stored")

        // Verify file exists and content matches
        val fileContent = Files.readString(storageDir.resolve(storedFilePath))
        Assertions.assertEquals(expectedXmlContent, fileContent, "File content should match")

        // Verify file size matches using Exposed
        val actualFileSize =
            transaction(database) {
                FormexDocuments
                    .selectAll()
                    .where { FormexDocuments.id eq metadata.id.toString() }
                    .single()[FormexDocuments.fileSize]
            }
        Assertions.assertEquals(
            expectedXmlContent.length,
            actualFileSize,
            "File size should match XML content length",
        )
    }

    /**
     * Helper method to create test XmlMetadata with actual file.
     */
    @Suppress("LongParameterList") // Test helper function with all required metadata fields
    private fun createTestXmlMetadata(
        xmlContent: String,
        uri: String,
        fileSize: Long,
        workId: UUID,
        exprId: String,
        manId: String,
        csId: String,
    ): XmlMetadata {
        val storageDir = Paths.get(storageProperties.directory)
        Files.createDirectories(storageDir)
        val relativePath = "test-xml-${UUID.randomUUID()}.xml"
        val file = storageDir.resolve(relativePath)
        Files.writeString(file, xmlContent)

        return XmlMetadata(
            id = UUID.randomUUID(),
            uri = uri,
            fileSize = fileSize,
            workId = workId,
            exprId = exprId,
            manId = manId,
            csId = csId,
            filePath = relativePath,
        )
    }
}
