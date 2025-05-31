package com.kludgeworks.eu.legislation.rdf

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import java.net.URI

// --- Enums for Controlled Vocabularies (based on cdm.xml NALs) ---

/**
 * Represents the type of legal resource sector, e.g., '3' for Secondary Legislation.
 * Corresponds to cdm:resource_legal_id_sector.
 *
 */
enum class LegalSector(val id: String) {
    CONSOLIDATED_ACTS("0"),
    TREATIES("1"),
    INTERNATIONAL_AGREEMENTS("2"),
    SECONDARY_LEGISLATION("3"),
    COMPLEMENTARY_LEGISLATION("4"),
    PREPARATORY_ACTS("5"),
    CASE_LAW("6"),
    NATIONAL_IMPLEMENTING_MEASURES("7"),
}

/**
 * Represents the status of a file or dossier.
 * Corresponds to AT file-status.
 * Explicit values are not extensively listed in cdm.xml, but 'file-status' is mentioned as a scheme.
 * The class `cdm:file-status` directly maps to `http://publications.europa.eu/resource/authority/file-status`.
 * Examples of values can be inferred from properties like `cdm:dossier_not-adopted-proposal` (boolean),
 * but for the enum, we should list explicit codes if available in comments, or common ones.
 * The XML defines `event-status/CANCELLED`, `event-status/DEADLINE`, `event-status/PROJECTED`.
 * For `file-status` specifically, the XML defines `status` as an equivalent class, and links `file-status` to it.
 * Let's assume common file statuses.
 */
enum class FileStatus(val code: String) {
    ACTIVE("ACTIVE"), // Common inferred status
    CLOSED("CLOSED"), // Common inferred status
    WITHDRAWN("WITHDRAWN"), // Implied by cdm:dossier_withdrawn-proposal
    PENDING("PENDING"), // Implied by cdm:dossier_pending-proposal
    ADOPTED("ADOPTED"), // Implied by cdm:dossier_adopted-proposal
    NOT_ADOPTED("NOT_ADOPTED") // Implied by cdm:dossier_not-adopted-proposal
    // The actual values would come from http://publications.europa.eu/resource/authority/file-status
}

/**
 * Represents the type of a resource, often linked to AT resource-type.
 * Corresponds to cdm:resource-type.
 * The class `cdm:resource-type` directly maps to `http://publications.europa.eu/resource/authority/resource-type`.
 * Values are inferred from class comments and properties in cdm.xml that use resource types.
 */
enum class ResourceType(val code: String) {
    REGULATION("REG"),
    DIRECTIVE("DIR"),
    DECISION("DEC"),
    PROPOSAL_REGULATION("PROP_REG"),
    PROPOSAL_DIRECTIVE("PROP_DIR"),
    PROPOSAL_DECISION("PROP_DEC"),
    COMMUNICATION("COMMUNIC"),
    REPORT("REPORT"),
    OPINION("OPIN"),
    GREEN_PAPER("PAPER_GREEN"),
    WHITE_PAPER("PAPER_WHITE"),
    STAFF_WORKING_DOCUMENT("SWD"),
    JOINT_STAFF_WORKING_DOCUMENT("JOINT_SWD"),
    IMPACT_ASSESSMENT("IMPACT_ASSESS"),
    IMPACT_ASSESSMENT_SUMMARY("IMPACT_ASSESS_SUM"),
    WORKING_DOCUMENT("WORK_DOC"),
    JOINT_COMMUNICATION("JOINT_COMMUNIC"),
}

/**
 * Represents the type of agent (Corporate Body).
 * Corresponds to AT corporate-body.
 * The class `cdm:institution` directly maps to `http://publications.europa.eu/resource/authority/corporate-body`.
 * Listing specific instances mentioned in the cdm.xml.
 */
