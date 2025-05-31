package com.kludgeworks.eu.legislation.cellarclient

/**
 * Exception thrown when there is an error communicating with the Cellar API.
 * This exception is thrown for all errors except 404 Not Found, which returns null instead.
 */
class CellarApiException(message: String, cause: Throwable? = null) : Exception(message, cause)