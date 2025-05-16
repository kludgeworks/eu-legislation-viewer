package com.kludgeworks.eu.legislation.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import kotlin.system.measureTimeMillis

/**
 * Extension function for processing a list of items with potential failures, collecting all successes
 * and logging failures without failing the entire operation
 *
 * @param logger The logger instance to use for logging failures
 * @param errorMessage The error message template to use for logging failures
 * @param transform The transformation function to apply to each item
 * @return A list of successfully transformed items
 */
suspend fun <T, R> List<T>.processWithLogging(
    logger: Logger,
    errorMessage: String,
    transform: suspend (T) -> Result<R>
): List<R> = withContext(Dispatchers.Default) {
    mapNotNull { item ->
        transform(item).fold(
            ifLeft = { error ->
                logger.warn(errorMessage, item, error.message)
                null
            },
            ifRight = { it }
        )
    }
}

/**
 * Extension function that transforms a Flow of items using the provided function,
 * collecting successes and logging errors
 *
 * @param logger The logger instance to use for logging failures
 * @param errorMessage The error message template to use for logging failures
 * @param transform The transformation function to apply to each item
 * @return A flow of successfully transformed items
 */
@Suppress("UNCHECKED_CAST")
fun <T, R : Any> Flow<T>.mapResults(
    logger: Logger,
    errorMessage: String,
    transform: suspend (T) -> Result<R>
): Flow<R> {
    return this
        .map { item ->
            transform(item).fold(
                ifLeft = { error ->
                    logger.warn(errorMessage, item, error.message)
                    null
                },
                ifRight = { it }
            )
        }
        .filter { value -> value != null }
        .map { value -> value as R }
}
