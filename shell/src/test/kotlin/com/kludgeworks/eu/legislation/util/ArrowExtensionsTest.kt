package com.kludgeworks.eu.legislation.util

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.Either
import arrow.core.raise.*
import com.kludgeworks.eu.legislation.util.processWithLogging
import com.kludgeworks.eu.legislation.util.mapResults
import com.kludgeworks.eu.legislation.util.retryWithBackoff
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArrowExtensionsTest {
    private val logger: Logger = LoggerFactory.getLogger(ArrowExtensionsTest::class.java)

    @Test
    fun `Option_fromNullable should convert non-null value to Some`() {
        val value = "test"
        val option = Option.fromNullable(value)
        assertTrue(option is Some)
        assertEquals(value, option.getOrNull())
    }
    
    @Test
    fun `Option_fromNullable should convert null to None`() {
        val value: String? = null
        val option = Option.fromNullable(value)
        assertTrue(option is None)
        assertNull(option.getOrNull())
    }
    
    @Test
    fun `processWithLogging should transform and filter items`() = runBlocking {
        val items = listOf("1", "two", "3", "four")
        
        val results = items.processWithLogging(
            logger,
            "Error converting item {} to number: {}",
        ) { item ->
            val intValue = item.toIntOrNull()
            either {
                intValue ?: raise(DomainError.MappingFailure("Failed to parse integer", null, "String", "Int"))
            }
        }
        
        assertEquals(2, results.size)
        assertContentEquals(listOf(1, 3), results)
    }
    
    @Test
    fun `mapResults should transform and filter flow items`() = runBlocking {
        val flow = flowOf("1", "two", "3", "four")
        
        val results = flow.mapResults(
            logger,
            "Error converting item {} to number: {}",
        ) { item ->
            val intValue = item.toIntOrNull()
            either {
                intValue ?: raise(DomainError.MappingFailure("Failed to parse integer", null, "String", "Int"))
            }
        }.toList()
        
        assertEquals(2, results.size)
        assertContentEquals(listOf(1, 3), results)
    }
    
    @Test
    fun `retryWithBackoff should return success eventually`() = runBlocking {
        var attempts = 0
        
        val result = retryWithBackoff(
            maxAttempts = 3,
            initialDelay = Duration.ofMillis(10),
            maxDelay = Duration.ofMillis(50),
            factor = 2.0
        ) {
            attempts++
            if (attempts < 2) {
                either { raise(DomainError.UnexpectedError("Temporary failure", null)) }
            } else {
                Either.Right("Success")
            }
        }
        
        assertEquals(2, attempts)
        assertTrue(result is Either.Right<*>)
        assertEquals("Success", (result as Either.Right<String>).value)
    }
    
    @Test
    fun `retryWithBackoff should respect maxAttempts`() = runBlocking {
        var attempts = 0
        
        val result = retryWithBackoff(
            maxAttempts = 3,
            initialDelay = Duration.ofMillis(10),
            maxDelay = Duration.ofMillis(50),
            factor = 2.0
        ) {
            attempts++
            either { raise(DomainError.UnexpectedError("Persistent failure", null)) }
        }
        
        assertEquals(3, attempts)
        assertTrue(result is Either.Left<*>)
    }
    
    @Test
    fun `retryWithBackoff should respect retryIf predicate`() = runBlocking {
        var attempts = 0
        
        val result = retryWithBackoff(
            maxAttempts = 3,
            initialDelay = Duration.ofMillis(10),
            retryIf = { error -> error !is DomainError.NotFound }
        ) {
            attempts++
            if (attempts == 1) {
                either { raise(DomainError.UnexpectedError("Retryable error", null)) }
            } else {
                either { raise(DomainError.NotFound("Entity not found with identifier: 1", null, "Entity", "1")) }
            }
        }
        
        assertEquals(2, attempts)
        assertTrue(result is Either.Left<*>)
        
        result.fold(
            ifLeft = { error ->
                assertTrue(error is DomainError.NotFound)
            },
            ifRight = {
                throw AssertionError("Expected Left but was Right")
            }
        )
    }
    
    @Test
    fun `either block should convert list of Results to Result of list`() {
        val successResults = listOf<Result<String>>(
            Either.Right("a"),
            Either.Right("b"),
            Either.Right("c")
        )
        
        val sequencedSuccess = either {
            successResults.map { it.bind() }
        }
        
        assertTrue(sequencedSuccess is Either.Right<*>)
        assertEquals(listOf("a", "b", "c"), (sequencedSuccess as Either.Right<List<String>>).value)
        
        val mixedResults = listOf<Result<String>>(
            Either.Right("a"),
            Either.Left(DomainError.NotFound("Entity not found with identifier: 1", null, "Entity", "1")),
            Either.Right("c")
        )
        
        val sequencedMixed = either {
            mixedResults.map { it.bind() }
        }
        
        assertTrue(sequencedMixed is Either.Left<*>)
        
        sequencedMixed.fold(
            ifLeft = { error ->
                assertTrue(error is DomainError.NotFound)
            },
            ifRight = {
                throw AssertionError("Expected Left but was Right")
            }
        )
    }
    
    @Test
    fun `either with catch should wrap exceptions in domain errors`() = runBlocking {
        val success = either {
            catch({
                "Success"
            }) { e ->
                raise(DomainError.UnexpectedError(e.message ?: "Unknown error", e))
            }
        }
        
        assertTrue(success is Either.Right<*>)
        assertEquals("Success", (success as Either.Right<String>).value)
        
        val failure = either {
            catch({
                throw RuntimeException("Test error")
            }) { e ->
                raise(DomainError.UnexpectedError(e.message ?: "Unknown error", e))
            }
        }
        
        assertTrue(failure is Either.Left<*>)
        
        failure.fold(
            ifLeft = { error ->
                assertTrue(error is DomainError.UnexpectedError)
                assertEquals("Test error", error.message)
            },
            ifRight = {
                throw AssertionError("Expected Left but was Right")
            }
        )
    }
}