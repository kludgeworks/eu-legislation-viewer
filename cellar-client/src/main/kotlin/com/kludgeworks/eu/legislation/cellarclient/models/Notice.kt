package com.kludgeworks.eu.legislation.cellarclient.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * Root element representing a legal notice
 * Contains the complete work, expression, and manifestation hierarchy
 */
@Serializable
@XmlSerialName("NOTICE")
data class Notice(
    @XmlSerialName("decoding")
    val decoding: String,
    @XmlSerialName("type")
    val type: String,
    @XmlElement
    @XmlSerialName("WORK")
    val work: Work,
    @XmlElement
    @XmlSerialName("EXPRESSION")
    val expression: List<Expression>,
    @XmlElement
    @XmlSerialName("MANIFESTATION")
    val manifestation: List<Manifestation>,
)
