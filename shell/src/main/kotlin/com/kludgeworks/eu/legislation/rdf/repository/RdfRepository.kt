package com.kludgeworks.eu.legislation.rdf.repository

import arrow.core.raise.Raise
import com.kludgeworks.eu.legislation.util.DomainError
import com.kludgeworks.eu.legislation.util.catchingUnexpectedSuspend
import com.kludgeworks.eu.legislation.util.queryFailureError
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
     * @return List of processed results
     */
    context(Raise<DomainError>)
    suspend fun <T> select(
        query: String,
        timeoutMs: Long = defaultTimeoutMs,
        processor: (QuerySolution) -> T
    ): List<T> {
        return catchingUnexpectedSuspend("SPARQL query execution error") { 
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
                queryFailureError("Query timed out after $timeoutMs ms", query, e)
            } catch (e: Exception) {
                logger.error("Error executing SPARQL query: {}", e.message, e)
                queryFailureError(e.message ?: "Unknown error", query, e)
            }
        }
    }

    /**
     * Execute an ASK SPARQL query
     *
     * @param query The ASK SPARQL query to execute
     * @param timeoutMs Optional timeout in milliseconds
     * @return Boolean result of the ASK query
     */
    context(Raise<DomainError>)
    suspend fun ask(
        query: String,
        timeoutMs: Long = defaultTimeoutMs
    ): Boolean {
        return catchingUnexpectedSuspend("SPARQL ASK query execution error") {
            try {
                withContext(Dispatchers.IO) {
                    withTimeout(timeoutMs) {
                        QueryExecution.service(endpointUrl).query(query).build().use { execution ->
                            execution.execAsk()
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                logger.error("SPARQL ASK query timed out after {} ms", timeoutMs)
                queryFailureError("ASK query timed out after $timeoutMs ms", query, e)
            } catch (e: Exception) {
                logger.error("Error executing SPARQL ASK query: {}", e.message, e)
                queryFailureError(e.message ?: "Unknown error", query, e)
            }
        }
    }

    /**
     * Execute a CONSTRUCT SPARQL query
     *
     * @param query The CONSTRUCT SPARQL query to execute
     * @param timeoutMs Optional timeout in milliseconds
     * @return Model containing the constructed RDF graph
     */
    context(Raise<DomainError>)
    suspend fun construct(
        query: String,
        timeoutMs: Long = defaultTimeoutMs
    ): org.apache.jena.rdf.model.Model {
        return catchingUnexpectedSuspend("SPARQL CONSTRUCT query execution error") {
            try {
                withContext(Dispatchers.IO) {
                    withTimeout(timeoutMs) {
                        QueryExecution.service(endpointUrl).query(query).build().use { execution ->
                            execution.execConstruct()
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                logger.error("SPARQL CONSTRUCT query timed out after {} ms", timeoutMs)
                queryFailureError("CONSTRUCT query timed out after $timeoutMs ms", query, e)
            } catch (e: Exception) {
                logger.error("Error executing SPARQL CONSTRUCT query: {}", e.message, e)
                queryFailureError(e.message ?: "Unknown error", query, e)
            }
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
