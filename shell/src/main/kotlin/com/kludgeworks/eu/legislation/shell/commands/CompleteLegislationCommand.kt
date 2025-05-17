package com.kludgeworks.eu.legislation.shell.commands

import com.kludgeworks.eu.legislation.shell.service.LegalDocumentService
import com.kludgeworks.eu.legislation.shell.service.TemplateService
import com.kludgeworks.eu.legislation.shell.util.JsonFormatter
import com.kludgeworks.eu.legislation.util.DomainError
import kotlinx.coroutines.runBlocking
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
    private val legalDocumentService: LegalDocumentService,
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
    ): String = runBlocking {
        logger.info("Getting complete legal document for CELEX {}", celex)

        try {
            val document = arrow.core.raise.either({
                legalDocumentService.getCompleteLegalDocument(celex)
            }).fold(
                { error -> return@runBlocking handleError(error, celex) },
                { it }
            )
            
            if (json) {
                return@runBlocking jsonFormatter.toPrettyJson(document)
            } else {
                // Create model for the template
                val model = mapOf(
                    "document" to document,
                    "datePattern" to datePattern
                )
                
                // Process the template
                return@runBlocking templateService.processTemplate("legislation-document", model)
            }
        } catch (e: Exception) {
            logger.error("Unexpected error getting document: {}", e.message, e)
            "Error: An unexpected error occurred: ${e.message}"
        }
    }
    
    /**
     * Handle errors from the Result type and convert them to user-friendly messages
     */
    private fun handleError(error: DomainError, celex: String): String {
        return when (error) {
            is DomainError.NotFound -> {
                logger.error("Document not found: {}", error.message)
                "Error: Document not found for CELEX $celex"
            }
            is DomainError.QueryFailure -> {
                logger.error("SPARQL query failed: {}", error.message)
                "Error: Failed to query EU legislation database. Please try again later."
            }
            is DomainError.MappingFailure -> {
                logger.error("Mapping failed: {}", error.message)
                "Error: Failed to process document data. Please report this issue."
            }
            is DomainError.ConcurrentFailure -> {
                logger.error("Concurrent operations failed: {}", error.message)
                "Error: Failed to fetch all document components. Document may be incomplete."
            }
            is DomainError.Timeout -> {
                logger.error("Operation timed out: {}", error.message)
                "Error: Operation timed out. Please try again later."
            }
            is DomainError.UnexpectedError -> {
                logger.error("Error getting complete legal document: {}", error.message, error.cause)
                "Error: ${error.message}"
            }
        }
    }
}