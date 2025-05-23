package com.kludgeworks.eu.legislation.rdf.config

import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for RDF/SPARQL repository components
 * Following Spring Boot best practices for configuration
 */
@Configuration
@EnableConfigurationProperties(RdfProperties::class)
internal class RdfConfiguration(
    private val properties: RdfProperties
) {
    private val logger = LoggerFactory.getLogger(RdfConfiguration::class.java)
    
    /**
     * Creates the SPARQL repository for querying EU Publications data
     * Uses the default HTTP client configuration from RDF4J
     */
    @Bean
    @ConditionalOnMissingBean
    fun sparqlRepository(): SPARQLRepository {
        logger.info("Creating SPARQL repository for endpoint: ${properties.endpoint.url}")
        
        return SPARQLRepository(properties.endpoint.url).apply {
            try {
                init()
                logger.info("SPARQL repository initialized successfully")
            } catch (e: Exception) {
                logger.error("Failed to initialize SPARQL repository", e)
                throw IllegalStateException("Cannot connect to SPARQL endpoint: ${properties.endpoint.url}", e)
            }
        }
    }
}