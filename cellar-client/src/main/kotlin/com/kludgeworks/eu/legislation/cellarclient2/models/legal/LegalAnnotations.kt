package com.kludgeworks.eu.legislation.cellarclient2.models.legal

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class ConsolidatedAnnotation(
    @XmlElement
    @XmlSerialName("PARAGRAPH")
    val paragraph: String? = null,
    @XmlElement
    @XmlSerialName("ARTICLE")
    val article: String? = null,
    @XmlElement
    @XmlSerialName("COMMENT_ON_LEGAL_BASIS")
    val commentOnLegalBasis: String? = null,
    @XmlElement
    @XmlSerialName("TYPE_OF_DATE")
    val typeOfDate: String? = null,
    @XmlElement
    @XmlSerialName("COMMENT_ON_DATE")
    val commentOnDate: String? = null,
    @XmlElement
    @XmlSerialName("START_OF_VALIDITY")
    val startOfValidity: String? = null,
    @XmlElement
    @XmlSerialName("REFERENCE_TO_MODIFIED_LOCATION")
    val referenceToModifiedLocation: String? = null,
    @XmlElement
    @XmlSerialName("ROLE2")
    val role2: String? = null,
    @XmlElement
    @XmlSerialName("TYPE_OF_LINK_TARGET")
    val typeOfLinkTarget: String? = null
)
