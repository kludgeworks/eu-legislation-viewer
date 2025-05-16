package com.kludgeworks.eu.legislation.shell.service

import com.kludgeworks.eu.legislation.Legislation32015R1998.DOC1_CONTENT_ITEM
import com.kludgeworks.eu.legislation.shell.util.XmlFormatter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadServiceTest {

    private lateinit var downloadService: DownloadService

    @TempDir
    lateinit var tempDir: Path

    private lateinit var mockXmlFormatter: XmlFormatter

    @BeforeEach
    fun setUp() {
        mockXmlFormatter = mock()
        whenever(mockXmlFormatter.formatXmlFile(any())).thenReturn(true)
        downloadService = DownloadService(mockXmlFormatter)
    }

    @Test
    fun downloadContentShouldDownloadFile() {
        // Arrange
        val outputPath = tempDir.resolve("doc1.xml")

        println("Starting download from ${DOC1_CONTENT_ITEM.uri}")

        // Act - Use the actual EU Cellar URL
        val result = downloadService.downloadContent(DOC1_CONTENT_ITEM.uri, outputPath)

        // Assert
        assertEquals(outputPath, result)
        assertTrue(Files.exists(outputPath))
        val fileSize = Files.size(outputPath)
        println("Downloaded file size: $fileSize bytes")
        assertTrue(fileSize > 0, "Downloaded file should not be empty")

        // Log the first 100 chars of content to verify it's XML content
        val content = Files.readString(outputPath)
        println("Content preview (first 100 chars): ${content.take(100).replace("\n", "\\n")}")
    }

    @Test
    fun downloadContentShouldFormatXmlFile() {
        // Arrange
        val xmlContent = """<?xml version="1.0" encoding="UTF-8"?><root><child>value</child></root>"""
        val xmlFilePath = tempDir.resolve("source.xml")
        Files.writeString(xmlFilePath, xmlContent)

        val outputPath = tempDir.resolve("formatted.xml")

        // Act - Use file URL for the test
        val result = downloadService.downloadContent(xmlFilePath.toUri().toString(), outputPath)

        // Assert
        assertEquals(outputPath, result)
        assertTrue(Files.exists(outputPath))
        // Verify that formatXmlFile was called on the mock
        org.mockito.kotlin.verify(mockXmlFormatter).formatXmlFile(outputPath.toFile())
    }

    @Test
    @Disabled("Zip test disabled as requested")
    fun downloadContentShouldUnzipZipFile() {
        // Arrange
        val zipData = createZipData()
        val zipFilePath = tempDir.resolve("source.zip")
        Files.write(zipFilePath, zipData)

        val outputPath = tempDir.resolve("test.zip")

        // Act - Use file URL for the test
        val result = downloadService.downloadContent(zipFilePath.toUri().toString(), outputPath)

        // Assert
        assertTrue(Files.exists(outputPath)) // Original zip file should exist
        assertTrue(Files.exists(tempDir.resolve("test"))) // Unzipped directory should exist
        assertTrue(Files.exists(tempDir.resolve("test/file1.txt"))) // Unzipped file should exist
        assertTrue(Files.exists(tempDir.resolve("test/dir/file2.txt"))) // Nested unzipped file should exist
        assertEquals("File 1 content", Files.readString(tempDir.resolve("test/file1.txt")))
        assertEquals("File 2 content", Files.readString(tempDir.resolve("test/dir/file2.txt")))
        assertEquals(tempDir.resolve("test"), result) // Result should be the unzipped directory
    }

    private fun createZipData(): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // Add file1.txt
            val entry1 = ZipEntry("file1.txt")
            zos.putNextEntry(entry1)
            val file1Content = "File 1 content".toByteArray()
            zos.write(file1Content, 0, file1Content.size)
            zos.closeEntry()

            // Create directory
            val dirEntry = ZipEntry("dir/")
            zos.putNextEntry(dirEntry)
            zos.closeEntry()

            // Add file2.txt in directory
            val entry2 = ZipEntry("dir/file2.txt")
            zos.putNextEntry(entry2)
            val file2Content = "File 2 content".toByteArray()
            zos.write(file2Content, 0, file2Content.size)
            zos.closeEntry()
        }
        return baos.toByteArray()
    }
}
