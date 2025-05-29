package com.kludgeworks.eu.legislation.cellarclient2.models.core

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * Interface for delegating concept properties
 */
interface ConceptDelegate {
    val uri: URI
    val opCode: String
    val identifier: String
    val prefLabel: String
    val altLabel: List<String>
}

/**
 * Implementation of concept properties
 */
@Serializable
data class ConceptProperties(
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
    override val altLabel: List<String> = emptyList()
) : ConceptDelegate
