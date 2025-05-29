package com.kludgeworks.eu.legislation.cellarclient2.models.work

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import com.kludgeworks.eu.legislation.cellarclient2.models.core.*
import com.kludgeworks.eu.legislation.cellarclient2.models.concepts.*
import com.kludgeworks.eu.legislation.cellarclient2.models.legal.ResourceLegalIsAboutSubjectMatter

@Serializable
data class WorkMetadataGroup(
    @XmlElement
    @XmlSerialName("IS_ABOUT")
    val isAbout: List<IsAbout>,
    @XmlElement
    @XmlSerialName("CREATED_BY")
    val createdBy: CreatedBy,
    @XmlElement
    @XmlSerialName("WORK_CREATED_BY_AGENT")
    val workCreatedByAgent: WorkCreatedByAgent,
    @XmlElement
    @XmlSerialName("WORK_HAS_RESOURCE-TYPE")
    val workHasResourceType: WorkHasResourceType,
    @XmlElement
    @XmlSerialName("WORK_IS_ABOUT_CONCEPT_EUROVOC")
    val workIsAboutConceptEurovoc: WorkIsAboutConceptEurovoc,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IS_ABOUT_CONCEPT_DIRECTORY-CODE")
    val resourceLegalIsAboutConceptDirectoryCode: ResourceLegalIsAboutConceptDirectoryCode,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IS_ABOUT_SUBJECT-MATTER")
    val resourceLegalIsAboutSubjectMatter: ResourceLegalIsAboutSubjectMatter,
    @XmlElement
    @XmlSerialName("VERSION")
    val version: Version,
    @XmlElement
    @XmlSerialName("YEAR")
    val year: Year,
    @XmlElement
    @XmlSerialName("TYPE")
    val types: List<String> = emptyList()
)
