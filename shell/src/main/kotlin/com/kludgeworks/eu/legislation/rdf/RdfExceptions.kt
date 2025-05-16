package com.kludgeworks.eu.legislation.rdf

import com.kludgeworks.eu.legislation.shell.service.LegislationServiceException

/**
 * Base exception for RDF-related errors
 */
open class RdfException(
    message: String,
    cause: Throwable? = null
) : LegislationServiceException(message, cause)

/**
 * Exception thrown when SPARQL query execution fails
 */
class SparqlQueryException(
    message: String,
    val query: String? = null,
    cause: Throwable? = null
) : RdfException("SPARQL query execution failed: $message", cause) {
    override fun toString(): String {
        return if (query != null) {
            "${super.toString()}\nQuery: $query"
        } else {
            super.toString()
        }
    }
}

/**
 * Exception thrown when a requested entity is not found
 */
class EntityNotFoundException(
    entityType: String,
    identifier: String,
    cause: Throwable? = null
) : RdfException("$entityType not found with identifier: $identifier", cause)

/**
 * Exception thrown when a required property is missing in entity mapping
 */
class MappingException(
    entityType: String,
    property: String,
    cause: Throwable? = null
) : RdfException("Failed to map property '$property' for entity type '$entityType'", cause)

/**
 * Exception thrown when concurrent operations fail
 */
class ConcurrentOperationException(
    message: String,
    cause: Throwable? = null
) : RdfException("Concurrent operation failed: $message", cause)

/**
 * Exception thrown when a request times out
 */
class RequestTimeoutException(
    operation: String,
    timeoutMs: Long,
    cause: Throwable? = null
) : RdfException("Operation '$operation' timed out after $timeoutMs ms", cause)