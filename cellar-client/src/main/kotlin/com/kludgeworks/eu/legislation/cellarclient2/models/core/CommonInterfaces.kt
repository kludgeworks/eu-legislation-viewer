package com.kludgeworks.eu.legislation.cellarclient2.models.core

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * Interface for entities that have an identifier
 */
interface Identifiable {
    val identifier: String
}

/**
 * Interface for entities with labels
 */
interface Labeled {
    val prefLabel: String
    val altLabel: List<String>
}

/**
 * Interface for entities with optional labels
 */
interface OptionallyLabeled {
    val prefLabel: String?
    val altLabel: List<String>?
}

/**
 * Interface for entities with URI and SameAs references
 */
interface UriResource {
    val uri: URI
    val sameAs: List<SameAs>
}

/**
 * Interface for entities with optional URI and SameAs references
 */
interface OptionalUriResource {
    val uri: URI?
    val sameAs: List<SameAs>
}

/**
 * Interface for concept entities with standard properties
 */
interface Concept : UriResource, Identifiable, Labeled {
    val type: String
    val opCode: String
}

/**
 * Base implementation for concept data
 */
@Serializable
data class ConceptData(
    override val type: String,
    @XmlElement
    @XmlSerialName("URI")
    override val uri: URI,
    @XmlElement
    @XmlSerialName("OP-CODE")
    override val opCode: String,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    override val identifier: String,
    @XmlElement
    @XmlSerialName("PREFLABEL")
    override val prefLabel: String,
    @XmlElement
    @XmlSerialName("ALTLABEL")
    override val altLabel: List<String> = emptyList(),
    @XmlElement
    @XmlSerialName("SAMEAS")
    override val sameAs: List<SameAs> = emptyList()
) : Concept