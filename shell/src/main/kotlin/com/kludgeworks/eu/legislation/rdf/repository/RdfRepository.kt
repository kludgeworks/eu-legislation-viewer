package com.kludgeworks.eu.legislation.rdf.repository

import arrow.core.Either
import arrow.core.raise.*
import com.kludgeworks.eu.legislation.util.DomainError
import com.kludgeworks.eu.legislation.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.jena.query.QueryExecution
import org.apache.jena.query.QuerySolution
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Base interface for SPARQL RDF repositories
 */
interface RdfRepository {
    /**
     * The SPARQL endpoint URL
     */
    val endpointUrl: String
    
    /**
     * Logger instance
     */
    val logger: Logger
    
    /**
     * Default query timeout (15 seconds)
     */
    val defaultTimeoutMs: Long
        get() = 15000L

    /**
     * Execute a SELECT SPARQL query and process the results with the provided function
     *
     * @param query The SPARQL query to execute
     * @param timeoutMs Optional timeout in milliseconds
     * @param processor Function to process each QuerySolution
     * @return List of processed results wrapped in Result
     */
    suspend fun <T> select(
        query: String,
        timeoutMs: Long = defaultTimeoutMs,
        processor: (QuerySolution) -> T
    ): Result<List<T>> = either {
        try {
            withContext(Dispatchers.IO) {
                withTimeout(timeoutMs) {
                    QueryExecution.service(endpointUrl).query(query).build().use { execution ->
                        val resultSet = execution.execSelect()
                        val results = ArrayList<T>()
                        
                        while (resultSet.hasNext()) {
                            val solution = resultSet.next()
                            results.add(processor(solution))
                        }
                        
                        results
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            logger.error("SPARQL query timed out after {} ms", timeoutMs)
            raise(DomainError.QueryFailure("Query timed out after $timeoutMs ms", e, query))
        } catch (e: Exception) {
            logger.error("Error executing SPARQL query: {}", e.message, e)
            raise(DomainError.QueryFailure(e.message ?: "Unknown error", e, query))
        }
    }
    
    /**
     * Create a base implementation with the provided endpoint and logger
     */
    companion object {
        fun create(endpointUrl: String, loggerName: String): RdfRepository {
            return object : RdfRepository {
                override val endpointUrl = endpointUrl
                override val logger: Logger = LoggerFactory.getLogger(loggerName)
            }
        }
    }
}