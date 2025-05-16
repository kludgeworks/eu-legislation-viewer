package com.kludgeworks.eu.legislation.shell.commands

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

/**
 * Shell command for retrieving complete EU legislation data
 */
@Component
@Command
class CompleteLegislationCommand @Autowired constructor(
    private val legislationService: EuLegislationService,
    private val jsonFormatter: JsonFormatter,
    private val templateService: TemplateService
) {
    private val logger: Logger = LoggerFactory.getLogger(CompleteLegislationCommand::class.java)
    private val datePattern: String = "yyyy-MM-dd"

    /**
     * Get a complete legal document with all related entities
     *
     * @param celex The CELEX number
     * @param json Whether to output in JSON format
     * @return The complete legal document
     */
    @Command(command = ["complete-legislation"], description = "Get a complete legal document with all related entities" )
    fun getCompleteLegalDocument(
        @Option(longNames = ["celex"], shortNames = ['c'], description = "CELEX number", required = true) celex: String,
        @Option(longNames = ["json"], shortNames = ['j'], description = "Output in JSON format", arity = CommandRegistration.OptionArity.ZERO ) json: Boolean
    ): String {
        try {
            logger.info("Getting complete legal document for CELEX {}", celex)

            val document = legislationService.getCompleteLegalDocument(celex)

            if (json) {
                return jsonFormatter.toPrettyJson(document)
            }

            // Create model for the template
            val model = mapOf(
                "document" to document,
                "datePattern" to datePattern
            )

            // Process the template
            return templateService.processTemplate("legislation-document", model)
        } catch (e: Exception) {
            logger.error("Error getting complete legal document: {}", e.message, e)
            return "Error: ${e.message}"
        }
    }
}