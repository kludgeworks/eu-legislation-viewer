package com.kludgeworks.eu.legislation.cellarclient2.models.concepts

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import com.kludgeworks.eu.legislation.cellarclient2.models.core.*

/**
 * Base sealed class for all concept types
 */
@Serializable
sealed class ConceptBase {
    abstract val type: String
    abstract val uri: URI
    abstract val identifier: String
    abstract val prefLabel: String
}

/**
 * Concept with OP-CODE
 */
@Serializable
data class ConceptWithOpCode(
    override val type: String,
    @XmlElement
    @XmlSerialName("URI")
    override val uri: URI,
    @XmlElement
    @XmlSerialName("OP-CODE")
    val opCode: String,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    override val identifier: String,
    @XmlElement
    @XmlSerialName("PREFLABEL")
    override val prefLabel: String,
    @XmlElement
    @XmlSerialName("ALTLABEL")
    val altLabel: List<String> = emptyList()
) : ConceptBase()

/**
 * Simple concept without OP-CODE
 */
@Serializable
data class SimpleConcept(
    override val type: String,
    @XmlElement
    @XmlSerialName("URI")
    override val uri: URI,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    override val identifier: String,
    @XmlElement
    @XmlSerialName("PREFLABEL")
    override val prefLabel: String
) : ConceptBase()