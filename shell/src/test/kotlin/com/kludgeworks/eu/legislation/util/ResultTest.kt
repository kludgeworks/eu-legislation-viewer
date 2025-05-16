package com.kludgeworks.eu.legislation.util

import arrow.core.Either
import arrow.core.raise.*
import com.kludgeworks.eu.legislation.rdf.EntityNotFoundException
import com.kludgeworks.eu.legislation.rdf.MappingException
import com.kludgeworks.eu.legislation.rdf.SparqlQueryException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class ResultTest {
    @Test
    fun `asSuccess should create a Right with the value`() {
        val result = "test".asSuccess()
        assertTrue(result is Either.Right<*>)
        assertEquals("test", (result as Either.Right<String>).value)
    }
    
    @Test
    fun `notFound should create a Left with NotFound error`() {
        val result = notFound("TestEntity", "test-id")
        assertTrue(result is Either.Left<*>)
        
        result.fold(
            ifLeft = { error ->
                assertTrue(error is DomainError.NotFound)
                assertEquals("TestEntity", (error as DomainError.NotFound).entityType)
                assertEquals("test-id", error.identifier)
                assertEquals("TestEntity not found with identifier: test-id", error.message)
            },
            ifRight = {
                throw AssertionError("Expected Left but was Right")
            }
        )
    }
    
    @Test
    fun `queryFailure should create a Left with QueryFailure error`() {
        val queryText = "SELECT * FROM test"
        val result = queryFailure("Query failed", queryText)
        assertTrue(result is Either.Left<*>)
        
        result.fold(
            ifLeft = { error ->
                assertTrue(error is DomainError.QueryFailure)
                assertEquals(queryText, (error as DomainError.QueryFailure).query)
                assertTrue(error.message.contains("Query failed"))
            },
            ifRight = {
                throw AssertionError("Expected Left but was Right")
            }
        )
    }
    
    @Test
    fun `mappingFailure should create a Left with MappingFailure error`() {
        val result = mappingFailure("TestEntity", "testProperty")
        assertTrue(result is Either.Left<*>)
        
        result.fold(
            ifLeft = { error ->
                assertTrue(error is DomainError.MappingFailure)
                assertEquals("TestEntity", (error as DomainError.MappingFailure).entityType)
                assertEquals("testProperty", error.property)
            },
            ifRight = {
                throw AssertionError("Expected Left but was Right")
            }
        )
    }
    
    @Test
    fun `getOrThrow should return the value for Right`() {
        val result: Result<String> = Either.Right("test")
        assertEquals("test", result.getOrThrow())
    }
    
    @Test
    fun `getOrThrow should throw EntityNotFoundException for NotFound error`() {
        val result: Result<String> = either {
            raise(DomainError.NotFound(
                message = "TestEntity not found with identifier: test-id",
                cause = null,
                entityType = "TestEntity",
                identifier = "test-id"
            ))
        }
        
        val exception = assertFailsWith<EntityNotFoundException> {
            result.getOrThrow()
        }
        
        assertEquals("TestEntity", exception.message?.substringBefore(" not found"))
    }
    
    @Test
    fun `getOrThrow should throw SparqlQueryException for QueryFailure error`() {
        val result: Result<String> = either {
            raise(DomainError.QueryFailure(
                message = "Query failed",
                cause = null,
                query = "SELECT * FROM test"
            ))
        }
        
        assertFailsWith<SparqlQueryException> {
            result.getOrThrow()
        }
    }
    
    @Test
    fun `getOrThrow should throw MappingException for MappingFailure error`() {
        val result: Result<String> = either {
            raise(DomainError.MappingFailure(
                message = "Mapping failed",
                cause = null,
                entityType = "TestEntity",
                property = "testProperty"
            ))
        }
        
        assertFailsWith<MappingException> {
            result.getOrThrow()
        }
    }
    
    @Test
    fun `runCatching should return Right on success`() = runBlocking {
        val result = com.kludgeworks.eu.legislation.util.runCatching { "Success" }
        
        assertTrue(result is Either.Right<*>)
        assertEquals("Success", (result as Either.Right<String>).value)
    }
    
    @Test
    fun `runCatching should handle EntityNotFoundException`() = runBlocking {
        val result = com.kludgeworks.eu.legislation.util.runCatching<String> { 
            throw EntityNotFoundException("TestEntity", "test-id") 
        }
        
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
    fun `runCatching should handle SparqlQueryException`() = runBlocking {
        val result = com.kludgeworks.eu.legislation.util.runCatching<String> { 
            throw SparqlQueryException("Query failed", "SELECT * FROM test") 
        }
        
        assertTrue(result is Either.Left<*>)
        
        result.fold(
            ifLeft = { error ->
                assertTrue(error is DomainError.QueryFailure)
            },
            ifRight = {
                throw AssertionError("Expected Left but was Right")
            }
        )
    }
    
    @Test
    fun `runCatching should handle MappingException`() = runBlocking {
        val result = com.kludgeworks.eu.legislation.util.runCatching<String> { 
            throw MappingException("TestEntity", "testProperty") 
        }
        
        assertTrue(result is Either.Left<*>)
        
        result.fold(
            ifLeft = { error ->
                assertTrue(error is DomainError.MappingFailure)
            },
            ifRight = {
                throw AssertionError("Expected Left but was Right")
            }
        )
    }
    
    @Test
    fun `runCatching should handle unexpected exceptions`() = runBlocking {
        val result = com.kludgeworks.eu.legislation.util.runCatching<String> { 
            throw RuntimeException("Unknown error") 
        }
        
        assertTrue(result is Either.Left<*>)
        
        result.fold(
            ifLeft = { error ->
                assertTrue(error is DomainError.UnexpectedError)
            },
            ifRight = {
                throw AssertionError("Expected Left but was Right")
            }
        )
    }
}