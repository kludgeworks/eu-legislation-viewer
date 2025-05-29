package com.kludgeworks.eu.legislation.cellarclient2.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import com.kludgeworks.eu.legislation.cellarclient2.models.core.*

@Serializable
data class Manifestation(
    @XmlSerialName("manifestation-type")
    val manifestationType: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI? = null,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs> = emptyList(),
    @XmlElement
    @XmlSerialName("MANIFESTATION_TYPE")
    val manifestationTypeValue: ManifestationType? = null,
    @XmlElement
    @XmlSerialName("METSSTRUCTSUPERDIV")
    val metsStructSuperDiv: MetsStructSuperDiv? = null,
    @XmlElement
    @XmlSerialName("MANIFESTATION_MANIFESTS_EXPRESSION")
    val manifestationManifestsExpression: ManifestationManifestsExpression? = null,
    @XmlElement
    @XmlSerialName("MANIFESTATION_HAS_ITEM")
    val manifestationHasItem: ManifestationHasItem? = null,
    @XmlElement
    @XmlSerialName("LASTMODIFICATIONDATE")
    val lastModificationDate: Date? = null,
    @XmlElement
    @XmlSerialName("CREATIONDATE")
    val creationDate: Date? = null,
    @XmlElement
    @XmlSerialName("TYPE")
    val type: String? = null,
    @XmlElement
    @XmlSerialName("PAGE_FIRST")
    val pageFirst: PageFirst? = null,
    @XmlElement
    @XmlSerialName("PAGE_LAST")
    val pageLast: PageLast? = null,
    @XmlElement
    @XmlSerialName("PAGES_TOTAL")
    val pagesTotal: PagesTotal? = null,
    @XmlElement
    @XmlSerialName("SUBSECTION_OJ")
    val subsectionOj: SubsectionOj? = null,
    @XmlElement
    @XmlSerialName("MANIFESTATION_OFFICIAL-JOURNAL_PART_PAGE_FIRST")
    val manifestationOfficialJournalPartPageFirst: ManifestationOfficialJournalPartPageFirst? = null,
    @XmlElement
    @XmlSerialName("MANIFESTATION_OFFICIAL-JOURNAL_PART_PAGE_LAST")
    val manifestationOfficialJournalPartPageLast: ManifestationOfficialJournalPartPageLast? = null,
    @XmlElement
    @XmlSerialName("MANIFESTATION_OFFICIAL-JOURNAL_PART_PAGES_TOTAL")
    val manifestationOfficialJournalPartPagesTotal: ManifestationOfficialJournalPartPagesTotal? = null,
    @XmlElement
    @XmlSerialName("MANIFESTATION_OFFICIAL-JOURNAL_PART_SUBSECTION_OJ")
    val manifestationOfficialJournalPartSubsectionOj: ManifestationOfficialJournalPartSubsectionOj? = null,
    @XmlElement
    @XmlSerialName("MANIFESTATION_PART_OF_MANIFESTATION")
    val manifestationPartOfManifestation: ManifestationPartOfManifestation? = null,
    @XmlElement
    @XmlSerialName("DURABILITY")
    val durability: Durability? = null,
    @XmlElement
    @XmlSerialName("PART_OF")
    val partOf: PartOf? = null,
    @XmlElement
    @XmlSerialName("MANIFESTATION_SOURCE-POOL")
    val manifestationSourcePool: ManifestationSourcePool? = null
)

@Serializable
data class MetsStructSuperDiv(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>
)

@Serializable
data class ManifestationManifestsExpression(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>
)

@Serializable
data class ManifestationHasItem(
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>,
    @XmlElement
    @XmlSerialName("TECHMD")
    val techMd: TechMd? = null,
    @XmlElement
    @XmlSerialName("ITEM_BELONGS_TO_MANIFESTATION")
    val itemBelongsToManifestation: ItemBelongsToManifestation? = null,
    @XmlElement
    @XmlSerialName("BELONGS_TO")
    val belongsTo: BelongsTo? = null,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    val identifier: Identifier? = null,
    @XmlElement
    @XmlSerialName("ITEM_IDENTIFIER")
    val itemIdentifier: ItemIdentifier? = null
)

@Serializable
data class TechMd(
    @XmlElement
    @XmlSerialName("STREAM_NAME")
    val streamName: StreamName? = null,
    @XmlElement
    @XmlSerialName("STREAM_LABEL")
    val streamLabel: StreamLabel? = null,
    @XmlElement
    @XmlSerialName("STREAM_COMPOSITION_LEVEL")
    val streamCompositionLevel: StreamCompositionLevel? = null,
    @XmlElement
    @XmlSerialName("STREAM_SIZE")
    val streamSize: StreamSize? = null,
    @XmlElement
    @XmlSerialName("STREAM_ORDER")
    val streamOrder: StreamOrder? = null
)

@Serializable
data class ItemBelongsToManifestation(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>
)

@Serializable
data class BelongsTo(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>
)

@Serializable
data class ManifestationPartOfManifestation(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>
)

@Serializable
data class PartOf(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs>
)
