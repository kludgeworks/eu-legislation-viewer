package com.kludgeworks.eu.legislation.cellarclient.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * Represents a physical manifestation of an expression
 */
@Serializable
data class Manifestation(
    @XmlSerialName("manifestation-type")
    val manifestationType: String? = null,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI? = null,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs> = emptyList(),
    @XmlElement
    @XmlSerialName("TYPE")
    val type: String? = null,
    @XmlElement
    @XmlSerialName("MANIFESTATION_TYPE")
    val manifestationTypeValue: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("MANIFESTATION_MANIFESTS_EXPRESSION")
    val manifestsExpression: ResourceReference? = null,
    @XmlElement
    @XmlSerialName("MANIFESTATION_HAS_ITEM")
    val item: Item? = null,
    @XmlElement
    @XmlSerialName("MANIFESTATION_PART_OF_MANIFESTATION")
    val partOfManifestation: ResourceReference? = null,
    @XmlElement
    @XmlSerialName("PART_OF")
    val partOf: ResourceReference? = null,

    // === Page Information ===
    @XmlElement
    @XmlSerialName("PAGE_FIRST")
    val pageFirst: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("PAGE_LAST")
    val pageLast: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("PAGES_TOTAL")
    val pagesTotal: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("SUBSECTION_OJ")
    val subsectionOj: SimpleValue<String>? = null,

    // === Official Journal Specific ===
    @XmlElement
    @XmlSerialName("MANIFESTATION_OFFICIAL-JOURNAL_PART_PAGE_FIRST")
    val ojPageFirst: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("MANIFESTATION_OFFICIAL-JOURNAL_PART_PAGE_LAST")
    val ojPageLast: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("MANIFESTATION_OFFICIAL-JOURNAL_PART_PAGES_TOTAL")
    val ojPagesTotal: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("MANIFESTATION_OFFICIAL-JOURNAL_PART_SUBSECTION_OJ")
    val ojSubsection: SimpleValue<String>? = null,

    // === Metadata ===
    @XmlElement
    @XmlSerialName("DURABILITY")
    val durability: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("MANIFESTATION_SOURCE-POOL")
    val sourcePool: SimpleValue<String>? = null,
    @XmlElement
    @XmlSerialName("CREATIONDATE")
    val creationDate: Date? = null,
    @XmlElement
    @XmlSerialName("LASTMODIFICATIONDATE")
    val lastModificationDate: Date? = null,
    @XmlElement
    @XmlSerialName("METSSTRUCTSUPERDIV")
    val metsStructSuperDiv: ResourceReference? = null
)
