package com.kludgeworks.eu.legislation.shell.commands

import com.kludgeworks.eu.legislation.domain.ContentItem
import com.kludgeworks.eu.legislation.shell.service.DownloadService
import com.kludgeworks.eu.legislation.shell.service.EuLegislationService
import com.kludgeworks.eu.legislation.shell.service.TemplateService
import com.kludgeworks.eu.legislation.shell.util.JsonFormatter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.shell.command.CommandRegistration
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.Option
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Shell commands for retrieving EU legislation data
 */
@Component
@Command
class EuLegislationCommands @Autowired constructor(
    private val legislationService: EuLegislationService,
    private val jsonFormatter: JsonFormatter,
    private val templateService: TemplateService,
    private val downloadService: DownloadService
) {
    private val logger: Logger = LoggerFactory.getLogger(EuLegislationCommands::class.java)
    private val datePattern: String = "yyyy-MM-dd"

    /**
     * Get a complete legal document with all related entities
     *
     * @param celex The CELEX number
     * @param includeExpressions Whether to include expressions
     * @param includeManifestations Whether to include manifestations  
     * @param includeItems Whether to include content items
     * @param includeAmendments Whether to include amendments
     * @param includeConsolidations Whether to include consolidations
     * @param json Whether to output in JSON format
     * @return The complete legal document
     */
    @Command(command = ["complete-legislation"])
    fun getCompleteLegalDocument(
        @Option(longNames = ["celex"], shortNames = ['c'], description = "CELEX number", required = true) celex: String,
        @Option(longNames = ["expressions"], description = "Include expressions", defaultValue = "true") includeExpressions: Boolean,
        @Option(longNames = ["manifestations"], description = "Include manifestations", defaultValue = "true") includeManifestations: Boolean,
        @Option(longNames = ["items"], description = "Include content items", defaultValue = "true") includeItems: Boolean,
        @Option(longNames = ["amendments"], description = "Include amendments", defaultValue = "true") includeAmendments: Boolean,
        @Option(longNames = ["consolidations"], description = "Include consolidations", defaultValue = "true") includeConsolidations: Boolean,
        @Option(longNames = ["json"], shortNames = ['j'], description = "Output in JSON format", arity = CommandRegistration.OptionArity.ZERO ) json: Boolean
    ): String {
        try {
            logger.info("Getting complete legal document for CELEX {}", celex)

            val document = legislationService.getCompleteLegalDocument(
                celexNumber = celex,
                includeExpressions = includeExpressions,
                includeManifestations = includeManifestations,
                includeItems = includeItems,
                includeAmendments = includeAmendments,
                includeConsolidations = includeConsolidations
            )

            if (json) {
                return jsonFormatter.toPrettyJson(document)
            }

            // Create model for the template
            val model = mapOf(
                "document" to document,
                "includeExpressions" to includeExpressions,
                "includeManifestations" to includeManifestations,
                "includeItems" to includeItems,
                "includeAmendments" to includeAmendments,
                "includeConsolidations" to includeConsolidations,
                "datePattern" to datePattern
            )

            // Process the template
            return templateService.processTemplate("legislation-document", model)
        } catch (e: Exception) {
            logger.error("Error getting complete legal document: {}", e.message, e)
            return "Error: ${e.message}"
        }
    }

    /**
     * Get the language code from a language URI
     *
     * @param languageUri The language URI
     * @return The language code
     */
    private fun getLanguageCode(languageUri: String): String {
        val parts = languageUri.split("/")
        return if (parts.isNotEmpty()) parts.last() else languageUri
    }

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
