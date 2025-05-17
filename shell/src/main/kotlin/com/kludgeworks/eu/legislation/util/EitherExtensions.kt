package com.kludgeworks.eu.legislation.util

import arrow.core.Either
import com.kludgeworks.eu.legislation.rdf.ConcurrentOperationException
import com.kludgeworks.eu.legislation.rdf.EntityNotFoundException
import com.kludgeworks.eu.legislation.rdf.MappingException
import com.kludgeworks.eu.legislation.rdf.RequestTimeoutException
import com.kludgeworks.eu.legislation.rdf.SparqlQueryException
import com.kludgeworks.eu.legislation.shell.service.LegislationServiceException

/**
 * Extension to convert Either<DomainError, T> to value or throw appropriate exception
 */
fun <T> Either<DomainError, T>.getOrThrow(): T = fold(
    { error ->
        when (error) {
            is DomainError.NotFound -> {
                throw EntityNotFoundException(error.entityType, error.identifier, error.cause)
            }
            is DomainError.QueryFailure -> {
                throw SparqlQueryException(error.message, error.query, error.cause)
            }
            is DomainError.MappingFailure -> {
                throw MappingException(error.entityType, error.property, error.cause)
            }
            is DomainError.Timeout -> {
                throw RequestTimeoutException(error.operation, error.timeoutMs, error.cause)
            }
            is DomainError.ConcurrentFailure -> {
                throw ConcurrentOperationException(error.message, error.cause)
            }
            is DomainError.UnexpectedError -> {
                throw LegislationServiceException(error.message, error.cause)
            }
        }
    },
    { value -> value }
)

/**
 * Helper functions for creating domain errors in Either form
 */
fun <T> notFound(entityType: String, identifier: String, cause: Throwable? = null): Either<DomainError, T> =
    Either.Left(DomainError.NotFound(
        message = "$entityType not found with identifier: $identifier",
        cause = cause,
        entityType = entityType,
        identifier = identifier
    ))

fun <T> queryFailure(message: String, query: String? = null, cause: Throwable? = null): Either<DomainError, T> =
    Either.Left(DomainError.QueryFailure(
        message = "SPARQL query execution failed: $message",
        cause = cause,
        query = query
    ))

fun <T> mappingFailure(entityType: String, property: String, cause: Throwable? = null): Either<DomainError, T> =
    Either.Left(DomainError.MappingFailure(
        message = "Failed to map property '$property' for entity type '$entityType'",
        cause = cause,
        entityType = entityType,
        property = property
    ))

/**
 * Helper extension to convert a value to success result (Right)
 */
fun <T> T.asSuccess(): Either<DomainError, T> = Either.Right(this)

/**
 * Helper function to run a block and catch exceptions, converting them to domain errors
 */
suspend fun <T> runCatching(block: suspend () -> T): Either<DomainError, T> = try {
    Either.Right(block())
} catch (e: Exception) {
    when (e) {
        is EntityNotFoundException -> {
            Either.Left(DomainError.NotFound(
                message = e.message ?: "",
                cause = e,
                entityType = e.message?.substringBefore(" not found") ?: "Unknown",
                identifier = e.message?.substringAfter("identifier: ") ?: ""
            ))
        }
        is SparqlQueryException -> {
            Either.Left(DomainError.QueryFailure(
                message = e.message ?: "", 
                cause = e, 
                query = e.query
            ))
        }
        is MappingException -> {
            val entityType = e.message?.substringAfter("entity type '")?.substringBefore("'") ?: "Unknown"
            val property = e.message?.substringAfter("property '")?.substringBefore("'") ?: "Unknown"
            Either.Left(DomainError.MappingFailure(
                message = e.message ?: "", 
                cause = e, 
                entityType = entityType, 
                property = property
            ))
        }
        is RequestTimeoutException -> {
            val operation = e.message?.substringAfter("Operation '")?.substringBefore("'") ?: "Unknown"
            val timeoutMs = e.message?.substringAfter("timed out after ")?.substringBefore(" ms")?.toLongOrNull() ?: 0L
            Either.Left(DomainError.Timeout(
                message = e.message ?: "", 
                cause = e, 
                operation = operation, 
                timeoutMs = timeoutMs
            ))
        }
        is ConcurrentOperationException -> {
            Either.Left(DomainError.ConcurrentFailure(e.message ?: "", e))
        }
        else -> {
            Either.Left(DomainError.UnexpectedError(e.message ?: "", e))
        }
    }
}