package com.kludgeworks.eu.legislation.shell.service

import arrow.core.Either
import arrow.core.getOrElse
import com.kludgeworks.eu.legislation.Legislation32015R1998.DOC1_CONTENT_ITEM
import com.kludgeworks.eu.legislation.shell.util.XmlFormatter
import com.kludgeworks.eu.legislation.util.Result
import com.kludgeworks.eu.legislation.util.asSuccess
import kotlinx.coroutines.runBlocking
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
    private lateinit var mockXmlFormatter: XmlFormatter
    private lateinit var mockKtorClient: KtorClient

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        mockXmlFormatter = mock()
        mockKtorClient = mock()
        whenever(mockXmlFormatter.formatXmlFile(any())).thenReturn(true)
        downloadService = DownloadService(mockXmlFormatter, mockKtorClient)
    }

    @Test
    fun downloadContentShouldDownloadFile() = runBlocking {
        // Arrange
        val outputPath = tempDir.resolve("doc1.xml")
        whenever(mockKtorClient.download(any(), any(), any())).thenReturn(outputPath.asSuccess())

        println("Starting download from ${DOC1_CONTENT_ITEM.uri}")

        // Act - Use the actual EU Cellar URL
        val result = downloadService.downloadContent(DOC1_CONTENT_ITEM.uri, outputPath)

        // Assert
        val path = result.getOrElse { 
            throw AssertionError("Download failed with error: ${it.message}")
        }
        assertEquals(outputPath, path)
    }

    @Test
    fun downloadContentShouldFormatXmlFile() = runBlocking {
        // Arrange
        val xmlContent = """<?xml version="1.0" encoding="UTF-8"?><root><child>value</child></root>"""
        val xmlFilePath = tempDir.resolve("source.xml")
        Files.writeString(xmlFilePath, xmlContent)

        val outputPath = tempDir.resolve("formatted.xml")
        whenever(mockKtorClient.download(any(), any(), any())).thenReturn(outputPath.asSuccess())
        Files.writeString(outputPath, xmlContent) // Simulate download

        // Act - Use file URL for the test
        val result = downloadService.downloadContent(xmlFilePath.toUri().toString(), outputPath)

        // Assert
        val path = result.getOrElse {
            throw AssertionError("Download failed with error: ${it.message}")
        }
        assertEquals(outputPath, path)
        // Verify that formatXmlFile was called on the mock
        org.mockito.kotlin.verify(mockXmlFormatter).formatXmlFile(outputPath.toFile())
    }

    @Test
    @Disabled("Zip test disabled as requested")
    fun downloadContentShouldUnzipZipFile() = runBlocking {
        // Arrange
        val zipData = createZipData()
        val zipFilePath = tempDir.resolve("source.zip")
        Files.write(zipFilePath, zipData)

        val outputPath = tempDir.resolve("test.zip")
        whenever(mockKtorClient.download(any(), any(), any())).thenReturn(outputPath.asSuccess())
        Files.write(outputPath, zipData) // Simulate download

        // Act - Use file URL for the test
        val result = downloadService.downloadContent(zipFilePath.toUri().toString(), outputPath)

        // Assert
        val path = result.getOrElse {
            throw AssertionError("Download failed with error: ${it.message}")
        }
        assertEquals(tempDir.resolve("test"), path) // Result should be the unzipped directory
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