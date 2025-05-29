package com.kludgeworks.eu.legislation.cellarclient2.models.work

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import com.kludgeworks.eu.legislation.cellarclient2.models.core.*

@Serializable
data class WorkLegalInfo(
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_TYPE")
    val resourceLegalType: ResourceLegalType,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IN-FORCE")
    val resourceLegalInForce: ResourceLegalInForce,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_ELI")
    val resourceLegalEli: ResourceLegalEli,
    @XmlElement
    @XmlSerialName("ELI")
    val eli: Eli,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_REPERTOIRE")
    val resourceLegalRepertoire: ResourceLegalRepertoire,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_YEAR")
    val resourceLegalYear: ResourceLegalYear,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_COMMENT_INTERNAL")
    val resourceLegalCommentInternal: ResourceLegalCommentInternal
)