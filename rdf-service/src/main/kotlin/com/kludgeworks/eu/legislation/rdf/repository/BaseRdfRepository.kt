package com.kludgeworks.eu.legislation.rdf.repository

import arrow.core.Either
import arrow.core.raise.either
import com.kludgeworks.eu.legislation.rdf.DomainError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import kotlin.collections.iterator

/**
 * Base repository for SPARQL queries following repository pattern
 * Provides common functionality for executing SPARQL queries with error handling
 */
abstract class BaseRdfRepository(
    protected val repository: SPARQLRepository
) {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Load a SPARQL query from the resources directory
     *
     * @param filename The name of the query file
     * @return The query string
     * @throws IllegalStateException if the query file is not found
     */
    protected fun loadQuery(filename: String): String {
        return this::class.java.getResourceAsStream("/sparql/$filename")
            ?.bufferedReader()
            ?.readText()
            ?: throw IllegalStateException("Query file not found: $filename")
    }

    /**
     * Execute a SPARQL SELECT query with parameter binding and result mapping
     * Uses Arrow's Either for functional error handling
     *
     * @param query The SPARQL query string
     * @param parameters Map of parameter names to values
     * @param mapper Function to map BindingSet to domain object
     * @return Either an error or a list of mapped results
     */
    protected suspend fun <T> executeQuery(
        query: String,
        parameters: Map<String, Any> = emptyMap(),
        mapper: (BindingSet) -> T
    ): Either<DomainError, List<T>> = either {
        withContext(Dispatchers.IO) {
            try {
                logger.debug("Executing SPARQL query: $query")
                repository.connection.use { connection ->
                    val tupleQuery = connection.prepareTupleQuery(query)

                    // Bind parameters with proper type handling
                    for ((key, value) in parameters) {
                        val rdfValue: Value = when (value) {
                            is String -> repository.valueFactory.createLiteral(value, XSD.STRING)
                            is Int -> repository.valueFactory.createLiteral(value)
                            is Long -> repository.valueFactory.createLiteral(value)
                            is Boolean -> repository.valueFactory.createLiteral(value)
                            is URI -> repository.valueFactory.createIRI(value.toString())
                            else -> repository.valueFactory.createLiteral(value.toString())
                        }
                        tupleQuery.setBinding(key, rdfValue)
                        logger.debug("Binding parameter: {} = {}", key, rdfValue)
                    }
                    logger.debug("Executing query with ${parameters.size} parameters")

                    val results = mutableListOf<T>()
                    tupleQuery.evaluate().use { result ->
                        while (result.hasNext()) {
                            try {
                                results.add(mapper(result.next()))
                            } catch (e: Exception) {
                                logger.warn("Failed to map result: ${e.message}")
                                // Continue processing other results
                            }
                        }
                    }

                    logger.debug("Query returned ${results.size} results")
                    results
                }
            } catch (e: Exception) {
                logger.error("Failed to execute SPARQL query: ${e.message}", e)
                raise(
                    DomainError.QueryFailure(
                        message = "Failed to execute SPARQL query: ${e.message}",
                        cause = e,
                        query = query
                    )
                )
            }
        }
    }
}
