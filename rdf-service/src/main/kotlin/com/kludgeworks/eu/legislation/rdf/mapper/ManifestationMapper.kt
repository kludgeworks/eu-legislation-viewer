package com.kludgeworks.eu.legislation.rdf.mapper

import com.kludgeworks.eu.legislation.rdf.Manifestation
import org.eclipse.rdf4j.query.BindingSet
import org.springframework.stereotype.Component
import java.net.URI

/**
 * Mapper for converting SPARQL query results to Manifestation domain objects
 * Following Spring Boot best practices with @Component annotation
 */
@Component
internal class ManifestationMapper {

    /**
     * Maps a BindingSet from SPARQL query results to a Manifestation domain object
     * Handles all optional fields gracefully using Arrow-style null safety
     */
    fun mapToManifestation(bindingSet: BindingSet): Manifestation {
        return Manifestation(
            // URI is required
            uri = bindingSet.getBinding("manifestation")?.value?.let {
                URI.create(it.stringValue())
                // TODO throw better exception
            } ?: throw IllegalArgumentException("Manifestation URI is required"),

            // Manifestation properties
            date = bindingSet.getBinding("date")?.value?.let {
                TypeConverters.parseDate(it)
            },
            datePublication = bindingSet.getBinding("datePublication")?.value?.let {
                TypeConverters.parseDate(it)
            },
            dimension = bindingSet.getBinding("dimension")?.value?.let {
                TypeConverters.parseString(it)
            },
            embargo = bindingSet.getBinding("embargo")?.value?.let {
                TypeConverters.parseDateTime(it)
            },
            isSigned = bindingSet.getBinding("isSigned")?.value?.let {
                TypeConverters.parseBoolean(it)
            },
            catalogueNumberGlobal = bindingSet.getBinding("catalogueNumberGlobal")?.value?.let {
                TypeConverters.parseString(it)
            },
            type = bindingSet.getBinding("type")?.value?.let {
                TypeConverters.parseString(it)
            },
            version = bindingSet.getBinding("version")?.value?.let {
                TypeConverters.parseString(it)
            },
            
            // References to other entities
            publishedByUri = bindingSet.getBinding("publishedByUri")?.value?.let {
                URI.create(it.stringValue())
            },
            manifestsExpressionUri = bindingSet.getBinding("manifestsExpressionUri")?.value?.let {
                URI.create(it.stringValue())
            },
            partOfManifestationUri = bindingSet.getBinding("partOfManifestationUri")?.value?.let {
                URI.create(it.stringValue())
            },
            
            // Items and contained manifestations will be loaded separately
            items = emptySet(),
            containsManifestations = emptySet()
        )
    }

    /**
     * Maps item URIs to a set
     */
    fun mapItemUris(bindingSets: List<BindingSet>): Set<URI> {
        return bindingSets.mapNotNull { bindingSet -> 
            bindingSet.getBinding("item")?.value?.let {
                URI.create(it.stringValue())
            }
        }.toSet()
    }

    /**
     * Maps child manifestation URIs to a set
     */
    fun mapChildManifestationUris(bindingSets: List<BindingSet>): Set<URI> {
        return bindingSets.mapNotNull { bindingSet -> 
            bindingSet.getBinding("childManifestation")?.value?.let {
                URI.create(it.stringValue())
            }
        }.toSet()
    }
}