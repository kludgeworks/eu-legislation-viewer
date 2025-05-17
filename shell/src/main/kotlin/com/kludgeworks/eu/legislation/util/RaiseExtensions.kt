package com.kludgeworks.eu.legislation.util

import arrow.core.Either
import arrow.core.Option
import arrow.core.None
import arrow.core.Some
import arrow.core.flatMap
import arrow.core.raise.Raise
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import arrow.core.raise.fold
import arrow.core.raise.result

/**
 * Helper function to provide Raise context for suspending functions
 */
suspend fun <T> withRaiseContext(block: suspend context(Raise<DomainError>) () -> T): Either<DomainError, T> {
    return arrow.core.raise.either { block() }
}

/**
 * Helper function for testing code that uses the Raise API in a non-suspending context
 */
fun <T> testRaise(block: context(Raise<DomainError>) () -> T): T {
    try {
        var value: T? = null
        return arrow.core.raise.either {
            value = block()
            value!!
        }.fold(
            { error -> throw RuntimeException("Operation failed: ${error.message}") },
            { it }
        )
    } catch (e: Exception) {
        throw AssertionError("Operation failed: ${e.message}")
    }
}

/**
 * Helper function for testing code that uses the Raise API with suspending functions
 */
suspend fun <T> testRaiseSuspend(block: suspend context(Raise<DomainError>) () -> T): T {
    try {
        var value: T? = null
        return arrow.core.raise.either {
            value = block()
            value!!
        }.fold(
            { error -> throw RuntimeException("Operation failed: ${error.message}") },
            { it }
        )
    } catch (e: Exception) {
        throw AssertionError("Operation failed: ${e.message}")
    }
}

/**
 * Helper extension to wrap a try-catch with better error mapping in a Raise context
 */
context(Raise<DomainError>)
inline fun <T> catching(crossinline mapError: (Throwable) -> DomainError, crossinline block: () -> T): T =
    catch({
        block()
    }) { e ->
        raise(mapError(e))
    }

/**
 * Suspending version of catching
 */
context(Raise<DomainError>)
suspend inline fun <T> catchingSuspend(crossinline mapError: (Throwable) -> DomainError, crossinline block: suspend () -> T): T =
    catch({
        block()
    }) { e ->
        raise(mapError(e))
    }

/**
 * Handle exceptions converting them to domain errors
 */
context(Raise<DomainError>)
inline fun <T> catchingUnexpected(message: String? = null, crossinline block: () -> T): T =
    catch({
        block()
    }) { e ->
        val errorMessage = message ?: "Unexpected error: ${e.message}"
        raise(DomainError.UnexpectedError(errorMessage, e))
    }

/**
 * Suspending version of catchingUnexpected
 */
context(Raise<DomainError>)
suspend inline fun <T> catchingUnexpectedSuspend(message: String? = null, crossinline block: suspend () -> T): T =
    catch({
        block()
    }) { e ->
        val errorMessage = message ?: "Unexpected error: ${e.message}"
        raise(DomainError.UnexpectedError(errorMessage, e))
    }

/**
 * Extension to handle Option type in Raise context (converts None to a NotFound error)
 */
context(Raise<DomainError>)
fun <T> Option<T>.getOrRaise(entityType: String, identifier: String): T =
    when (this) {
        is Some -> this.value
        is None -> raise(DomainError.NotFound(
            message = "$entityType not found with identifier: $identifier",
            entityType = entityType,
            identifier = identifier
        ))
    }

/**
 * Extension to handle potentially null values in Raise context
 */
context(Raise<DomainError>)
fun <T: Any> T?.ensureExists(entityType: String, identifier: String): T =
    ensureNotNull(this) {
        DomainError.NotFound(
            message = "$entityType not found with identifier: $identifier",
            entityType = entityType,
            identifier = identifier
        )
    }

/**
 * Extension to validate conditions in Raise context with custom error factory
 */
context(Raise<DomainError>)
inline fun validate(condition: Boolean, errorFactory: () -> DomainError) {
    ensure(condition, errorFactory)
}

/**
 * Map function for working with Either in a Raise context
 */
context(Raise<DomainError>)
inline fun <A, B> Either<DomainError, A>.mapInRaise(crossinline f: (A) -> B): B =
    when (this) {
        is Either.Left -> raise(this.value)
        is Either.Right -> f(this.value)
    }

/**
 * Helper function to accumulate errors from a collection of operations
 */
context(Raise<DomainError>)
suspend fun <A, B> Iterable<A>.mapOrAccumulate(transform: suspend (A) -> B): List<B> {
    val result = mutableListOf<B>()
    for (item in this) {
        result.add(transform(item))
    }
    return result
}

/**
 * Helper function to accumulate errors while iterating through a collection
 */
context(Raise<DomainError>)
suspend fun <A> Iterable<A>.forEachAccumulating(action: suspend (A) -> Unit) {
    mapOrAccumulate { item -> 
        action(item)
        Unit
    }
    return
}
