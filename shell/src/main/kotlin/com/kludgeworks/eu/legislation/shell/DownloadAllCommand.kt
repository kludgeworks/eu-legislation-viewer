package com.kludgeworks.eu.legislation.shell

import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import picocli.CommandLine.*
import java.io.File
import java.util.concurrent.Callable

@Component
@Command(
    name = "download-all",
    mixinStandardHelpOptions = true,
    version = ["1.0"],
    description = ["Downloads all content items for a work, its amendments, and consolidations"]
)
class DownloadAllCommand(
    private val legislationDownloadService: LegislationDownloadService,
    private val summaryGenerator: DownloadSummaryGenerator
) : Callable<Int> {

    @Option(
        names = ["-c", "--celex"],
        description = ["The CELEX number of the work to download"],
        required = true
    )
    lateinit var celexNumber: String

    @Option(
        names = ["-o", "--output"],
        description = ["The output directory path"],
        defaultValue = "legislation"
    )
    lateinit var outputPath: String

    @Option(
        names = ["-l", "--language"],
        description = ["Language code for content (default: eng)"],
        defaultValue = "eng"
    )
    var languageCode: String = "eng"

    override fun call(): Int {
        return runBlocking {
            try {
                println()
                println("=" * 80)
                println("EU Legislation Download")
                println("=" * 80)
                println("CELEX Number: $celexNumber")
                println("Output Directory: $outputPath")
                println("Language: $languageCode")
                println("=" * 80)

                // Ensure output directory exists
                val outputDir = File(outputPath)
                outputDir.mkdirs()

                // Start the download process
                legislationDownloadService.downloadAllContent(
                    celexNumber = celexNumber,
                    baseDirectory = outputPath,
                    languageCode = languageCode
                )

                // Generate and save summary report
                val workDir = File(outputPath, celexNumber)
                val summary = summaryGenerator.generateSummaryReport(workDir)
                File(workDir, "DOWNLOAD_SUMMARY.txt").writeText(summary)

                println()
                println("=" * 80)
                println("Download completed successfully!")
                println("Files saved to: ${workDir.absolutePath}")
                println("Summary report saved as: DOWNLOAD_SUMMARY.txt")
                println("=" * 80)
                println()
                println(summary)

                return@runBlocking 0
            } catch (e: Exception) {
                System.err.println()
                System.err.println("Error: ${e.message}")
                e.printStackTrace()
                return@runBlocking 1
            }
        }
    }
}

// Extension operator for String multiplication (for separator lines)
private operator fun String.times(count: Int): String = this.repeat(count)