enum class CorporateBody(val uri: URI) {
    EUROPEAN_COMMISSION(URI("http://publications.europa.eu/resource/authority/corporate-body/COM")),
    COUNCIL_OF_THE_EUROPEAN_UNION(URI("http://publications.europa.eu/resource/authority/corporate-body/CONSIL")),
    EUROPEAN_PARLIAMENT(URI("http://publications.europa.eu/resource/authority/corporate-body/EP")),
    EUROPEAN_COURT_OF_AUDITORS(URI("http://publications.europa.eu/resource/authority/corporate-body/ECA")),
    EUROPEAN_CENTRAL_BANK(URI("http://publications.europa.eu/resource/authority/corporate-body/ECB")),
    EUROPEAN_ECONOMIC_SOCIAL_COMMITTEE(URI("http://publications.europa.eu/resource/authority/corporate-body/EESC")),
    COMMITTEE_OF_THE_REGIONS(URI("http://publications.europa.eu/resource/authority/corporate-body/COR")),
    EUROPEAN_COAL_STEEL_COMMUNITY(URI("http://publications.europa.eu/resource/authority/corporate-body/ECSC")),
    COREPER_I(URI("http://publications.europa.eu/resource/authority/corporate-body/COREPER_I")),
    COREPER_II(URI("http://publications.europa.eu/resource/authority/corporate-body/COREPER_II")),
 }

/**
 * Represents a product form (e.g., electronic, print).
 * Corresponds to AT product-form.
 * The class `cdm:product-form` directly maps to `http://publications.europa.eu/resource/authority/product-form`.
 * Explicit values are not extensively listed in cdm.xml, so common ones are used.
 */
enum class ProductForm(val code: String) {
    ELECTRONIC("ELEC"),
    PRINT("PRNT"),
    PDF("PDF"),
    HTML("HTML"),
    XML("XML")
}

/**
 * Represents types of legal procedures in a court case.
 * Corresponds to AT procjur-type.
 * Values explicitly mentioned in `cdm.xml` comments are R, I, P.
 */
enum class ProcjurType(val code: String) {
    REFERENCE_FOR_PRELIMINARY_RULING("R"),
    ACTION_FOR_ANNULMENT("I"),
    ACTION_FOR_FAILURE_TO_FULFIL_AN_OBLIGATION("P")
}

/**
 * Represents the status of a court case.
 * Corresponds to AT case-status.
 * Values explicitly mentioned in `cdm.xml` comments are ENC, CLOT, SUSP, DESIST.
 */
enum class CaseStatus(val code: String) {
    PENDING("ENC"), // En cours
    CLOSED("CLOT"), // Clôturée
    SUSPENDED("SUSP"), // Suspendue
    DISCONTINUED("DESIST") // Désistée
}

/**
 * Represents the classification of a national court.
 * Corresponds to AT court-type.
 * Values explicitly mentioned in `cdm.xml` comments are Court of first instance, Court of appeal, Supreme court.
 */
enum class NationalCourtClassification(val label: String) {
    COURT_OF_FIRST_INSTANCE("Court of first instance"),
    COURT_OF_APPEAL("Court of appeal"),
    SUPREME_COURT("Supreme court")
}

/**
 * Represents types of internal Commission events.
 * Corresponds to AT com_internal_event.
 * No specific values are listed in cdm.xml, but it's linked to `http://publications.europa.eu/resource/authority/com_internal_event`.
 * This would need external lookup for full completion.
 */
enum class CommissionInternalEventType(val code: String) {
    // Placeholder as specific values are not in provided XML
    ADOPTION("ADP"),
    CONSULTATION("CONSULT")
}

/**
 * Represents types of internal Commission procedures.
 * Corresponds to AT com_internal_procedure.
 * No specific values are listed in cdm.xml, but it's linked to `http://publications.europa.eu/resource/authority/com_internal_procedure`.
 * Example from comments: "Proposition de décision" for ID "1095" for type "PRDC".
 */
enum class CommissionInternalProcedureType(val code: String) {
    PROPOSAL_DECISION("PRDC") // Example from cdm:concept_type_dossier comment
}

/**
 * Represents types of events in the interinstitutional procedure.
 * Corresponds to AT event.
 * No specific values are listed in cdm.xml for `cdm:type_event_interinstitutional`,
 * but `cdm:event` links to `http://publications.europa.eu/resource/authority/event`.
 */
enum class InterinstitutionalEventType(val code: String) {
    // Placeholder as specific values are not in provided XML
    TRANSMISSION("TRANSMIS"),
    ADOPTION_BY_COUNCIL("ADP_CONSIL"),
}

/**
 * Represents the mode of decision in a legal event.
 * Corresponds to AT fd_606.
 * Example from comments: "Procédure orale" for ID "1005".
 */
enum class DecisionMode(val code: String) {
    ORAL_PROCEDURE("1005"), // Procédure orale
}

/**
 * Represents the type of decision in a legal event.
 * Corresponds to AT fd_602.
 * Example from comments: "Adoption" for ID "1070".
 */
