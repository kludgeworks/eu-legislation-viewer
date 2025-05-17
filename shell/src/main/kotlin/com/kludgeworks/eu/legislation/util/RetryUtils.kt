package com.kludgeworks.eu.legislation.util

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.coroutines.delay
import java.time.Duration

/**
 * Extension function to retry an operation with exponential backoff
 * Note: This is designed to be used within a Raise context
 */
context(Raise<DomainError>)
suspend fun <T> retryWithBackoff(
    maxAttempts: Int,
    initialDelay: Duration = Duration.ofMillis(100),
    maxDelay: Duration = Duration.ofSeconds(10),
    factor: Double = 2.0,
    retryIf: (DomainError) -> Boolean = { true },
    operation: suspend context(Raise<DomainError>) () -> T
): T {
    var currentDelay = initialDelay.toMillis()
    var attempt = 0
    var lastError: DomainError? = null
    
    while (attempt < maxAttempts) {
        try {
            // Skip the delay on the first attempt
            if (attempt > 0) {
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay.toMillis())
            }
            
            // Create a nested context to catch errors
            val result = either<DomainError, T> { 
                operation() 
            }
            
            when (result) {
                is Either.Right -> return result.value
                is Either.Left -> {
                    val error = result.value
                    lastError = error
                    
                    if (!retryIf(error)) {
                        // Use check which will throw an exception in this context
                        // to short-circuit the retry
                        val e = RetryAbortException(error)
                        throw e
                    }
                    // Continue to next retry attempt
                }
            }
        } catch (e: RetryAbortException) {
            // We should not retry, re-throw
            throw RetryFailureException(e.error)
        } catch (e: Exception) {
            if (e is RetryFailureException) throw e
            
            // Handle unexpected exceptions
            val error = DomainError.UnexpectedError("Error during retry: ${e.message}", e)
            lastError = error
            
            if (!retryIf(error)) {
                throw RetryFailureException(error)
            }
        }
        
        attempt++
    }
    
    // If we've exhausted all attempts, raise the last error
    throw RetryFailureException(lastError ?: DomainError.UnexpectedError("Unknown error during retry", null))
}

/**
 * Exception used internally for aborting retries when an error shouldn't be retried
 */
private class RetryAbortException(val error: DomainError) : Exception("Retry aborted due to: ${error.message}")

/**
 * Exception used internally for signaling a failed retry operation
 */
private class RetryFailureException(val error: DomainError) : Exception("Retry failed: ${error.message}")