package com.kludgeworks.eu.legislation.cellarclient2.models.work

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import com.kludgeworks.eu.legislation.cellarclient2.models.core.*
import com.kludgeworks.eu.legislation.cellarclient2.models.legal.ConsolidatedAnnotation

@Serializable
data class WorkHasExpression(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>,
)

@Serializable
data class WorkCitesWork(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>
)

@Serializable
data class BasedOn(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>,
    @XmlElement
    @XmlSerialName("ANNOTATION")
    val annotation: ConsolidatedAnnotation? = null,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    val identifier: String? = null,
    @XmlElement
    @XmlSerialName("OP-CODE")
    val opCode: String? = null,
    @XmlElement
    @XmlSerialName("PREFLABEL")
    val prefLabel: String? = null,
    @XmlElement
    @XmlSerialName("ALTLABEL")
    val altLabel: List<String>? = emptyList()
)

@Serializable
data class ResourceLegalAmendsResourceLegal(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>,
    @XmlElement
    @XmlSerialName("ANNOTATION")
    val annotation: List<ConsolidatedAnnotation>,
)

@Serializable
data class ResourceLegalBasedOnResourceLegal(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>,
    @XmlElement
    @XmlSerialName("ANNOTATION")
    val annotation: ConsolidatedAnnotation,
)
