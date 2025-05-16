package com.kludgeworks.eu.legislation.util

import arrow.core.Either
import arrow.core.raise.*
import java.time.Duration

/**
 * Extension function to retry an operation with exponential backoff
 *
 * @param maxAttempts Maximum number of retry attempts
 * @param initialDelay Initial delay duration before the first retry
 * @param maxDelay Maximum delay duration for any retry
 * @param factor The exponential factor to increase the delay by on each retry
 * @param retryIf Predicate to determine if a failure should be retried
 * @return The result of the operation
 */
suspend fun <T> retryWithBackoff(
    maxAttempts: Int,
    initialDelay: Duration = Duration.ofMillis(100),
    maxDelay: Duration = Duration.ofSeconds(10),
    factor: Double = 2.0,
    retryIf: (DomainError) -> Boolean = { true },
    operation: suspend () -> Result<T>
): Result<T> {
    var currentDelay = initialDelay.toMillis()
    var lastError: DomainError? = null
    
    repeat(maxAttempts) { attempt ->
        // Skip the delay on the first attempt
        if (attempt > 0) {
            kotlinx.coroutines.delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay.toMillis())
        }
        
        // Attempt the operation
        val result = operation()
        
        // Check the result and return or continue retrying
        result.fold(
            ifLeft = { error ->
                lastError = error
                if (!retryIf(error)) {
                    return Either.Left(error)
                }
                // Continue with retry
            },
            ifRight = { 
                return result
            }
        )
    }
    
    // If we've exhausted all attempts, return the last error
    return Either.Left(lastError ?: DomainError.UnexpectedError("Unknown error during retry", null))
}