enum class DecisionType(val code: String) {
    ADOPTION("1070"),
    OPINION("OPIN") // Example value
}

/**
 * Represents the type of procedure with an interinstitutional code.
 * Corresponds to AT procedure.
 * Examples from comments: "COD" for "Procédure de codécision", "APP" for "procédure législative spéciale (approbation du PE)".
 */
enum class InterinstitutionalProcedureCode(val code: String) {
    ORDINARY_LEGISLATIVE_PROCEDURE("COD"), // Procédure de codécision
    SPECIAL_LEGISLATIVE_PROCEDURE_EP_APPROVAL("APP"), // procédure législative spéciale (approbation du PE)
    BUDGETARY_PROCEDURE("BUD") // Explicitly mentioned in cdm:procedure-budgetary
}

/**
 * Represents the status of an event.
 * Corresponds to AT event-status.
 * Values explicitly mentioned in `cdm.xml` comments are CANCELLED, DEADLINE, PROJECTED.
 */
enum class EventStatus(val code: String) {
    NORMAL("NORMAL"),
    CANCELLED("CANCELLED"),
    DEADLINE("DEADLINE"),
    PROJECTED("PROJECTED")
}


/**
 * Represents an amendment relationship, including details about the amendment itself.
 * Corresponds to cdm:resource_legal_amends_resource_legal.
 *
 */
data class AmendmentRelation(
    val targetUri: URI, // The URI of the legal resource being amended
    val dateOfAmendment: LocalDate? = null, // cdm:date_document of the amending resource
    val specificProvisions: String? = null // e.g., "Article 3, paragraph 2" or "Annex I"
)

/**
 * Represents a consolidation relationship.
 * Corresponds to cdm:act_consolidated_consolidates_resource_legal.
 *
 */
data class ConsolidationRelation(
    val targetUri: URI, // The URI of the legal resource being consolidated
    val dateOfConsolidation: LocalDate? = null // cdm:act_consolidated_date
)


/**
 * Simplified Work data class representing an EU legal work/document
 */
open class Work(
    val uri: URI, // cdm:uri - Unique URI identifying this work
    val creationDate: Instant? = null, // cmr:creationDate - When the entity was created in CMR
    val lastModificationDate: Instant? = null, // cmr:lastModificationDate - When the entity was last modified in CMR
    val title: String, // cdm:work_title - The main title of the work
    val version: String? = null, // cdm:work_version - Version information if applicable
    val dateCreation: LocalDate? = null, // cdm:work_date_creation - Creation date of the work itself
    val dateDocument: LocalDate, // cdm:work_date_document - Official document date (non-nullable based on cdm.xml cardinality)
    val embargo: Instant? = null, // cdm:work_embargo - Embargo date/time if applicable
    val createdBy: CorporateBody, // cdm:work_created_by_agent - URI of the corporate body who created the work (made non-nullable)
    val idDocuments: Set<String> = emptySet(), // cdm:work_id_document - Set of document identifiers
    val celexNumber: String? = null, // cdm:resource_legal_id_celex (for legal resources) - CELEX number identifier for EU legislation
    val sectorId: LegalSector, // cdm:resource_legal_id_sector - Sector identifier (made non-nullable)
    val eli: URI? = null, // cdm:resource_legal_eli - European Legislation Identifier (changed to URI and made nullable based on usage)
    val inForce: Boolean? = null, // cdm:resource_legal_in-force - Whether the legislation is in force
    val numberCorrigendum: String? = null, // cdm:resource_legal_number_corrigendum - Corrigendum number if applicable
    val dateEntryIntoForce: LocalDate? = null, // cdm:resource_legal_date_entry-into-force - Date when the legislation enters into force
    val dateEndOfValidity: LocalDate? = null, // cdm:resource_legal_date_end-of-validity - Date when the legislation ceases to be valid
    val basedOnTreaty: URI? = null, // cdm:resource_legal_based_on_concept_treaty - Treaty this legislation is based on
    val basedOnResource: URI? = null, // cdm:resource_legal_based_on_resource_legal - Legal resource this is based on
    val signatory: String? = null, // cdm:resource_legal_signatory_name - Name of the signatory
    val signatoryFunction: String? = null, // cdm:resource_legal_signatory_function - Function/role of the signatory
    val legalType: String? = null, // cdm:resource_legal_type - Type of legal resource (R for Regulation, etc.)
    val legalYear: Int? = null, // cdm:resource_legal_year - Year of the legal resource
    val eea: Boolean? = null, // cdm:resource_legal_eea - Whether this is relevant to the European Economic Area
    val repealsResource: Set<URI> = emptySet(), // cdm:resource_legal_repeals_resource_legal - Resources explicitly repealed
    val implicitlyRepealsResource: Set<URI> = emptySet(), // cdm:resource_legal_implicitly_repeals_resource_legal - Resources implicitly repealed
    val citesWork: Set<URI> = emptySet(), // cdm:work_cites_work - Works cited by this work
    val citedByWork: Set<URI> = emptySet(), // Inverse of cdm:work_cites_work - Works that cite this work
    val subjectMatters: Set<URI> = emptySet(), // cdm:resource_legal_is_about_subject-matter - Subject matter classifications
    val eurovocConcepts: Set<URI> = emptySet(), // cdm:work_is_about_concept_eurovoc - EuroVoc thesaurus concepts
    val directoryCodes: Set<URI> = emptySet(), // cdm:resource_legal_is_about_concept_directory-code - Directory classification codes
    // Representing complex relations as Sets of URIs still for simplicity,
    // but acknowledging that for deeper semantic needs, dedicated relationship classes would be used.
    val expressions: Set<URI> = emptySet(), // cdm:work_has_expression - Set of expression URIs
    val subdivisions: Set<URI> = emptySet(), // cdm:work_contains_subdivision - Set of subdivision URIs (e.g., Article, Chapter)
    val relatedWorks: Set<URI> = emptySet() // cdm:work_related_to_work - Set of related work URIs
)

