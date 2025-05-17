package com.kludgeworks.eu.legislation.shell.commands

import arrow.core.raise.either
import com.kludgeworks.eu.legislation.rdf.model.ContentItem
import com.kludgeworks.eu.legislation.rdf.model.Expression
import com.kludgeworks.eu.legislation.rdf.model.Manifestation
import com.kludgeworks.eu.legislation.shell.service.DownloadService
import com.kludgeworks.eu.legislation.shell.service.LegalDocumentService
import kotlinx.coroutines.runBlocking
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
    private val legalDocumentService: LegalDocumentService,
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
    @Command(command = ["download-content-items"], description = "Download content items for a manifestation")
    fun downloadContentItems(
        @Option(longNames = ["celex"], shortNames = ['c'], description = "CELEX number", required = true) celex: String,
        @Option(longNames = ["language"], shortNames = ['l'], description = "Language code", defaultValue = "ENG") language: String
    ): String = runBlocking {
        try {
            logger.info("Downloading content items for CELEX {} in language {}", celex, language)

            // Get the complete document with all expressions
            val documentResult = either {
                legalDocumentService.getCompleteLegalDocument(celex)
            }
            
            val document = documentResult.fold(
                { error -> 
                    logger.error("Error retrieving document: {}", error.message)
                    return@runBlocking "Error: Could not retrieve document for CELEX $celex: ${error.message}"
                },
                { it }
            )
            
            // Filter expressions by language if provided
            val filteredExpressions = document.expressions.filter { expression: Expression -> 
                language.isEmpty() || expression.language.equals(language, ignoreCase = true) 
            }
            
            if (filteredExpressions.isEmpty()) {
                return@runBlocking "No expressions found for CELEX $celex in language $language"
            }
            
            // Process each expression's manifestations and content items
            val downloadedFiles = filteredExpressions.flatMap { expression: Expression ->
                // We already have manifestations in the expressions from our service
                expression.manifestations.flatMap { manifestation: Manifestation ->
                    manifestation.items.map { item: ContentItem ->
                        val outputFile = determineOutputFilePath(celex, expression.language, item)
                        logger.info("Downloading content item {} to {}", item.uri, outputFile)
                        
                        // Use arrow.core.raise.either to provide Raise context
                        val downloadResult = either {
                            downloadService.downloadContent(item.uri, outputFile)
                        }
                        
                        downloadResult.fold(
                            { error ->
                                logger.error("Error downloading content item: {}", error.message)
                                null 
                            },
                            { it.toString() }
                        )
                    }
                }
            }.filterNotNull()

            if (downloadedFiles.isEmpty()) {
                "No content items found to download for CELEX $celex in language $language"
            } else {
                "Successfully downloaded ${downloadedFiles.size} content items for CELEX $celex in language $language:\n" +
                    downloadedFiles.joinToString("\n")
            }
        } catch (e: Exception) {
            logger.error("Error downloading content items: {}", e.message, e)
            "Error downloading content items: ${e.message}"
        }
    }

    private fun determineOutputFilePath(celex: String, language: String, item: ContentItem): Path {
        val fileExtension = when {
            item.mimeType.contains("pdf", ignoreCase = true) -> "pdf"
            item.mimeType.contains("xml", ignoreCase = true) -> "xml"
            item.mimeType.contains("html", ignoreCase = true) -> "html"
            item.mimeType.contains("xhtml", ignoreCase = true) -> "xhtml"
            else -> {
                logger.warn("Unknown MIME type: {}.", item.mimeType)
                item.mimeType
            }
        }
        val outputDir = Paths.get("legislation", celex, language)
        // Ensure item.identifier is safe for use as a filename, or sanitize it.
        val fileName = item.identifier.ifBlank { "unknown_item_${item.uri.hashCode()}" }
        return outputDir.resolve("$fileName.$fileExtension")
    }
}