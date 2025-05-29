package com.kludgeworks.eu.legislation.cellarclient2.models.core

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class SameAs(
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI
)
