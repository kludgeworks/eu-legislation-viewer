package com.kludgeworks.eu.legislation.cellarclient2.models.work

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import com.kludgeworks.eu.legislation.cellarclient2.models.core.*

@Serializable
data class IsAbout(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    override val uri: URI,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    override val identifier: String,
    @XmlElement
    @XmlSerialName("PREFLABEL")
    override val prefLabel: String,
    @XmlElement
    @XmlSerialName("ALTLABEL")
    override val altLabel: List<String>,
    @XmlElement
    @XmlSerialName("OP-CODE")
    val opCode: String? = null,
    @XmlElement
    @XmlSerialName("SAMEAS")
    override val sameAs: List<SameAs> = emptyList()
) : UriResource, Identifiable, Labeled

@Serializable
data class CreatedBy(
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
    override val altLabel: List<String>,
    @XmlElement
    @XmlSerialName("SAMEAS")
    override val sameAs: List<SameAs> = emptyList()
) : Concept

@Serializable
data class WorkCreatedByAgent(
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
    override val altLabel: List<String>,
    @XmlElement
    @XmlSerialName("SAMEAS")
    override val sameAs: List<SameAs> = emptyList()
) : Concept

@Serializable
data class WorkHasResourceType(
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
