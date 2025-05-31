package com.kludgeworks.eu.legislation.cellarclient.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * Represents a legislative work - the abstract concept of the legislation
 */
@Serializable
data class Work(
    // === Identity ===
    @XmlElement
    @XmlSerialName("URI")
    internal val _uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs> = emptyList(),

    // === Identifiers ===
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    val identifiers: List<SimpleValue<String>> = emptyList(),
    @XmlElement
    @XmlSerialName("ID_CELEX")
    val idCelex: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("ID_SECTOR")
    val idSector: SimpleValue<Int>? = null,
    @XmlElement
    @XmlSerialName("WORK_ID_DOCUMENT")
    val workIdDocument: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_ID_CELEX")
    val resourceLegalIdCelex: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_NUMBER_NATURAL")
    val resourceLegalNumberNatural: SimpleValue<Int>? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_NUMBER_NATURAL_CELEX")
    val resourceLegalNumberNaturalCelex: SimpleValue<Int>? = null,

    // === ELI ===
    @XmlElement
    @XmlSerialName("ELI")
    val eli: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_ELI")
    val resourceLegalEli: SimpleValue<String>? = null,

    // === Dates ===
    @XmlElement
    @XmlSerialName("WORK_DATE_DOCUMENT")
    val workDateDocument: Date? = null,
    @XmlElement
    @XmlSerialName("DATE_DOCUMENT")
    val dateDocument: List<Date> = emptyList(),
    @XmlElement
    @XmlSerialName("DATE")
    val dates: List<Date> = emptyList(),
    @XmlElement
    @XmlSerialName("CREATIONDATE")
    val creationDate: Date? = null,
    @XmlElement
    @XmlSerialName("DATE_CREATION_LEGACY")
    val dateCreationLegacy: Date? = null,
    @XmlElement
    @XmlSerialName("LASTMODIFICATIONDATE")
    val lastModificationDate: Date? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_DATE_ENTRY-INTO-FORCE")
    val dateEntryIntoForce: List<Date> = emptyList(),
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_DATE_END-OF-VALIDITY")
    val dateEndOfValidity: Date? = null,

    // === Legal Metadata ===
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
    @XmlSerialName("RESOURCE_LEGAL_IN-FORCE")
    val inForce: SimpleValue<Boolean>? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_REPERTOIRE")
    val repertoire: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_COMMENT_INTERNAL")
    val commentInternal: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("VERSION")
    val version: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("TYPE")
    val types: List<String> = emptyList(),

    // === Concepts and Classifications ===
    @XmlElement
    @XmlSerialName("WORK_HAS_RESOURCE-TYPE")
    val resourceType: Concept? = null,
    @XmlElement
    @XmlSerialName("WORK_IS_ABOUT_CONCEPT_EUROVOC")
    val eurovocConcept: EurovocConcept? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IS_ABOUT_CONCEPT_DIRECTORY-CODE")
    val directoryCodes: DirectoryCodeConcepts? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IS_ABOUT_SUBJECT-MATTER")
    val subjectMatter: SubjectMatter? = null,
    @XmlElement
    @XmlSerialName("IS_ABOUT")
    val isAbout: List<IsAbout> = emptyList(),

    // === Relations ===
    @XmlElement
    @XmlSerialName("WORK_HAS_EXPRESSION")
    val expressions: List<WorkExpression> = emptyList(),
    @XmlElement
    @XmlSerialName("BASED_ON")
    val basedOn: ResourceReference? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_BASED_ON_RESOURCE_LEGAL")
    val basedOnResourceLegal: ResourceReference? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_BASED_ON_CONCEPT_TREATY")
    val basedOnTreaty: Concept? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_AMENDS_RESOURCE_LEGAL")
    val amends: List<ResourceReference> = emptyList(),
    @XmlElement
    @XmlSerialName("WORK_CITES_WORK")
    val cites: List<ResourceReference> = emptyList(),
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_CONSOLIDATED_BY_ACT_CONSOLIDATED")
    val consolidatedBy: EmbeddedNotice? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_AMENDED_BY_RESOURCE_LEGAL")
    val amendedBy: List<ResourceReference> = emptyList(),
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_CORRECTED_BY_RESOURCE_LEGAL")
    val correctedBy: List<ResourceReference> = emptyList(),
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IMPLICITLY_REPEALS_RESOURCE_LEGAL")
    val implicitlyRepeals: List<ResourceReference> = emptyList(),
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_BASIS_FOR_ACT_CONSOLIDATED")
    val basisForActConsolidated: List<EmbeddedNotice> = emptyList(),
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_REPEALS_RESOURCE_LEGAL")
    val repeals: List<ResourceReference> = emptyList(),

    // === Creation and Publication ===
    @XmlElement
    @XmlSerialName("CREATED_BY")
    val createdBy: CreatedBy? = null,
    @XmlElement
    @XmlSerialName("WORK_CREATED_BY_AGENT")
    val createdByAgent: Concept? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_PUBLISHED_IN_OFFICIAL-JOURNAL")
    val publishedInOfficialJournal: EmbeddedNotice? = null
) {
    val uri: URI
        get() = _uri
}
