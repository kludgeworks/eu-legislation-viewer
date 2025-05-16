package com.kludgeworks.eu.legislation.shell.commands

import com.kludgeworks.eu.legislation.rdf.ContentItem
import com.kludgeworks.eu.legislation.shell.service.DownloadService
import com.kludgeworks.eu.legislation.shell.service.EuLegislationService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.Option
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Shell command for downloading EU legislation content items
 */
@Component
@Command
class DownloadContentItemsCommand @Autowired constructor(
    private val legislationService: EuLegislationService,
    private val downloadService: DownloadService
) {
    private val logger: Logger = LoggerFactory.getLogger(DownloadContentItemsCommand::class.java)

    /**
     * Download content items for a manifestation
     *
     * @param celex The CELEX number
     * @param language The language code (defaults to ENG)
     * @return A message indicating the result of the download
     */
    @Command(command = ["download-content-items"])
    fun downloadContentItems(
        @Option(longNames = ["celex"], shortNames = ['c'], description = "CELEX number", required = true ) celex: String,
        @Option(longNames = ["language"], shortNames = ['l'], description = "Language code", defaultValue = "ENG") language: String
    ): String {
        try {
            logger.info("Downloading content items for CELEX {} in language {}", celex, language)

            val downloadedFiles = legislationService.getExpressions(celex, language)
                .ifEmpty {
                    return "No expressions found for CELEX $celex in language $language"
                }
                .flatMap { expression ->
                    legislationService.getManifestations(expression.uri)
                        .flatMap { manifestation ->
                            legislationService.getContentItems(manifestation.uri)
                                .map { item ->
                                    val outputFile = determineOutputFilePath(celex, language, item)
                                    logger.info("Downloading content item {} to {}", item.uri, outputFile)
                                    downloadService.downloadContent(item.uri, outputFile) // downloadContent should return the Path
                                    outputFile.toString()
                                }
                        }
                }

            return if (downloadedFiles.isEmpty()) {
                "No content items found to download for CELEX $celex in language $language"
            } else {
                "Successfully downloaded ${downloadedFiles.size} content items for CELEX $celex in language $language:\n" +
                        downloadedFiles.joinToString("\n")
            }
        } catch (e: Exception) {
            logger.error("Error downloading content items: {}", e.message, e)
            return "Error downloading content items: ${e.message}"
        }
    }

    private fun determineOutputFilePath(celex: String, language: String, item: ContentItem): Path {
        val fileExtension = when {
            item.mimeType.contains("pdf", ignoreCase = true) -> "pdf"
            item.mimeType.contains("xml", ignoreCase = true) -> "xml"
            item.mimeType.contains("html", ignoreCase = true) -> "html"
            item.mimeType.contains("xhtml", ignoreCase = true) -> "xhtml"
            else -> "bin" // Default binary extension
        }
        val outputDir = Paths.get("legislation", celex, language)
        // Ensure item.identifier is safe for use as a filename, or sanitize it.
        val fileName = item.identifier.ifBlank { "unknown_item_${item.uri.hashCode()}" }
        return outputDir.resolve("$fileName.$fileExtension")
    }
}