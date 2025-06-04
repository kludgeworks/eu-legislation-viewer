package com.kludgeworks.eu.legislation.cellarclient

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
import org.springframework.stereotype.Service
import java.io.File
import java.io.OutputStream

/**
 * Service for downloading content items from EU Cellar
 */
@Service
class ContentDownloadService(
    private val httpClient: HttpClient
) {

    /**
     * Downloads a content item from the given URI to a temporary file
     *
     * @param uri The URI of the content item to download
     * @param onProgress Optional callback function that receives progress updates during download
     * @return The temporary file that was created
     * @throws Exception If there's an error during the download or if the download fails
     */
    suspend fun download(
        uri: String,
        onProgress: (Long, Long?) -> Unit = { _, _ -> }
    ): File {
        val outputFile = File.createTempFile("download-", "-temp")

        try {
            val result = httpClient.prepareGet(uri).execute { response ->
                if (response.status.value in 200..299) {
                    val contentLength = response.headers["Content-Length"]?.toLongOrNull()

                    // Create a progress tracking output stream wrapper
                    val progressOutputStream = object : OutputStream() {
                        private val outputStream = outputFile.outputStream().buffered()
                        private var totalBytes = 0L

                        override fun write(b: Int) {
                            outputStream.write(b)
                            totalBytes++
                            onProgress(totalBytes, contentLength)
                        }

                        override fun write(b: ByteArray) {
                            outputStream.write(b)
                            totalBytes += b.size
                            onProgress(totalBytes, contentLength)
                        }

                        override fun write(b: ByteArray, off: Int, len: Int) {
                            outputStream.write(b, off, len)
                            totalBytes += len
                            onProgress(totalBytes, contentLength)
                        }

                        override fun flush() {
                            outputStream.flush()
                        }

                        override fun close() {
                            outputStream.close()
                        }
                    }

                    // Copy the response body to the output file with progress tracking
                    response.bodyAsChannel().copyTo(progressOutputStream)
                    progressOutputStream.close()

                    outputFile
                } else {
                    throw Exception("HTTP ${response.status.value}: ${response.status.description}")
                }
            }

            return result
        } catch (e: Exception) {
            // Clean up the temporary file if there was an error
            outputFile.delete()
            throw e
        }
    }
}
