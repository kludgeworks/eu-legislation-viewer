package com.kludgeworks.eu.legislation.cellarclient.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * Represents a specific linguistic expression of a work
 */
@Serializable
data class Expression(
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI? = null,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs> = emptyList(),
    @XmlElement
    @XmlSerialName("EXPRESSION_TITLE")
    val title: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("TITLE")
    val titleAlt: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("LANG")
    val language: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("EXPRESSION_USES_LANGUAGE")
    val usesLanguage: Concept? = null,
    @XmlElement
    @XmlSerialName("EXPRESSION_BELONGS_TO_WORK")
    val belongsToWork: ResourceReference? = null,
    @XmlElement
    @XmlSerialName("EXPRESSION_MANIFESTED_BY_MANIFESTATION")
    val manifestations: List<ResourceReference> = emptyList(),
    @XmlElement
    @XmlSerialName("CREATIONDATE")
    val creationDate: Date? = null,
    @XmlElement
    @XmlSerialName("LASTMODIFICATIONDATE")
    val lastModificationDate: Date? = null
)