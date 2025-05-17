package com.kludgeworks.eu.legislation.util

import arrow.core.Either
import arrow.core.raise.*
import kotlin.contracts.ExperimentalContracts
import com.kludgeworks.eu.legislation.rdf.ConcurrentOperationException
import com.kludgeworks.eu.legislation.rdf.EntityNotFoundException
import com.kludgeworks.eu.legislation.rdf.MappingException
import com.kludgeworks.eu.legislation.rdf.RdfException
import com.kludgeworks.eu.legislation.rdf.RequestTimeoutException
import com.kludgeworks.eu.legislation.rdf.SparqlQueryException
import com.kludgeworks.eu.legislation.shell.service.LegislationServiceException

/**
 * Domain error types for functional error handling
 */
sealed class DomainError {
    abstract val message: String
    abstract val cause: Throwable?
    
    data class NotFound(
        override val message: String,
        override val cause: Throwable? = null,
        val entityType: String,
        val identifier: String
    ) : DomainError()
    
    data class QueryFailure(
        override val message: String,
        override val cause: Throwable? = null,
        val query: String? = null
    ) : DomainError()
    
    data class MappingFailure(
        override val message: String,
        override val cause: Throwable? = null,
        val entityType: String,
        val property: String
    ) : DomainError()
    
    data class Timeout(
        override val message: String,
        override val cause: Throwable? = null,
        val operation: String,
        val timeoutMs: Long
    ) : DomainError()
    
    data class ConcurrentFailure(
        override val message: String,
        override val cause: Throwable? = null
    ) : DomainError()
    
    data class UnexpectedError(
        override val message: String,
        override val cause: Throwable? = null
    ) : DomainError()
}

/**
 * Create a NotFound domain error in a Raise context
 */
context(Raise<DomainError>)
fun notFoundError(entityType: String, identifier: String, cause: Throwable? = null): Nothing {
    raise(DomainError.NotFound(
        message = "$entityType not found with identifier: $identifier",
        cause = cause,
        entityType = entityType,
        identifier = identifier
    ))
}

/**
 * Create a QueryFailure domain error in a Raise context
 */
context(Raise<DomainError>)
fun queryFailureError(message: String, query: String? = null, cause: Throwable? = null): Nothing {
    raise(DomainError.QueryFailure(
        message = "SPARQL query execution failed: $message",
        cause = cause,
        query = query
    ))
}

/**
 * Create a MappingFailure domain error in a Raise context
 */
context(Raise<DomainError>)
fun mappingFailureError(entityType: String, property: String, cause: Throwable? = null): Nothing {
    raise(DomainError.MappingFailure(
        message = "Failed to map property '$property' for entity type '$entityType'",
        cause = cause, 
        entityType = entityType,
        property = property
    ))
}

/**
 * Extension function to handle exceptions in a suspending context
 */
context(Raise<DomainError>)
suspend fun <T> raiseCatch(block: suspend () -> T): T {
    return catch({
        block()
    }) { e ->
        when (e) {
            is EntityNotFoundException -> {
                raise(DomainError.NotFound(
                    message = e.message ?: "",
                    cause = e,
                    entityType = e.message?.substringBefore(" not found") ?: "Unknown",
                    identifier = e.message?.substringAfter("identifier: ") ?: ""
                ))
            }
            is SparqlQueryException -> {
                raise(DomainError.QueryFailure(
                    message = e.message ?: "", 
                    cause = e, 
                    query = e.query
                ))
            }
            is MappingException -> {
                val entityType = e.message?.substringAfter("entity type '")?.substringBefore("'") ?: "Unknown"
                val property = e.message?.substringAfter("property '")?.substringBefore("'") ?: "Unknown"
                raise(DomainError.MappingFailure(
                    message = e.message ?: "", 
                    cause = e, 
                    entityType = entityType, 
                    property = property
                ))
            }
            is RequestTimeoutException -> {
                val operation = e.message?.substringAfter("Operation '")?.substringBefore("'") ?: "Unknown"
                val timeoutMs = e.message?.substringAfter("timed out after ")?.substringBefore(" ms")?.toLongOrNull() ?: 0L
                raise(DomainError.Timeout(
                    message = e.message ?: "", 
                    cause = e, 
                    operation = operation, 
                    timeoutMs = timeoutMs
                ))
            }
            is ConcurrentOperationException -> {
                raise(DomainError.ConcurrentFailure(e.message ?: "", e))
            }
            is RdfException -> {
                raise(DomainError.UnexpectedError(e.message ?: "", e))
            }
            is LegislationServiceException -> {
                raise(DomainError.UnexpectedError(e.message ?: "", e))
            }
            else -> {
                raise(DomainError.UnexpectedError(e.message ?: "", e))
            }
        }
    }
}

// Moved helper functions to RaiseExtensions.kt

