package com.kludgeworks.eu.legislation.cellarclient2.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import com.kludgeworks.eu.legislation.cellarclient2.models.core.*

@Serializable
data class Expression(
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI? = null,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs> = emptyList(),
    @XmlElement
    @XmlSerialName("EXPRESSION_MANIFESTED_BY_MANIFESTATION")
    val expressionManifestedByManifestation: List<ExpressionManifestedByManifestation> = emptyList(),
    @XmlElement
    @XmlSerialName("EXPRESSION_TITLE")
    val expressionTitle: ExpressionTitle? = null,
    @XmlElement
    @XmlSerialName("EXPRESSION_USES_LANGUAGE")
    val expressionUsesLanguage: ExpressionUsesLanguage? = null,
    @XmlElement
    @XmlSerialName("TITLE")
    val title: Title? = null,
    @XmlElement
    @XmlSerialName("EXPRESSION_BELONGS_TO_WORK")
    val expressionBelongsToWork: ExpressionBelongsToWork? = null,
    @XmlElement
    @XmlSerialName("LANG")
    val lang: Lang? = null,
    @XmlElement
    @XmlSerialName("CREATIONDATE")
    val creationDate: Date? = null,
    @XmlElement
    @XmlSerialName("LASTMODIFICATIONDATE")
    val lastModificationDate: Date? = null
)

@Serializable
data class ExpressionBelongsToWork(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>
)

@Serializable
data class ExpressionUsesLanguage(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("OP-CODE")
    val opCode: String,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    val identifier: String,
    @XmlElement
    @XmlSerialName("PREFLABEL")
    val prefLabel: String,
    @XmlElement
    @XmlSerialName("ALTLABEL")
    val altLabel: String? = null
)

@Serializable
data class ExpressionManifestedByManifestation(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI? = null,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>
)
