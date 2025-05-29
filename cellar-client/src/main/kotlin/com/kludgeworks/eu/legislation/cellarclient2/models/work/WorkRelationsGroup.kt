package com.kludgeworks.eu.legislation.cellarclient2.models.work

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import com.kludgeworks.eu.legislation.cellarclient2.models.core.*
import com.kludgeworks.eu.legislation.cellarclient2.models.legal.*

@Serializable
data class WorkRelationsGroup(
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>,
    @XmlElement
    @XmlSerialName("BASED_ON")
    val basedOn: BasedOn,
    @XmlElement
    @XmlSerialName("WORK_HAS_EXPRESSION")
    val workHasExpression: List<WorkHasExpression>,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_AMENDS_RESOURCE_LEGAL")
    val resourceLegalAmendsResourceLegal: List<ResourceLegalAmendsResourceLegal>,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_BASED_ON_RESOURCE_LEGAL")
    val resourceLegalBasedOnResourceLegal: ResourceLegalBasedOnResourceLegal,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_BASED_ON_CONCEPT_TREATY")
    val resourceLegalBasedOnConceptTreaty: ResourceLegalBasedOnConceptTreaty,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_PUBLISHED_IN_OFFICIAL-JOURNAL")
    val resourceLegalPublishedInOfficialJournal: ResourceLegalPublishedInOfficialJournal,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_CONSOLIDATED_BY_ACT_CONSOLIDATED")
    val resourceLegalConsolidatedByActConsolidated: ResourceLegalConsolidatedByActConsolidated,
    @XmlElement
    @XmlSerialName("WORK_CITES_WORK")
    val workCitesWork: List<WorkCitesWork>
)