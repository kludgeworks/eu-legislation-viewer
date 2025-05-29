package com.kludgeworks.eu.legislation.cellarclient2.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import com.kludgeworks.eu.legislation.cellarclient2.models.core.*
import com.kludgeworks.eu.legislation.cellarclient2.models.legal.ConsolidatedAnnotation
import com.kludgeworks.eu.legislation.cellarclient2.models.legal.ResourceLegalBasedOnConceptTreaty
import com.kludgeworks.eu.legislation.cellarclient2.models.legal.ResourceLegalPublishedInOfficialJournal
import com.kludgeworks.eu.legislation.cellarclient2.models.legal.ResourceLegalIsAboutSubjectMatter
import com.kludgeworks.eu.legislation.cellarclient2.models.legal.ResourceLegalConsolidatedByActConsolidated
import com.kludgeworks.eu.legislation.cellarclient2.models.concepts.WorkIsAboutConceptEurovoc
import com.kludgeworks.eu.legislation.cellarclient2.models.concepts.ResourceLegalIsAboutConceptDirectoryCode
import com.kludgeworks.eu.legislation.cellarclient2.models.work.BaseWork
import com.kludgeworks.eu.legislation.cellarclient2.models.work.BasedOn
import com.kludgeworks.eu.legislation.cellarclient2.models.work.CreatedBy
import com.kludgeworks.eu.legislation.cellarclient2.models.work.IsAbout
import com.kludgeworks.eu.legislation.cellarclient2.models.work.ResourceLegalAmendsResourceLegal
import com.kludgeworks.eu.legislation.cellarclient2.models.work.ResourceLegalBasedOnResourceLegal
import com.kludgeworks.eu.legislation.cellarclient2.models.work.WorkCitesWork
import com.kludgeworks.eu.legislation.cellarclient2.models.work.WorkCreatedByAgent
import com.kludgeworks.eu.legislation.cellarclient2.models.work.WorkDates
import com.kludgeworks.eu.legislation.cellarclient2.models.work.WorkHasExpression
import com.kludgeworks.eu.legislation.cellarclient2.models.work.WorkHasResourceType
import com.kludgeworks.eu.legislation.cellarclient2.models.work.WorkIdentifiers
import com.kludgeworks.eu.legislation.cellarclient2.models.work.WorkLegalInfo
import com.kludgeworks.eu.legislation.cellarclient2.models.work.WorkMetadataGroup
import com.kludgeworks.eu.legislation.cellarclient2.models.work.WorkRelationsGroup