/**
 * Represents a legal resource, extending the base [Work] with specific legal properties and relationships.
 *
 * This class now uses the richer relationship classes for amendments and consolidations.
 */
class LegalResource(
    val amends: Set<AmendmentRelation> = emptySet(), // cdm:resource_legal_amends_resource_legal
    val amendedBy: Set<AmendmentRelation> = emptySet(), // Inverse of cdm:resource_legal_amends_resource_legal
    val consolidates: Set<ConsolidationRelation> = emptySet(), // cdm:act_consolidated_consolidates_resource_legal
    val consolidatedBy: Set<ConsolidationRelation> = emptySet(), // Inverse of cdm:act_consolidated_consolidates_resource_legal
    val corrections: Set<URI> = emptySet(), // cdm:resource_legal_corrects_resource_legal - Corrections made by this resource
    val correctedBy: Set<URI> = emptySet(), // Inverse of cdm:resource_legal_corrects_resource_legal - Corrections to this resource

    // Work properties (passed to superclass)
    uri: URI,
    creationDate: Instant? = null,
    lastModificationDate: Instant? = null,
    title: String,
    version: String? = null, // Marked nullable as it often is
    dateCreation: LocalDate? = null,
    dateDocument: LocalDate, // Non-nullable from CDM
    embargo: Instant? = null,
    createdBy: CorporateBody, // Now an enum
    idDocuments: Set<String> = emptySet(),
    celexNumber: String? = null,
    sectorId: LegalSector, // Now an enum
    eli: URI? = null, // Now URI?
    inForce: Boolean? = null,
    numberCorrigendum: String? = null,
    dateEntryIntoForce: LocalDate? = null,
    dateEndOfValidity: LocalDate? = null,
    basedOnTreaty: URI? = null,
    basedOnResource: URI? = null,
    signatory: String? = null,
    signatoryFunction: String? = null,
    legalType: String? = null,
    legalYear: Int? = null,
    eea: Boolean? = null,
    repealsResource: Set<URI> = emptySet(),
    implicitlyRepealsResource: Set<URI> = emptySet(),
    citesWork: Set<URI> = emptySet(),
    citedByWork: Set<URI> = emptySet(),
    subjectMatters: Set<URI> = emptySet(),
    eurovocConcepts: Set<URI> = emptySet(),
    directoryCodes: Set<URI> = emptySet(),
    expressions: Set<URI> = emptySet(),
    subdivisions: Set<URI> = emptySet(),
    relatedWorks: Set<URI> = emptySet()

) : Work(
    uri = uri,
    creationDate = creationDate,
    lastModificationDate = lastModificationDate,
    title = title,
    version = version,
    dateCreation = dateCreation,
    dateDocument = dateDocument,
    embargo = embargo,
    createdBy = createdBy,
    idDocuments = idDocuments,
    celexNumber = celexNumber,
    sectorId = sectorId,
    eli = eli,
    inForce = inForce,
    numberCorrigendum = numberCorrigendum,
    dateEntryIntoForce = dateEntryIntoForce,
    dateEndOfValidity = dateEndOfValidity,
    basedOnTreaty = basedOnTreaty,
    basedOnResource = basedOnResource,
    signatory = signatory,
    signatoryFunction = signatoryFunction,
    legalType = legalType,
    legalYear = legalYear,
    eea = eea,
    repealsResource = repealsResource,
    implicitlyRepealsResource = implicitlyRepealsResource,
    citesWork = citesWork,
    citedByWork = citedByWork,
    subjectMatters = subjectMatters,
    eurovocConcepts = eurovocConcepts,
    directoryCodes = directoryCodes,
    expressions = expressions,
    subdivisions = subdivisions,
    relatedWorks = relatedWorks
)

