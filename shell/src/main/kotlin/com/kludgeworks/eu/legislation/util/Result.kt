package com.kludgeworks.eu.legislation.util

import arrow.core.Either
import arrow.core.raise.*
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
 * Type alias for Either<DomainError, T> to represent a result that can be a success (Right) or an error (Left)
 */
typealias Result<T> = Either<DomainError, T>

/**
 * Extension function to create a successful Result
 */
fun <T> T.asSuccess(): Result<T> = Either.Right(this)

/**
 * Extension function to create a not found error Result
 */
fun notFound(entityType: String, identifier: String, cause: Throwable? = null): Result<Nothing> = either {
    raise(DomainError.NotFound(
        message = "$entityType not found with identifier: $identifier",
        cause = cause,
        entityType = entityType,
        identifier = identifier
    ))
}

/**
 * Extension function to create a query failure error Result
 */
fun queryFailure(message: String, query: String? = null, cause: Throwable? = null): Result<Nothing> = either {
    raise(DomainError.QueryFailure(
        message = "SPARQL query execution failed: $message",
        cause = cause,
        query = query
    ))
}

/**
 * Extension function to create a mapping failure error Result
 */
fun mappingFailure(entityType: String, property: String, cause: Throwable? = null): Result<Nothing> = either {
    raise(DomainError.MappingFailure(
        message = "Failed to map property '$property' for entity type '$entityType'",
        cause = cause, 
        entityType = entityType,
        property = property
    ))
}

/**
 * Extension function to run a block of code and convert exceptions to DomainError
 */
suspend fun <T> runCatching(block: suspend () -> T): Result<T> = either {
    catch({
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
                raise(DomainError.QueryFailure(e.message ?: "", e, e.query))
            }
            is MappingException -> {
                val entityType = e.message?.substringAfter("entity type '")?.substringBefore("'") ?: "Unknown"
                val property = e.message?.substringAfter("property '")?.substringBefore("'") ?: "Unknown"
                raise(DomainError.MappingFailure(e.message ?: "", e, entityType, property))
            }
            is RequestTimeoutException -> {
                val operation = e.message?.substringAfter("Operation '")?.substringBefore("'") ?: "Unknown"
                val timeoutMs = e.message?.substringAfter("timed out after ")?.substringBefore(" ms")?.toLongOrNull() ?: 0L
                raise(DomainError.Timeout(e.message ?: "", e, operation, timeoutMs))
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

/**
 * Convert a Result to the corresponding exception
 */
fun <T> Result<T>.getOrThrow(): T = fold(
    ifLeft = { error ->
        when (error) {
            is DomainError.NotFound -> throw EntityNotFoundException(error.entityType, error.identifier, error.cause)
            is DomainError.QueryFailure -> throw SparqlQueryException(error.message, error.query, error.cause)
            is DomainError.MappingFailure -> throw MappingException(error.entityType, error.property, error.cause)
            is DomainError.Timeout -> throw RequestTimeoutException(error.operation, error.timeoutMs, error.cause)
            is DomainError.ConcurrentFailure -> throw ConcurrentOperationException(error.message, error.cause)
            is DomainError.UnexpectedError -> throw LegislationServiceException(error.message, error.cause)
        }
    },
    ifRight = { it }
)

