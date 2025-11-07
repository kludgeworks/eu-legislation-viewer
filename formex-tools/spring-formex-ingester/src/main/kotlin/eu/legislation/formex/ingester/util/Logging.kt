package eu.legislation.formex.ingester.util

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Idiomatic Kotlin logger using kotlin-logging.
 * Usage: private val logger = logger()
 */
inline fun <reified T : Any> T.logger(): KLogger = KotlinLogging.logger(T::class.java.name)
