package com.kludgeworks.eu.legislation.cellarclient2.models.work

import com.kludgeworks.eu.legislation.cellarclient2.models.Date
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import com.kludgeworks.eu.legislation.cellarclient2.models.core.*

/**
 * Base abstract class for all work types
 */
@Serializable
abstract class BaseWork {
    abstract val workDateDocument: Date
    abstract val resourceLegalIdCelex: ResourceLegalIdCelex?
    abstract val idCelex: IdCelex?
    abstract val idSector: IdSector?
    abstract val resourceLegalType: ResourceLegalType?
    abstract val workHasResourceType: WorkHasResourceType?
    abstract val year: Year?
    abstract val types: List<String>
}

/**
 * Embedded work implementation
 */
@Serializable
data class EmbeddedWork(
    @XmlElement
    @XmlSerialName("WORK_DATE_DOCUMENT")
    override val workDateDocument: Date,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_ID_CELEX")
    override val resourceLegalIdCelex: ResourceLegalIdCelex? = null,
    @XmlElement
    @XmlSerialName("ID_CELEX")
    override val idCelex: IdCelex? = null,
    @XmlElement
    @XmlSerialName("ID_SECTOR")
    override val idSector: IdSector? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_TYPE")
    override val resourceLegalType: ResourceLegalType? = null,
    @XmlElement
    @XmlSerialName("WORK_HAS_RESOURCE-TYPE")
    override val workHasResourceType: WorkHasResourceType? = null,
    @XmlElement
    @XmlSerialName("YEAR")
    override val year: Year? = null,
    @XmlElement
    @XmlSerialName("TYPE")
    override val types: List<String> = emptyList()
) : BaseWork()