package com.kludgeworks.eu.legislation.cellarclient2.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * Represents a legal notice from the EU legislation system.
 * Contains the complete work, expression, and manifestation data.
 */
@Serializable
@XmlSerialName("NOTICE")
data class Notice(
    /** The decoding format used */
    val decoding: String,
    /** The type of notice */
    val type: String,
    @XmlElement
    @XmlSerialName("WORK")
    val work: Work,
    @XmlElement
    @XmlSerialName("EXPRESSION")
    val expression: Expression,
    @XmlElement
    @XmlSerialName("MANIFESTATION")
    val manifestation: Manifestation
)