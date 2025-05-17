package com.kludgeworks.eu.legislation.shell.service

import arrow.core.getOrElse
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class KtorClientTest {

    private val ktorClient = KtorClient()

    @TempDir
    lateinit var tempDir: Path

    @AfterEach
    fun tearDown() {
        ktorClient.close()
    }

    @Test
    fun `should download file from a URL`() = runBlocking {
        // Arrange
        val outputPath = tempDir.resolve("test-download.txt")
        
        // Act - Use a reliable test URL
        val result = ktorClient.download(
            "https://raw.githubusercontent.com/ktorio/ktor/main/README.md", 
            outputPath
        )
        
        // Assert
        val path = result.getOrElse { 
            throw AssertionError("Download failed with error: ${it.message}") 
        }
        
        assertEquals(outputPath, path)
        assertTrue(Files.exists(outputPath))
        assertTrue(Files.size(outputPath) > 0)
        val content = Files.readString(outputPath)
        assertTrue(content.contains("Ktor"), "Content should contain 'Ktor'")
    }

    @Test
    fun `should get text content from a URL`() = runBlocking {
        // Act
        val result = ktorClient.get("https://raw.githubusercontent.com/ktorio/ktor/main/README.md")
        
        // Assert
        val content = result.getOrElse { 
            throw AssertionError("GET request failed with error: ${it.message}") 
        }
        
        assertTrue(content.isNotEmpty())
        assertTrue(content.contains("Ktor"), "Content should contain 'Ktor'")
    }
}