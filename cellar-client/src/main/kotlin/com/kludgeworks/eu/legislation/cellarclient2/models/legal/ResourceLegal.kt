package com.kludgeworks.eu.legislation.cellarclient2.models.legal

import com.kludgeworks.eu.legislation.cellarclient2.models.Expression
import com.kludgeworks.eu.legislation.cellarclient2.models.Manifestation
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import com.kludgeworks.eu.legislation.cellarclient2.models.core.*
import com.kludgeworks.eu.legislation.cellarclient2.models.work.BasedOn
import com.kludgeworks.eu.legislation.cellarclient2.models.Date

@Serializable
data class ResourceLegalBasedOnConceptTreaty(
    val type: String,
    private val conceptData: ConceptProperties
) : ConceptDelegate by conceptData

@Serializable
data class ResourceLegalPublishedInOfficialJournal(
    val type: String,
    @XmlElement
    @XmlSerialName("EMBEDDED_NOTICE")
    val embeddedNotice: EmbeddedNotice,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>
)

@Serializable
data class ResourceLegalConsolidatedByActConsolidated(
    val type: String,
    @XmlElement
    @XmlSerialName("EMBEDDED_NOTICE")
    val embeddedNotice: EmbeddedNotice,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>
)

@Serializable
data class EmbeddedNotice(
    @XmlElement
    @XmlSerialName("WORK")
    val work: EmbeddedWork,
    @XmlElement
    @XmlSerialName("EXPRESSION")
    val expression: Expression,
    @XmlElement
    @XmlSerialName("MANIFESTATION")
    val manifestations: List<Manifestation>
)

@Serializable
data class EmbeddedWork(
    @XmlElement
    @XmlSerialName("WORK_DATE_DOCUMENT")
    val workDateDocument: Date,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_ID_CELEX")
    val resourceLegalIdCelex: ResourceLegalIdCelex? = null,
    @XmlElement
    @XmlSerialName("NUMBER")
    val number: ResourceLegalNumber? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_NUMBER_NATURAL")
    val resourceLegalNumberNatural: ResourceLegalNumberNatural? = null,
    @XmlElement
    @XmlSerialName("ID_CELEX")
    val idCelex: IdCelex? = null,
    @XmlElement
    @XmlSerialName("ID_SECTOR")
    val idSector: IdSector? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_TYPE")
    val resourceLegalType: ResourceLegalType? = null,
    @XmlElement
    @XmlSerialName("WORK_HAS_RESOURCE-TYPE")
    val workHasResourceType: com.kludgeworks.eu.legislation.cellarclient2.models.work.WorkHasResourceType? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_NUMBER_NATURAL_CELEX")
    val resourceLegalNumberNaturalCelex: ResourceLegalNumberNaturalCelex? = null,
    @XmlElement
    @XmlSerialName("YEAR")
    val year: Year? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_YEAR")
    val resourceLegalYear: ResourceLegalYear? = null,
    @XmlElement
    @XmlSerialName("TYPE")
    val types: List<String> = emptyList(),
    @XmlElement
    @XmlSerialName("OFFICIAL-JOURNAL_YEAR")
    val officialJournalYear: OfficialJournalYear? = null,
    @XmlElement
    @XmlSerialName("OFFICIAL-JOURNAL_CLASS")
    val officialJournalClass: OfficialJournalClass? = null,
    @XmlElement
    @XmlSerialName("PART_OF")
    val partOf: BasedOn? = null,
    @XmlElement
    @XmlSerialName("OFFICIAL-JOURNAL_NUMBER")
    val officialJournalNumber: OfficialJournalNumber? = null,
    @XmlElement
    @XmlSerialName("DATE_PUBLICATION")
    val datePublication: Date? = null,
    @XmlElement
    @XmlSerialName("OFFICIAL-JOURNAL_PART_OF_COLLECTION_DOCUMENT")
    val officialJournalPartOfCollectionDocument: BasedOn? = null
)

@Serializable
data class ResourceLegalIsAboutSubjectMatter(
    val type: String,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IS_ABOUT_SUBJECT-MATTER_1")
    val resourceLegalIsAboutSubjectMatter1: ResourceLegalIsAboutSubjectMatter1
)

@Serializable
data class ResourceLegalIsAboutSubjectMatter1(
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
    val prefLabel: String
)
