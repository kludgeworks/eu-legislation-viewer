package com.kludgeworks.eu.legislation.rdf.mapper

import com.kludgeworks.eu.legislation.rdf.Expression
import org.eclipse.rdf4j.query.BindingSet
import org.springframework.stereotype.Component
import java.net.URI

/**
 * Mapper for converting SPARQL query results to Expression domain objects
 * Following Spring Boot best practices with @Component annotation
 */
@Component
internal class ExpressionMapper {

    /**
     * Maps a BindingSet from SPARQL query results to an Expression domain object
     * Handles all optional fields gracefully using Arrow-style null safety
     */
    fun mapToExpression(bindingSet: BindingSet): Expression {
        return Expression(
            // URI is required
            uri = bindingSet.getBinding("expression")?.value?.let {
                URI.create(it.stringValue())
                // TODO throw better exception
            } ?: throw IllegalArgumentException("Expression URI is required"),
            // Title is required
            title = bindingSet.getBinding("title")?.value?.let {
                TypeConverters.parseString(it)
                // TODO throw better exception
            } ?: throw IllegalArgumentException("Expression title is required"),
            // BelongsToWork is required
            belongsToWorkUri = bindingSet.getBinding("belongsToWorkUri")?.value?.let {
                URI.create(it.stringValue())
                // TODO throw better exception
            } ?: throw IllegalArgumentException("Expression belongsToWorkUri is required"),

            // Expression properties
            date = bindingSet.getBinding("date")?.value?.let {
                TypeConverters.parseDate(it)
            },
            edition = bindingSet.getBinding("edition")?.value?.let {
                TypeConverters.parseString(it)
            },
            embargo = bindingSet.getBinding("embargo")?.value?.let {
                TypeConverters.parseDateTime(it)
            },
            source = bindingSet.getBinding("source")?.value?.let {
                TypeConverters.parseString(it)
            },
            version = bindingSet.getBinding("version")?.value?.let {
                TypeConverters.parseString(it)
            },
            language = bindingSet.getBinding("language")?.value?.let {
                TypeConverters.parseString(it)
            } ?: "",

            // Manifestations will be loaded separately
            manifestations = emptySet()
        )
    }

    /**
     * Maps manifestation URIs to a set
     */
    fun mapManifestationUris(bindingSets: List<BindingSet>): Set<URI> {
        return bindingSets.mapNotNull { bindingSet -> 
            bindingSet.getBinding("manifestation")?.value?.let {
                URI.create(it.stringValue())
            }
        }.toSet()
    }
}