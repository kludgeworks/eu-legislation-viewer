package com.kludgeworks.eu.legislation.cellarclient2.models.work

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import com.kludgeworks.eu.legislation.cellarclient2.models.core.*

@Serializable
data class WorkIdentifiers(
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    val identifiers: List<Identifier>,
    @XmlElement
    @XmlSerialName("ID_SECTOR")
    val idSector: IdSector,
    @XmlElement
    @XmlSerialName("ID_CELEX")
    val idCelex: IdCelex,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_ID_CELEX")
    val resourceLegalIdCelex: ResourceLegalIdCelex,
    @XmlElement
    @XmlSerialName("WORK_ID_DOCUMENT")
    val workIdDocument: WorkIdDocument,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_NUMBER_NATURAL_CELEX")
    val resourceLegalNumberNaturalCelex: ResourceLegalNumberNaturalCelex,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_NUMBER_NATURAL")
    val resourceLegalNumberNatural: ResourceLegalNumberNatural
)