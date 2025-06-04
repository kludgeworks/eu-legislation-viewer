package com.kludgeworks.eu.legislation.shell

import com.kludgeworks.eu.legislation.cellarclient.ContentDownloadService
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import picocli.CommandLine.*
import java.io.File
import java.util.concurrent.Callable

@Component
@Command(
    name = "download",
    mixinStandardHelpOptions = true,
    version = ["1.0"],
    description = ["Downloads a content item by its URI"]
)
class DownloadCommand(
    private val contentDownloadService: ContentDownloadService
) : Callable<Int> {

    @Option(
        names = ["-u", "--uri"],
        description = ["The URI of the content item to download"],
        required = true
    )
    lateinit var uri: String

    @Option(
        names = ["-o", "--output"],
        description = ["The output file path"],
        required = true
    )
    lateinit var outputPath: String

    override fun call(): Int {
        return runBlocking {
            try {
                println()
                println("=" * 60)
                println("Downloading content item from URI: $uri")
                println("=" * 60)

                // Create the output file
                val outputFile = File(outputPath)

                // Ensure parent directory exists
                outputFile.parentFile?.mkdirs()

                println("Output file will be: ${outputFile.absolutePath}")

                // Download to temporary file
                println("Downloading to temporary file...")
                val tempFile = contentDownloadService.download(uri)

                try {
                    // Move the temporary file to the output location
                    println("Moving temporary file to output location...")
                    tempFile.renameTo(outputFile)
                    println("Downloaded to: ${outputFile.absolutePath}")
                    println("Download completed successfully.")
                    return@runBlocking 0
                } catch (e: Exception) {
                    System.err.println("Error moving temporary file: ${e.message}")
                    tempFile.delete()
                    return@runBlocking 1
                }
            } catch (e: Exception) {
                System.err.println("Error downloading content item: ${e.message}")
                e.printStackTrace()
                return@runBlocking 1
            }
        }
    }
}

// Extension operator for String multiplication (for separator lines)
private operator fun String.times(count: Int): String = this.repeat(count)