@Serializable
data class Work(
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>,
    @XmlElement
    @XmlSerialName("DATE_CREATION_LEGACY")
    val dateCreationLegacy: Date,
    @XmlElement
    @XmlSerialName("BASED_ON")
    val basedOn: BasedOn,
    @XmlElement
    @XmlSerialName("WORK_HAS_EXPRESSION")
    val workHasExpression: List<WorkHasExpression>,
    @XmlElement
    @XmlSerialName("IS_ABOUT")
    val isAbout: List<IsAbout>,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    val identifier: List<Identifier>,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_DATE_ENTRY-INTO-FORCE")
    val resourceLegalDateEntryIntoForce: List<Date>,
    @XmlElement
    @XmlSerialName("ID_SECTOR")
    override val idSector : IdSector,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_NUMBER_NATURAL_CELEX")
    val resourceLegalNumberNaturalCelex: ResourceLegalNumberNaturalCelex,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_NUMBER_NATURAL")
    val resourceLegalNumberNatural: ResourceLegalNumberNatural,
    @XmlElement
    @XmlSerialName("ID_CELEX")
    override val idCelex: IdCelex,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_BASED_ON_CONCEPT_TREATY")
    val resourceLegalBasedOnConceptTreaty: ResourceLegalBasedOnConceptTreaty,
    @XmlElement
    @XmlSerialName("ELI")
    val eli: Eli,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IN-FORCE")
    val resourceLegalInForce: ResourceLegalInForce,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_ID_CELEX")
    override val resourceLegalIdCelex: ResourceLegalIdCelex,
    @XmlElement
    @XmlSerialName("LASTMODIFICATIONDATE")
    val lastModificationDate: Date,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_ELI")
    val resourceLegalEli: ResourceLegalEli,
    @XmlElement
    @XmlSerialName("WORK_ID_DOCUMENT")
    val workIdDocument: WorkIdDocument,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_REPERTOIRE")
    val resourceLegalRepertoire: ResourceLegalRepertoire,
    @XmlElement
    @XmlSerialName("VERSION")
    val version: Version,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_AMENDS_RESOURCE_LEGAL")
    val resourceLegalAmendsResourceLegal: List<ResourceLegalAmendsResourceLegal>,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_COMMENT_INTERNAL")
    val resourceLegalCommentInternal: ResourceLegalCommentInternal,
    @XmlElement
    @XmlSerialName("DATE_DOCUMENT")
    val dateDocument: List<Date>,
    @XmlElement
    @XmlSerialName("CREATED_BY")
    val createdBy: CreatedBy,
    @XmlElement
    @XmlSerialName("WORK_DATE_DOCUMENT")
    override val workDateDocument: Date,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_BASED_ON_RESOURCE_LEGAL")
    val resourceLegalBasedOnResourceLegal: ResourceLegalBasedOnResourceLegal,
    @XmlElement
    @XmlSerialName("WORK_IS_ABOUT_CONCEPT_EUROVOC")
    val workIsAboutConceptEurovoc: WorkIsAboutConceptEurovoc,
    @XmlElement
    @XmlSerialName("WORK_CREATED_BY_AGENT")
    val workCreatedByAgent: WorkCreatedByAgent,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IS_ABOUT_CONCEPT_DIRECTORY-CODE")
    val resourceLegalIsAboutConceptDirectoryCode: ResourceLegalIsAboutConceptDirectoryCode,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_TYPE")
    override val resourceLegalType: ResourceLegalType,
    @XmlElement
    @XmlSerialName("WORK_CITES_WORK")
    val workCitesWork: List<WorkCitesWork>,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_PUBLISHED_IN_OFFICIAL-JOURNAL")
    val resourceLegalPublishedInOfficialJournal: ResourceLegalPublishedInOfficialJournal,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_YEAR")
    val resourceLegalYear: ResourceLegalYear,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IS_ABOUT_SUBJECT-MATTER")
    val resourceLegalIsAboutSubjectMatter: ResourceLegalIsAboutSubjectMatter,
    @XmlElement
    @XmlSerialName("DATE")
    val date: List<Date>,
    @XmlElement
    @XmlSerialName("CREATIONDATE")
    val creationDate: Date,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_DATE_END-OF-VALIDITY")
    val resourceLegalDateEndOfValidity: Date,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_CONSOLIDATED_BY_ACT_CONSOLIDATED")
    val resourceLegalConsolidatedByActConsolidated: ResourceLegalConsolidatedByActConsolidated,
    @XmlElement
    @XmlSerialName("WORK_HAS_RESOURCE-TYPE")
    override val workHasResourceType: WorkHasResourceType,
    @XmlElement
    @XmlSerialName("YEAR")
    override val year: Year,
    @XmlElement
    @XmlSerialName("TYPE")
    override val types: List<String> = emptyList(),
) : BaseWork() {

    val identifiers: WorkIdentifiers
        get() = WorkIdentifiers(
            uri = uri,
            identifiers = identifier,
            idSector = idSector,
            idCelex = idCelex,
            resourceLegalIdCelex = resourceLegalIdCelex,
            workIdDocument = workIdDocument,
            resourceLegalNumberNaturalCelex = resourceLegalNumberNaturalCelex,
            resourceLegalNumberNatural = resourceLegalNumberNatural
        )

    val dates: WorkDates
        get() = WorkDates(
            dateCreationLegacy = dateCreationLegacy,
            lastModificationDate = lastModificationDate,
            dateDocument = dateDocument,
            workDateDocument = workDateDocument,
            date = date,
            creationDate = creationDate,
            resourceLegalDateEndOfValidity = resourceLegalDateEndOfValidity,
            resourceLegalDateEntryIntoForce = resourceLegalDateEntryIntoForce
        )

    val legalInfo: WorkLegalInfo
        get() = WorkLegalInfo(
            resourceLegalType = resourceLegalType,
            resourceLegalInForce = resourceLegalInForce,
            resourceLegalEli = resourceLegalEli,
            eli = eli,
            resourceLegalRepertoire = resourceLegalRepertoire,
            resourceLegalYear = resourceLegalYear,
            resourceLegalCommentInternal = resourceLegalCommentInternal
        )

    val relations: WorkRelationsGroup
        get() = WorkRelationsGroup(
            sameAs = sameAs,
            basedOn = basedOn,
            workHasExpression = workHasExpression,
            resourceLegalAmendsResourceLegal = resourceLegalAmendsResourceLegal,
            resourceLegalBasedOnResourceLegal = resourceLegalBasedOnResourceLegal,
            resourceLegalBasedOnConceptTreaty = resourceLegalBasedOnConceptTreaty,
            resourceLegalPublishedInOfficialJournal = resourceLegalPublishedInOfficialJournal,
            resourceLegalConsolidatedByActConsolidated = resourceLegalConsolidatedByActConsolidated,
            workCitesWork = workCitesWork
        )

    val metadata: WorkMetadataGroup
        get() = WorkMetadataGroup(
            isAbout = isAbout,
            createdBy = createdBy,
            workCreatedByAgent = workCreatedByAgent,
            workHasResourceType = workHasResourceType,
            workIsAboutConceptEurovoc = workIsAboutConceptEurovoc,
            resourceLegalIsAboutConceptDirectoryCode = resourceLegalIsAboutConceptDirectoryCode,
            resourceLegalIsAboutSubjectMatter = resourceLegalIsAboutSubjectMatter,
            version = version,
            year = year,
            types = types
        )
}

@Serializable
data class Date(
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
    val annotation: List<ConsolidatedAnnotation> = emptyList()
)
