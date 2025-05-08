package com.kludgeworks.eu.legislation.shell.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.zip.ZipInputStream

/**
 * Service for downloading content from URLs and optionally unzipping it
 */
@Service
class DownloadService {
    private val logger = LoggerFactory.getLogger(DownloadService::class.java)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /**
     * Download content from a URL to a file
     *
     * @param url The URL to download from
     * @param outputPath The path to save the downloaded content to
     * @return The path to the downloaded file
     * @throws IOException If an error occurs during download
     */
    @Throws(IOException::class)
    fun downloadContent(url: String, outputPath: Path): Path {
        logger.info("Downloading content from {} to {}", url, outputPath)

        // Create parent directories if they don't exist
        Files.createDirectories(outputPath.parent)

        // Check if it's a file URL, which is useful for testing
        if (url.startsWith("file:")) {
            // Use direct file operations for file URLs
            val inputPath = Paths.get(URI.create(url))
            Files.copy(inputPath, outputPath)
            logger.info("Copied file content to {}", outputPath)
        } else {
            // Download from web URL using Java's HttpClient
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Accept", "*/*")
                .GET()
                .build()

            // Send request and save the response body to the output file
            FileOutputStream(outputPath.toFile()).use { fileOutputStream ->
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
                if (response.statusCode() >= 400) {
                    throw IOException("Failed to download content: HTTP ${response.statusCode()}")
                }
                response.body().use { inputStream ->
                    inputStream.copyTo(fileOutputStream)
                }
            }
            
            logger.info("Downloaded content to {}", outputPath)
        }

        // Check if the downloaded file is a ZIP file and unzip if needed
        return if (outputPath.toString().endsWith(".zip", ignoreCase = true)) {
            logger.info("Downloaded file is a ZIP file, unzipping...")
            unzip(outputPath, outputPath.parent).also {
                logger.info("Unzipped content to {}", it)
            }
        } else {
            outputPath
        }
    }

    /**
     * Unzip a file to a directory
     *
     * @param zipFile The ZIP file to unzip
     * @param outputDir The directory to unzip to
     * @return The path to the unzipped directory
     * @throws IOException If an error occurs during unzipping
     */
    @Throws(IOException::class)
    private fun unzip(zipFile: Path, outputDir: Path): Path {
        // Create a directory with the same name as the ZIP file (without the .zip extension)
        val dirName = zipFile.fileName.toString().removeSuffix(".zip")
        val unzipDir = outputDir.resolve(dirName).also {
            Files.createDirectories(it)
        }

        // Unzip the file
        ZipInputStream(Files.newInputStream(zipFile)).use { zipInputStream ->
            // Keep reading entries until we get null
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                val entryPath = unzipDir.resolve(entry.name)

                if (entry.isDirectory) {
                    Files.createDirectories(entryPath)
                } else {
                    Files.createDirectories(entryPath.parent)
                    FileOutputStream(entryPath.toFile()).use { out ->
                        zipInputStream.copyTo(out)
                    }
                }

                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
        }

        return unzipDir
    }
}