/**
 * Expression data class representing an EU legislation expression.
 * Now uses Language enum and ProductForm enum.
 * URI and language are now non-nullable as per CDM interpretation.
 */
data class Expression(
    val uri: URI, // The unique URI identifying this expression
    val date: LocalDate? = null, // cdm:expression_date - The date of the expression
    val edition: String? = null, // cdm:expression_edition - The edition of the expression
    val embargo: Instant? = null, // cdm:expression_embargo - The embargo date/time if applicable
    val source: String? = null, // cdm:expression_source - The source of the expression
    val title: String, // cdm:expression_title - The title of the expression (non-nullable based on cdm.xml)
    val version: String? = null, // cdm:expression_version - The version of the expression
    val language: String,
    val belongsToWorkUri: URI, // cdm:expression_belongs_to_work - The work URI this expression belongs to (non-nullable based on cdm.xml)
    val manifestations: Set<URI> = emptySet() // cdm:expression_includes_manifestation - Set of manifestation URIs
)

/**
 * Manifestation data class representing an EU legislation manifestation.
 * Now uses ProductForm for a more specific type.
 * URI and manifestsExpressionUri are now non-nullable.
 */
data class Manifestation(
    val uri: URI, // The unique URI identifying this manifestation
    val date: LocalDate? = null, // cdm:manifestation_date - The date of the manifestation
    val datePublication: LocalDate? = null, // cdm:manifestation_date_publication - The publication date of the manifestation
    val dimension: String? = null, // cdm:manifestation_dimension - The dimension of the manifestation (string kept for flexibility)
    val embargo: Instant? = null, // cdm:manifestation_embargo - The embargo date/time if applicable
    val isSigned: Boolean? = null, // cdm:manifestation_is-signed - Whether the manifestation is signed
    val catalogueNumberGlobal: String? = null, // cdm:manifestation_number_catalogue_global - The global catalogue number
    val type: String? = null, // cdm:manifestation_type - The type of the manifestation (string kept for flexibility, could be an enum if categories are fixed)
    val version: String? = null, // cdm:manifestation_version - The version of the manifestation
    val publishedByUri: URI? = null, // cdm:manifestation_published_by_agent - The agent URI who published this manifestation
    val manifestsExpressionUri: URI, // cdm:manifestation_manifests_expression - The expression URI this manifestation manifests (made non-nullable)
    val productForm: ProductForm? = null, // cdm:manifestation_has-product-form_product-form - The product form of the manifestation
    val items: Set<URI> = emptySet(), // cdm:manifestation_has_item - Set of item URIs
    val containsManifestations: Set<URI> = emptySet(), // cdm:manifestation_contains_manifestation - Set of contained manifestation URIs
    val partOfManifestationUri: URI? = null // cdm:manifestation_part_of_manifestation - The parent manifestation URI
)

/**
 * Represents a content item, typically a specific file or rendition of a manifestation.
 * URI and identifier are now non-nullable.
 */
data class ContentItem(
    val uri: URI, // The unique URI identifying this content item.
    val identifier: String, // cdm:item_identifier - A string identifier for the item (non-nullable from cdm.xml)
    val doi: String? = null, // cdm:item_doi - The Digital Object Identifier of the item
    val embargo: Instant? = null, // cdm:item_embargo - The embargo date/time if applicable
    val belongsToManifestationUri: URI? = null, // cdm:item_belongs_to_manifestation - The URI of the manifestation this item belongs to
    val mimeType: String? = null // cdm:manifestationMimeType - The MIME type of the item (could be an enum if fixed list)
)