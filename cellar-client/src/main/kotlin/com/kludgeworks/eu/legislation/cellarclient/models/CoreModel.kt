package com.kludgeworks.eu.legislation.cellarclient.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * Represents a URI with additional metadata
 */
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

/**
 * Represents a SameAs reference
 */
@Serializable
data class SameAs(
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI
)

/**
 * Generic value wrapper for consistent handling of simple values
 */
@Serializable
data class SimpleValue<T>(
    @XmlSerialName("type")
    val type: String? = null,
    @XmlElement
    @XmlSerialName("VALUE")
    val value: T,
    @XmlElement
    @XmlSerialName("LANG")
    val lang: String? = null
)

/**
 * Represents a date with detailed breakdown
 */
@Serializable
data class Date(
    @XmlSerialName("type")
    val type: String,
    @XmlElement
    @XmlSerialName("VALUE")
    val value: String,
    @XmlElement
    @XmlSerialName("YEAR")
    val year: Int,
    @XmlElement
    @XmlSerialName("MONTH")
    val month: Int,
    @XmlElement
    @XmlSerialName("DAY")
    val day: Int,
    @XmlElement
    @XmlSerialName("ANNOTATION")
    val annotations: List<Annotation> = emptyList()
)

/**
 * Base annotation type
 */
@Serializable
data class Annotation(
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
    val typeOfLinkTarget: String? = null,
    @XmlElement
    @XmlSerialName("LANGUAGE_LIST")
    val languageList: String? = null
)

@Serializable
data class Item(
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs> = emptyList(),
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    val identifier: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("ITEM_IDENTIFIER")
    val itemIdentifier: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("ITEM_BELONGS_TO_MANIFESTATION")
    val belongsToManifestation: ResourceReference? = null,
    @XmlElement
    @XmlSerialName("BELONGS_TO")
    val belongsTo: ResourceReference? = null,
    @XmlElement
    @XmlSerialName("TECHMD")
    val technicalMetadata: TechnicalMetadata? = null
)

@Serializable
data class TechnicalMetadata(
    @XmlElement
    @XmlSerialName("STREAM_NAME")
    val streamName: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("STREAM_LABEL")
    val streamLabel: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("STREAM_COMPOSITION_LEVEL")
    val streamCompositionLevel: SimpleValue<Int>? = null,
    @XmlElement
    @XmlSerialName("STREAM_SIZE")
    val streamSize: SimpleValue<Int>? = null,
    @XmlElement
    @XmlSerialName("STREAM_ORDER")
    val streamOrder: SimpleValue<Int>? = null
)

/**
 * Reference to another resource
 */
@Serializable
data class ResourceReference(
    @XmlSerialName("type")
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI? = null,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs> = emptyList(),
    @XmlElement
    @XmlSerialName("ANNOTATION")
    val annotation:Annotation? = null,
    @XmlElement
    @XmlSerialName("OP-CODE")
    val opCode: String? = null,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    val identifier: String? = null,
    @XmlElement
    @XmlSerialName("PREFLABEL")
    val prefLabel: String? = null,
    @XmlElement
    @XmlSerialName("ALTLABEL")
    val altLabel: List<String> = emptyList()
)

/**
 * Embedded notice within a work
 */
@Serializable
data class EmbeddedNotice(
    @XmlSerialName("type")
    val type: String,
    @XmlElement
    @XmlSerialName("EMBEDDED_NOTICE")
    val notice: CompactNotice,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs> = emptyList()
)

/**
 * Compact version of a notice for embedding
 */
@Serializable
data class CompactNotice(
    @XmlElement
    @XmlSerialName("WORK")
    val work: CompactWork,
    @XmlElement
    @XmlSerialName("EXPRESSION")
    val expression: Expression? = null,
    @XmlElement
    @XmlSerialName("MANIFESTATION")
    val manifestations: List<Manifestation> = emptyList()
)

/**
 * Compact version of work for embedding
 */
@Serializable
data class CompactWork(
    @XmlElement
    @XmlSerialName("WORK_DATE_DOCUMENT")
    val workDateDocument: Date? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_ID_CELEX")
    val idCelex: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("NUMBER")
    val number: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("ID_CELEX")
    val idCelexWork: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("ID_SECTOR")
    val idSector: SimpleValue<Int>? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_TYPE")
    val resourceLegalType: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_YEAR")
    val resourceLegalYear: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("YEAR")
    val year: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("OFFICIAL-JOURNAL_YEAR")
    val officialJournalYear: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("OFFICIAL-JOURNAL_CLASS")
    val officialJournalClass: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_NUMBER_NATURAL")
    val numberNatural: SimpleValue<Int>? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_NUMBER_NATURAL_CELEX")
    val numberNaturalCelex: SimpleValue<Int>? = null,
    @XmlElement
    @XmlSerialName("WORK_HAS_RESOURCE-TYPE")
    val resourceType: Concept? = null,
    @XmlElement
    @XmlSerialName("TYPE")
    val types: List<String> = emptyList(),
    @XmlElement
    @XmlSerialName("DATE_PUBLICATION")
    val datePublication: Date? = null,
    @XmlElement
    @XmlSerialName("PART_OF")
    val partOf: ResourceReference? = null,
    @XmlElement
    @XmlSerialName("OFFICIAL-JOURNAL_PART_OF_COLLECTION_DOCUMENT")
    val officialJournalPartOfCollectionDocument: Concept? = null,
    @XmlElement
    @XmlSerialName("OFFICIAL-JOURNAL_NUMBER")
    val officialJournalNumber: SimpleValue<String>? = null
)

@Serializable
data class WorkExpression(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs> = emptyList()
)

@Serializable
data class IsAbout(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs> = emptyList(),
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    val identifier: String? = null,
    @XmlElement
    @XmlSerialName("PREFLABEL")
    val prefLabel: String? = null,
    @XmlElement
    @XmlSerialName("ALTLABEL")
    val altLabel: List<String> = emptyList(),
    @XmlElement
    @XmlSerialName("OP-CODE")
    val opCode: String? = null
)

@Serializable
data class CreatedBy(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs> = emptyList(),
    @XmlElement
    @XmlSerialName("OP-CODE")
    val opCode: String? = null,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    val identifier: String? = null,
    @XmlElement
    @XmlSerialName("PREFLABEL")
    val prefLabel: String? = null,
    @XmlElement
    @XmlSerialName("ALTLABEL")
    val altLabel: List<String> = emptyList()
)

@Serializable
data class SubjectMatter(
    val type: String,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IS_ABOUT_SUBJECT-MATTER_1")
    val subject: Concept
)
