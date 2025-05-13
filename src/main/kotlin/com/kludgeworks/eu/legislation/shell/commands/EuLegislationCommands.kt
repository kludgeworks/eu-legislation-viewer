package com.kludgeworks.eu.legislation.shell.commands

import com.kludgeworks.eu.legislation.shell.service.EuLegislationService
import com.kludgeworks.eu.legislation.shell.service.TemplateService
import com.kludgeworks.eu.legislation.shell.util.JsonFormatter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.Option
import org.springframework.stereotype.Component

/**
 * Shell commands for retrieving EU legislation data
 */
@Component
@Command
class EuLegislationCommands @Autowired constructor(
    private val legislationService: EuLegislationService,
    private val jsonFormatter: JsonFormatter,
    private val templateService: TemplateService
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
}