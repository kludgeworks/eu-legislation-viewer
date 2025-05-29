package com.kludgeworks.eu.legislation.cellarclient.model

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText
import java.time.LocalDate
import java.time.LocalDateTime

// --- Generic RDF/XML Parsing Classes ---

@JacksonXmlRootElement(localName = "RDF", namespace = "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
@JsonIgnoreProperties(ignoreUnknown = true) // Ignores namespace declarations like xmlns:j.0 at the root
data class RdfRoot(
    @field:JacksonXmlElementWrapper(useWrapping = false) // No wrapper element around Description tags
    @field:JacksonXmlProperty(localName = "Description", namespace = "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
    val descriptions: List<RdfDescription> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RdfDescription(
    @field:JacksonXmlProperty(isAttribute = true, localName = "about", namespace = "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
    var about: String? = null,

    @field:JacksonXmlProperty(isAttribute = true, localName = "nodeID", namespace = "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
    var nodeID: String? = null,

    // Using @JsonAnySetter to capture all child elements dynamically.
    // The key will be the element name (e.g., "owl:sameAs", "j.0:identifier").
    // Values are now always a list to handle repeated XML elements.
    val properties: MutableMap<String, MutableList<Any>> = mutableMapOf()
) {
    @JsonAnySetter
    fun addProperty(name: String, value: Any) {
        // Jackson might pass a single object or a list of objects if multiple elements with the same name exist.
        val list = properties.computeIfAbsent(name) { mutableListOf() }
        if (value is List<*>) {
            value.forEach { if (it != null) list.add(it) }
        } else {
            list.add(value)
        }
        // logger.debug("RdfDescription.addProperty: name='{}', added value(s), current size for key: {}", name, list.size)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class RdfChildElement(
    @field:JacksonXmlProperty(isAttribute = true, localName = "resource", namespace = "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
    var resource: String? = null,

    @field:JacksonXmlProperty(isAttribute = true, localName = "datatype", namespace = "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
    var datatype: String? = null,

    @JacksonXmlText
    var textValue: String? = null,

    // For deeply nested structures within a property element (e.g. OWL Axioms with annotations)
    // This makes RdfChildElement potentially recursive if an element has further complex children
    // not just attributes and text.
    // val nestedChildren: MutableMap<String, MutableList<RdfChildElement>> = mutableMapOf() // Removing for simplification
)
// @JsonAnySetter from RdfChildElement was already removed.

// --- Target Domain Kotlin Data Classes (Plain, No Jackson XML Annotations) ---
// These will be populated by custom logic after parsing into RdfRoot.

data class Notice(
    val work: Work,
    val expressions: List<Expression> = emptyList(),
    val manifestations: List<Manifestation> = emptyList()
)

data class Work(
    val selfUri: String?, // The rdf:about URI of this Work's RdfDescription
    val identifiers: List<String> = emptyList(), // e.g. from j.0:identifier, j.0:id_celex
    val eli: String? = null, // from j.0:eli or j.0:resource_legal_eli
    val lastModificationDate: LocalDateTime? = null, // from j.3:lastModificationDate or j.0:LASTMODIFICATIONDATE
    val dateCreationLegacy: LegacyDate? = null, // from j.0:date_creation_legacy or j.0:work_date_creation_legacy
    val idSector: String? = null, // from j.0:id_sector or j.0:resource_legal_id_sector
    val resourceLegalNumberNaturalCelex: String? = null, // from j.0:resource_legal_number_natural_celex or j.0:number_natural_celex
    val resourceLegalEli: String? = null, // This is covered by the 'eli' field logic. Kept for original structure.
    val resourceLegalRepertoire: String? = null, // from j.0:resource_legal_repertoire or j.0:repertoire
    val version: String? = null, // from j.0:version or j.0:work_version
    val resourceLegalCommentInternal: String? = null, // from j.0:resource_legal_comment_internal or j.0:comment_internal
    // ... other fields from the user's original Work class
    val expressionUris: List<String> = emptyList(), // URIs from j.0:work_has_expression
    val typeUris: List<String> = emptyList() // URIs from rdf:type
)

data class Expression(
    val selfUri: String?,
    val title: String?, // From EXPRESSION_TITLE or TITLE
    val lang: String?, // From LANG
    val lastModificationDate: LocalDateTime?, // From LASTMODIFICATIONDATE
    // ... other fields
    val manifestationUris: List<String> = emptyList() // URIs from EXPRESSION_MANIFESTED_BY_MANIFESTATION
)

data class Manifestation(
    val selfUri: String?,
    val type: String?, // From MANIFESTATION_TYPE
    val lastModificationDate: LocalDateTime?,
    val pageFirst: String? = null,
    val pageLast: String? = null,
    val pagesTotal: String? = null, // Or Int?
    // ... other fields
    val itemUris: List<String> = emptyList() // URIs from MANIFESTATION_HAS_ITEM
)

// Example sub-classes (plain Kotlin)
data class Uri(
    val value: String?, // Could be rdf:resource or text of a <VALUE> tag
    val identifier: String?,
    val type: String?
)

data class LegacyDate(
    val value: LocalDate?,
    val year: Int?,
    val month: Int?,
    val day: Int?
)

data class Title( // For Expression.TITLE
    val value: String?
)

data class LastModificationDate( // For Expression.LASTMODIFICATIONDATE
    val value: LocalDateTime?,
    val year: Int?,
    val month: Int?,
    val day: Int?
)

data class ExpressionTitle( // For Expression.EXPRESSION_TITLE
    val value: String?
)

// NOTE: The full list of data classes provided by the user would need to be
// defined here as plain Kotlin data classes. For brevity, only a subset is included.
// The custom mapping logic will need to populate all of them.