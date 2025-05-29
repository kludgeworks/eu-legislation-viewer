package com.kludgeworks.eu.legislation.cellarclient2.models.core

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class SimpleValue<T>(
    val type: String,
    @XmlElement
    @XmlSerialName("VALUE")
    val value: T,
)
