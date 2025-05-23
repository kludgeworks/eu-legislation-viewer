package com.kludgeworks.eu.legislation.rdf.mapper

import com.kludgeworks.eu.legislation.rdf.ContentItem
import org.eclipse.rdf4j.query.BindingSet
import org.springframework.stereotype.Component
import java.net.URI

/**
 * Mapper for converting SPARQL query results to ContentItem domain objects.
 */
@Component
internal class ContentItemMapper {

    /**
     * Maps a BindingSet from a SPARQL query result to a [ContentItem] domain object.
     *
     * @param bindingSet The BindingSet containing the query result for one content item.
     * @param manifestationUri The URI of the manifestation this content item belongs to.
     *                         This is passed explicitly as it's a fixed parameter for the query
     *                         that retrieves content items for a specific manifestation.
     * @return A [ContentItem] object.
     * @throws IllegalArgumentException if required fields (item URI, identifier) are missing.
     */
    fun mapToContentItem(bindingSet: BindingSet, manifestationUri: URI): ContentItem {
        val itemUri = bindingSet.getBinding("item")?.value?.let {
            URI.create(it.stringValue())
        } ?: throw IllegalArgumentException("ContentItem URI ('item') is required in binding set.")

        val identifier = bindingSet.getBinding("identifier")?.value?.let {
            TypeConverters.parseString(it)
        } ?: throw IllegalArgumentException("ContentItem identifier is required in binding set.")

        val doi = bindingSet.getBinding("doi")?.value?.let {
            TypeConverters.parseString(it)
        }

        val embargo = bindingSet.getBinding("embargo")?.value?.let {
            TypeConverters.parseDateTime(it) // Assuming cdm:item_embargo is xsd:dateTime
        }

        val mimeType = bindingSet.getBinding("mimeType")?.value?.let {
            TypeConverters.parseString(it)
        }

        return ContentItem(
            uri = itemUri,
            identifier = identifier,
            doi = doi,
            embargo = embargo,
            belongsToManifestationUri = manifestationUri, // Set from the parameter
            mimeType = mimeType
        )
    }
}