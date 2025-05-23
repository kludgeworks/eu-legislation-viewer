package com.kludgeworks.eu.legislation.rdf

/**
 * Base class for domain errors in the EU Legislation system
 * Following Arrow best practices for error handling
 */
sealed class DomainError(
    open val message: String,
    open val cause: Throwable? = null
) {
    /**
     * Entity not found error
     */
    data class NotFound(
        override val message: String,
        val entityType: String,
        val identifier: String,
        override val cause: Throwable? = null
    ) : DomainError(message, cause)

    /**
     * Query failure error
     */
    data class QueryFailure(
        override val message: String,
        override val cause: Throwable? = null,
        val query: String? = null
    ) : DomainError(message, cause)

}