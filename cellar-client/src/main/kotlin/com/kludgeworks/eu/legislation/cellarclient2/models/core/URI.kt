package com.kludgeworks.eu.legislation.cellarclient2.models.core

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class URI(
    @XmlElement
    @XmlSerialName("VALUE")
    val value: String,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    val identifier: String,
    @XmlElement
    @XmlSerialName("TYPE")
    val type: String
)
