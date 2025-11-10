package eu.legislation.formex.ingester

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class XmlDatabaseWriterTest {

    @Autowired
    private lateinit var xmlDatabaseWriter: XmlDatabaseWriter

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @TempDir
    lateinit var tempDir: Path

    @AfterEach
    fun cleanup() {
        // Clear test data from database
        jdbcTemplate.execute("DELETE FROM formex_documents")
    }

    @Test
    fun `should write multiple items in batch and verify all records`() {
        // Given: Create 2 test items with XML files
        val metadata1 = createTestXmlMetadata(
            xmlContent = "<doc1>Content 1</doc1>",
            uri = "http://example.org/doc1",
            fileSize = 100L,
            workId = UUID.randomUUID(),
            exprId = "expr-1",
            manId = "man-1",
            csId = "cs-1"
        )

        val metadata2 = createTestXmlMetadata(
            xmlContent = "<doc2>Content 2</doc2>",
            uri = "http://example.org/doc2",
            fileSize = 200L,
            workId = UUID.randomUUID(),
            exprId = "expr-2",
            manId = "man-2",
            csId = "cs-2"
        )

        val chunk = Chunk(listOf(metadata1, metadata2))

        // When: Write batch to database
        xmlDatabaseWriter.write(chunk)

        // Then: Verify both records exist in database
        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM formex_documents", Int::class.java)
        Assertions.assertEquals(2, count, "Should have 2 records in database")

        // Verify first record
        val record1 = jdbcTemplate.queryForMap(
            "SELECT * FROM formex_documents WHERE id = ?",
            metadata1.id.toString()
        )
        Assertions.assertEquals(metadata1.id.toString(), record1["id"].toString())
        Assertions.assertEquals(metadata1.filePath, record1["file_path"])
        Assertions.assertEquals(100L, (record1["file_size"] as Number).toLong())
        Assertions.assertEquals("http://example.org/doc1", record1["uri"])
        Assertions.assertEquals(metadata1.workId.toString(), record1["work_id"].toString())
        Assertions.assertEquals("expr-1", record1["expr_id"])
        Assertions.assertEquals("man-1", record1["man_id"])
        Assertions.assertEquals("cs-1", record1["cs_id"])

        // Verify second record
        val record2 = jdbcTemplate.queryForMap(
            "SELECT * FROM formex_documents WHERE id = ?",
            metadata2.id.toString()
        )
        Assertions.assertEquals(metadata2.id.toString(), record2["id"].toString())
        Assertions.assertEquals(metadata2.filePath, record2["file_path"])
        Assertions.assertEquals(200L, (record2["file_size"] as Number).toLong())
        Assertions.assertEquals("http://example.org/doc2", record2["uri"])
        Assertions.assertEquals(metadata2.workId.toString(), record2["work_id"].toString())
        Assertions.assertEquals("expr-2", record2["expr_id"])
        Assertions.assertEquals("man-2", record2["man_id"])
        Assertions.assertEquals("cs-2", record2["cs_id"])
    }

    @Test
    fun `should store file path in database`() {
        // Given: Create file with specific XML content
        val expectedXmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <FORMEX_PLUS>
                <HEADER>Test Header</HEADER>
                <BODY>
                    <SECTION>Test Section Content</SECTION>
                </BODY>
            </FORMEX_PLUS>
        """.trimIndent()

        val metadata = createTestXmlMetadata(
            xmlContent = expectedXmlContent,
            uri = "http://example.org/test-xml",
            fileSize = expectedXmlContent.length.toLong(),
            workId = UUID.randomUUID(),
            exprId = "test-expr",
            manId = "test-man",
            csId = "test-cs"
        )

        val chunk = Chunk(listOf(metadata))

        // When: Write to database
        xmlDatabaseWriter.write(chunk)

        // Then: Verify file path is stored in database
        val storedFilePath = jdbcTemplate.queryForObject(
            "SELECT file_path FROM formex_documents WHERE id = ?",
            String::class.java,
            metadata.id.toString()
        )

        Assertions.assertEquals(metadata.filePath, storedFilePath, "File path should be stored")
        
        // Verify file exists and content matches
        val fileContent = Files.readString(Paths.get(storedFilePath))
        Assertions.assertEquals(expectedXmlContent, fileContent, "File content should match")

        // Verify file size matches
        val actualFileSize = jdbcTemplate.queryForObject(
            "SELECT file_size FROM formex_documents WHERE id = ?",
            Long::class.java,
            metadata.id.toString()
        )
        Assertions.assertEquals(
            expectedXmlContent.length.toLong(),
            actualFileSize,
            "File size should match XML content length"
        )
    }

    /**
     * Helper method to create test XmlMetadata with an XML file.
     * Uses JUnit's @TempDir for automatic cleanup.
     *
     * @param xmlContent The XML content to write to the file
     * @param uri The URI for the metadata
     * @param fileSize The file size
     * @param workId Work UUID (required)
     * @param exprId Expression ID (required)
     * @param manId Manifestation ID (required)
     * @param csId Content Stream ID (required)
     * @return XmlMetadata with filePath pointing to created file
     */
    private fun createTestXmlMetadata(
        xmlContent: String,
        uri: String,
        fileSize: Long,
        workId: UUID,
        exprId: String,
        manId: String,
        csId: String
    ): XmlMetadata {
        // Create file with XML content in JUnit's managed temp directory
        val file = Files.createTempFile(tempDir, "test-xml-", ".xml")
        Files.writeString(file, xmlContent)

        return XmlMetadata(
            id = UUID.randomUUID(),
            uri = uri,
            fileSize = fileSize,
            workId = workId,
            exprId = exprId,
            manId = manId,
            csId = csId,
            filePath = file.toString()
        )
    }
}