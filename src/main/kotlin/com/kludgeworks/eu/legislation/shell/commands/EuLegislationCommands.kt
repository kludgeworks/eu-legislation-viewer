package com.kludgeworks.eu.legislation.shell.commands

import com.kludgeworks.eu.legislation.domain.Amendment
import com.kludgeworks.eu.legislation.domain.Consolidation
import com.kludgeworks.eu.legislation.domain.ContentItem
import com.kludgeworks.eu.legislation.domain.Expression
import com.kludgeworks.eu.legislation.domain.LegalDocument
import com.kludgeworks.eu.legislation.domain.LegislationServiceException
import com.kludgeworks.eu.legislation.domain.Manifestation
import com.kludgeworks.eu.legislation.shell.service.DownloadService
import com.kludgeworks.eu.legislation.shell.service.EuLegislationService
import com.kludgeworks.eu.legislation.shell.util.JsonFormatter
import com.kludgeworks.eu.legislation.util.DateUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.Option
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Shell commands for retrieving EU legislation data
 */
@Component
@Command(group = "EU Legislation")
class EuLegislationCommands @Autowired constructor(
    private val legislationService: EuLegislationService,
    private val downloadService: DownloadService,
    private val jsonFormatter: JsonFormatter
) {
    private val logger: Logger = LoggerFactory.getLogger(EuLegislationCommands::class.java)
    private val datePattern: String = "yyyy-MM-dd"

    /**
     * Get legislation information by CELEX number
     *
     * @param celex The CELEX number
     * @param json Whether to output in JSON format
     * @return The legislation information
     */
    @Command(command = ["legislation"])
    fun getLegislation(
        @Option(longNames = ["celex"], shortNames = ['c'], description = "CELEX number") celex: String,
        @Option(longNames = ["json"], shortNames = ['j'], description = "Output in JSON format", defaultValue = "false") json: Boolean
    ): String {
        try {
            logger.info("Getting legislation information for CELEX {}", celex)
            val document = legislationService.getLegislation(celex)

            if (json) {
                return jsonFormatter.toPrettyJson(document)
            }

            val result = buildString {
                append("Legislation: $celex\n")
                append("Title: ${document.title}\n")
                append("Date: ${DateUtils.format(document.documentDate, datePattern)}\n")
                append("Expressions: ${document.expressions.size}\n")

                document.expressions.forEach { expression ->
                    append("  Language: ${expression.language}\n")
                    append("  Manifestations: ${expression.manifestations.size}\n")

                    expression.manifestations.forEach { manifestation ->
                        append("    Type: ${manifestation.type}\n")
                        append("    Items: ${manifestation.items.size}\n")

                        manifestation.items.forEach { item ->
                            append("      URI: ${item.uri}\n")
                            if (item.identifier.isNotEmpty()) {
                                append("      ID: ${item.identifier}\n")
                            }
                            if (item.mimeType.isNotEmpty()) {
                                append("      MIME: ${item.mimeType}\n")
                            }
                        }
                    }
                }
            }

            return result
        } catch (e: LegislationServiceException) {
            logger.error("Error getting legislation information: {}", e.message, e)
            return "Error: ${e.message}"
        }
    }

    /**
     * Get expressions for a CELEX number
     *
     * @param celex The CELEX number
     * @param language The language code (optional)
     * @param json Whether to output in JSON format
     * @return The expressions
     */
    @Command(command = ["expressions"])
    fun getExpressions(
        @Option(longNames = ["celex"], shortNames = ['c'], description = "CELEX number") celex: String,
        @Option(longNames = ["language"], shortNames = ['l'], description = "Language code", defaultValue = "") language: String,
        @Option(longNames = ["json"], shortNames = ['j'], description = "Output in JSON format", defaultValue = "false") json: Boolean
    ): String {
        try {
            logger.info("Getting expressions for CELEX {} and language {}", celex, if (language.isEmpty()) "all" else language)
            val expressions = if (language.isEmpty()) {
                legislationService.getExpressions(celex)
            } else {
                legislationService.getExpressions(celex, language)
            }

            if (json) {
                return jsonFormatter.toPrettyJson(expressions)
            }

            val result = buildString {
                append("Expressions for CELEX $celex: ${expressions.size}\n")

                expressions.forEach { expression ->
                    append("Language: ${expression.language}\n")
                    append("URI: ${expression.uri}\n")
                    append("Manifestations: ${expression.manifestations.size}\n\n")
                }
            }

            return result
        } catch (e: Exception) {
            logger.error("Error getting expressions: {}", e.message, e)
            return "Error: ${e.message}"
        }
    }

    /**
     * Get manifestations for an expression URI
     *
     * @param expressionUri The expression URI
     * @param json Whether to output in JSON format
     * @return The manifestations
     */
    @Command(command = ["manifestations"])
    fun getManifestations(
        @Option(longNames = ["uri"], shortNames = ['u'], description = "Expression URI") expressionUri: String,
        @Option(longNames = ["json"], shortNames = ['j'], description = "Output in JSON format", defaultValue = "false") json: Boolean
    ): String {
        try {
            logger.info("Getting manifestations for expression {}", expressionUri)
            val manifestations = legislationService.getManifestations(expressionUri)

            if (json) {
                return jsonFormatter.toPrettyJson(manifestations)
            }

            val result = buildString {
                append("Manifestations for expression $expressionUri: ${manifestations.size}\n")

                manifestations.forEach { manifestation ->
                    append("Type: ${manifestation.type}\n")
                    append("URI: ${manifestation.uri}\n")
                    append("Items: ${manifestation.items.size}\n\n")
                }
            }

            return result
        } catch (e: Exception) {
            logger.error("Error getting manifestations: {}", e.message, e)
            return "Error: ${e.message}"
        }
    }

    /**
     * Get content items for a manifestation URI
     *
     * @param manifestationUri The manifestation URI
     * @param json Whether to output in JSON format
     * @return The content items
     */
    @Command(command = ["content-items"])
    fun getContentItems(
        @Option(longNames = ["uri"], shortNames = ['u'], description = "Manifestation URI") manifestationUri: String,
        @Option(longNames = ["json"], shortNames = ['j'], description = "Output in JSON format", defaultValue = "false") json: Boolean
    ): String {
        try {
            logger.info("Getting content items for manifestation {}", manifestationUri)
            val items = legislationService.getContentItems(manifestationUri)

            if (json) {
                return jsonFormatter.toPrettyJson(items)
            }

            val result = buildString {
                append("Content items for manifestation $manifestationUri: ${items.size}\n")

                items.forEach { item ->
                    append("URI: ${item.uri}\n")
                    if (item.identifier.isNotEmpty()) {
                        append("ID: ${item.identifier}\n")
                    }
                    if (item.manifestationType.isNotEmpty()) {
                        append("Manifestation type: ${item.manifestationType}\n")
                    }
                    if (item.mimeType.isNotEmpty()) {
                        append("MIME type: ${item.mimeType}\n")
                    }
                    append("\n")
                }
            }

            return result
        } catch (e: Exception) {
            logger.error("Error getting content items: {}", e.message, e)
            return "Error: ${e.message}"
        }
    }

    /**
     * Download content from a URL
     *
     * @param url The URL to download from
     * @param outputPath The path to save the downloaded content to
     * @return The path to the downloaded file
     */
    @Command(command = ["download"])
    fun downloadContent(
        @Option(longNames = ["url"], shortNames = ['u'], description = "URL to download from") url: String,
        @Option(longNames = ["output"], shortNames = ['o'], description = "Output path") outputPath: String
    ): String {
        try {
            logger.info("Downloading content from {} to {}", url, outputPath)
            val path = Paths.get(outputPath)
            val downloadedPath = downloadService.downloadContent(url, path)
            return "Downloaded content to $downloadedPath"
        } catch (e: IOException) {
            logger.error("Error downloading content: {}", e.message, e)
            return "Error downloading content: ${e.message}"
        }
    }

    /**
     * Get amendments for a CELEX number
     *
     * @param celex The CELEX number
     * @param json Whether to output in JSON format
     * @return The amendments
     */
    @Command(command = ["amendments"])
    fun getAmendments(
        @Option(longNames = ["celex"], shortNames = ['c'], description = "CELEX number") celex: String,
        @Option(longNames = ["json"], shortNames = ['j'], description = "Output in JSON format", defaultValue = "false") json: Boolean
    ): String {
        try {
            logger.info("Getting amendments for CELEX {}", celex)
            val amendments = legislationService.getAmendments(celex)

            if (json) {
                return jsonFormatter.toPrettyJson(amendments)
            }

            val result = buildString {
                append("Amendments for CELEX $celex: ${amendments.size}\n")

                amendments.forEach { amendment ->
                    append("CELEX: ${amendment.celexNumber}\n")
                    append("URI: ${amendment.uri}\n")
                    append("Date: ${DateUtils.format(amendment.date, datePattern)}\n")
                    append("Consolidations: ${amendment.consolidations.size}\n")

                    amendment.consolidations.forEach { consolidation ->
                        append("  CELEX: ${consolidation.celexNumber}\n")
                        append("  Date: ${DateUtils.format(consolidation.consolidationDate, datePattern)}\n")
                        if (consolidation.eli.isNotEmpty()) {
                            append("  ELI: ${consolidation.eli}\n")
                        }
                    }
                    append("\n")
                }
            }

            return result
        } catch (e: Exception) {
            logger.error("Error getting amendments: {}", e.message, e)
            return "Error: ${e.message}"
        }
    }
    
    /**
     * Get amendments with consolidations for a CELEX number
     *
     * @param celex The CELEX number
     * @param includeConsolidations Whether to include consolidations
     * @param json Whether to output in JSON format
     * @return The amendments with consolidations
     */
    @Command(command = ["amendments-with-consolidations"])
    fun getAmendmentsWithConsolidations(
        @Option(longNames = ["celex"], shortNames = ['c'], description = "CELEX number") celex: String,
        @Option(longNames = ["consolidations"], description = "Include consolidations", defaultValue = "true") includeConsolidations: Boolean,
        @Option(longNames = ["json"], shortNames = ['j'], description = "Output in JSON format", defaultValue = "false") json: Boolean
    ): String {
        try {
            logger.info("Getting amendments with consolidations for CELEX {}", celex)
            val amendments = legislationService.getAmendmentsWithConsolidations(celex, includeConsolidations)

            if (json) {
                return jsonFormatter.toPrettyJson(amendments)
            }

            val result = buildString {
                append("Amendments for CELEX $celex: ${amendments.size}\n")

                amendments.forEach { amendment ->
                    append("CELEX: ${amendment.celexNumber}\n")
                    append("URI: ${amendment.uri}\n")
                    append("Date: ${DateUtils.format(amendment.date, datePattern)}\n")
                    
                    if (includeConsolidations) {
                        append("Consolidations: ${amendment.consolidations.size}\n")

                        amendment.consolidations.forEach { consolidation ->
                            append("  CELEX: ${consolidation.celexNumber}\n")
                            append("  Date: ${DateUtils.format(consolidation.consolidationDate, datePattern)}\n")
                            if (consolidation.eli.isNotEmpty()) {
                                append("  ELI: ${consolidation.eli}\n")
                            }
                        }
                    }
                    append("\n")
                }
            }

            return result
        } catch (e: Exception) {
            logger.error("Error getting amendments with consolidations: {}", e.message, e)
            return "Error: ${e.message}"
        }
    }

    /**
     * Get consolidations for an amendment CELEX number
     *
     * @param celex The amendment CELEX number
     * @param json Whether to output in JSON format
     * @return The consolidations
     */
    @Command(command = ["consolidations"])
    fun getConsolidations(
        @Option(longNames = ["celex"], shortNames = ['c'], description = "Amendment CELEX number") celex: String,
        @Option(longNames = ["json"], shortNames = ['j'], description = "Output in JSON format", defaultValue = "false") json: Boolean
    ): String {
        try {
            logger.info("Getting consolidations for amendment CELEX {}", celex)
            val consolidations = legislationService.getConsolidations(celex)

            if (json) {
                return jsonFormatter.toPrettyJson(consolidations)
            }

            val result = buildString {
                append("Consolidations for amendment CELEX $celex: ${consolidations.size}\n")

                consolidations.forEach { consolidation ->
                    append("CELEX: ${consolidation.celexNumber}\n")
                    append("URI: ${consolidation.uri}\n")
                    append("Date: ${DateUtils.format(consolidation.consolidationDate, datePattern)}\n")
                    if (consolidation.eli.isNotEmpty()) {
                        append("ELI: ${consolidation.eli}\n")
                    }
                    append("\n")
                }
            }

            return result
        } catch (e: Exception) {
            logger.error("Error getting consolidations: {}", e.message, e)
            return "Error: ${e.message}"
        }
    }
    
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
        @Option(longNames = ["celex"], shortNames = ['c'], description = "CELEX number") celex: String,
        @Option(longNames = ["expressions"], description = "Include expressions", defaultValue = "true") includeExpressions: Boolean,
        @Option(longNames = ["manifestations"], description = "Include manifestations", defaultValue = "true") includeManifestations: Boolean,
        @Option(longNames = ["items"], description = "Include content items", defaultValue = "true") includeItems: Boolean,
        @Option(longNames = ["amendments"], description = "Include amendments", defaultValue = "true") includeAmendments: Boolean,
        @Option(longNames = ["consolidations"], description = "Include consolidations", defaultValue = "true") includeConsolidations: Boolean,
        @Option(longNames = ["json"], shortNames = ['j'], description = "Output in JSON format", defaultValue = "false") json: Boolean
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

            val result = buildString {
                append("Complete legal document for CELEX $celex\n")
                append("Title: ${document.title}\n")
                append("Date: ${DateUtils.format(document.documentDate, datePattern)}\n")
                
                if (includeExpressions) {
                    append("Expressions: ${document.expressions.size}\n")
                    document.expressions.forEach { expression ->
                        append("  Language: ${expression.language}\n")
                        
                        if (includeManifestations) {
                            append("  Manifestations: ${expression.manifestations.size}\n")
                            expression.manifestations.forEach { manifestation ->
                                append("    Type: ${manifestation.type}\n")
                                
                                if (includeItems) {
                                    append("    Items: ${manifestation.items.size}\n")
                                    manifestation.items.forEach { item ->
                                        append("      URI: ${item.uri}\n")
                                        if (item.identifier.isNotEmpty()) {
                                            append("      ID: ${item.identifier}\n")
                                        }
                                        if (item.mimeType.isNotEmpty()) {
                                            append("      MIME: ${item.mimeType}\n")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (includeAmendments) {
                    append("Amendments: ${document.amendments.size}\n")
                    document.amendments.forEach { amendment ->
                        append("  CELEX: ${amendment.celexNumber}\n")
                        append("  Date: ${DateUtils.format(amendment.date, datePattern)}\n")
                        
                        if (includeConsolidations) {
                            append("  Consolidations: ${amendment.consolidations.size}\n")
                            amendment.consolidations.forEach { consolidation ->
                                append("    CELEX: ${consolidation.celexNumber}\n")
                                append("    Date: ${DateUtils.format(consolidation.consolidationDate, datePattern)}\n")
                                if (consolidation.eli.isNotEmpty()) {
                                    append("    ELI: ${consolidation.eli}\n")
                                }
                            }
                        }
                    }
                }
            }

            return result
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
}