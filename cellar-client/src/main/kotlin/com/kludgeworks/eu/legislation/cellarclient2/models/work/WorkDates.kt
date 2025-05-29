package com.kludgeworks.eu.legislation.cellarclient2.models.work

import com.kludgeworks.eu.legislation.cellarclient2.models.Date
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class WorkDates(
    @XmlElement
    @XmlSerialName("DATE_CREATION_LEGACY")
    val dateCreationLegacy: Date,
    @XmlElement
    @XmlSerialName("LASTMODIFICATIONDATE")
    val lastModificationDate: Date,
    @XmlElement
    @XmlSerialName("DATE_DOCUMENT")
    val dateDocument: List<Date>,
    @XmlElement
    @XmlSerialName("WORK_DATE_DOCUMENT")
    val workDateDocument: Date,
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
    @XmlSerialName("RESOURCE_LEGAL_DATE_ENTRY-INTO-FORCE")
    val resourceLegalDateEntryIntoForce: List<Date>
)