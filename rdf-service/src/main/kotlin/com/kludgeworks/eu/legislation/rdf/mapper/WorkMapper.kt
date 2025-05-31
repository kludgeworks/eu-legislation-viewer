package com.kludgeworks.eu.legislation.rdf.mapper

import com.kludgeworks.eu.legislation.rdf.AmendmentRelation
import com.kludgeworks.eu.legislation.rdf.ConsolidationRelation
import com.kludgeworks.eu.legislation.rdf.CorporateBody
import com.kludgeworks.eu.legislation.rdf.LegalResource
import com.kludgeworks.eu.legislation.rdf.LegalSector
import com.kludgeworks.eu.legislation.rdf.Work
import org.eclipse.rdf4j.query.BindingSet
import org.springframework.stereotype.Component
import java.net.URI

/**
 * Mapper for converting SPARQL query results to Work domain objects
 * Following Spring Boot best practices with @Component annotation
 */
@Component
internal class WorkMapper {

    private val complexRelationDelimiter = "||"
    private val subDelimiter = "##"

    /**
     * Maps a BindingSet from SPARQL query results to a Work domain object
     * Handles all optional fields gracefully
     */
    fun mapToWork(bindingSet: BindingSet): Work {
        return Work(
            uri = bindingSet.getBinding("work")?.value?.let {
                URI.create(it.stringValue())
            } ?: throw IllegalArgumentException("Work URI is required"),

            creationDate = bindingSet.getBinding("creationDate")?.value?.let {
                TypeConverters.parseDateTime(it)
            },
            lastModificationDate = bindingSet.getBinding("lastModificationDate")?.value?.let {
                TypeConverters.parseDateTime(it)
            },

            title = bindingSet.getBinding("title")?.value?.let {
                TypeConverters.parseString(it)
            } ?: "",
            version = bindingSet.getBinding("version")?.value?.let {
                TypeConverters.parseString(it)
            },
            dateCreation = bindingSet.getBinding("dateCreation")?.value?.let {
                TypeConverters.parseDate(it)
            },
            dateDocument = bindingSet.getBinding("dateDocument")?.value?.let {
                TypeConverters.parseDate(it)
            } ?: throw IllegalArgumentException("Work dateDocument is required"),
            embargo = bindingSet.getBinding("embargo")?.value?.let {
                TypeConverters.parseDateTime(it)
            },
            createdBy = bindingSet.getBinding("createdBy")?.value?.let {
                val uri = URI.create(it.stringValue())
                CorporateBody.entries.find { body -> body.uri == uri }
                    ?: throw IllegalArgumentException("Unknown CorporateBody URI: $uri")
            } ?: throw IllegalArgumentException("Work createdBy is required"),

            idDocuments = bindingSet.getBinding("idDocuments")?.value?.let {
                parseDelimitedValues(TypeConverters.parseString(it), complexRelationDelimiter)
            } ?: emptySet(),

            celexNumber = bindingSet.getBinding("celexNumber")?.value?.let {
                TypeConverters.parseString(it)
            },
            sectorId = bindingSet.getBinding("sectorId")?.value?.let {
                val sectorIdStr = TypeConverters.parseString(it) ?: ""
                LegalSector.entries.find { sector -> sector.id == sectorIdStr }
                    ?: LegalSector.SECONDARY_LEGISLATION // Default
            } ?: LegalSector.SECONDARY_LEGISLATION,
            eli = bindingSet.getBinding("eli")?.value?.let {
                val eliStr = TypeConverters.parseString(it)
                if (eliStr.isNullOrBlank()) null else URI.create(eliStr)
            },
            inForce = bindingSet.getBinding("inForce")?.value?.let {
                TypeConverters.parseBoolean(it)
            },
            numberCorrigendum = bindingSet.getBinding("numberCorrigendum")?.value?.let {
                TypeConverters.parseString(it)
            },
            
            dateEntryIntoForce = bindingSet.getBinding("dateEntryIntoForce")?.value?.let {
                TypeConverters.parseDate(it)
            },
            dateEndOfValidity = bindingSet.getBinding("dateEndOfValidity")?.value?.let {
                TypeConverters.parseDate(it)
            },
            basedOnTreaty = bindingSet.getBinding("basedOnTreaty")?.value?.let {
                URI.create(it.stringValue())
            },
            basedOnResource = bindingSet.getBinding("basedOnResource")?.value?.let {
                URI.create(it.stringValue())
            },
            signatory = bindingSet.getBinding("signatory")?.value?.let {
                TypeConverters.parseString(it)
            },
            signatoryFunction = bindingSet.getBinding("signatoryFunction")?.value?.let {
                TypeConverters.parseString(it)
            },
            legalType = bindingSet.getBinding("legalType")?.value?.let {
                TypeConverters.parseString(it)
            },
            legalYear = bindingSet.getBinding("legalYear")?.value?.let {
                TypeConverters.parseString(it)?.toIntOrNull()
            },
            eea = bindingSet.getBinding("eea")?.value?.let {
                TypeConverters.parseBoolean(it)
            },
            
            repealsResource = bindingSet.getBinding("repealsResources")?.value?.let { // Updated name
                parseDelimitedURIs(TypeConverters.parseString(it), complexRelationDelimiter)
            } ?: emptySet(),
            implicitlyRepealsResource = bindingSet.getBinding("implicitlyRepealsResources")?.value?.let { // Updated name
                parseDelimitedURIs(TypeConverters.parseString(it), complexRelationDelimiter)
            } ?: emptySet(),
            
            citesWork = bindingSet.getBinding("citedWorks")?.value?.let {
                parseDelimitedURIs(TypeConverters.parseString(it), complexRelationDelimiter)
            } ?: emptySet(),
            citedByWork = bindingSet.getBinding("citedByWorks")?.value?.let {
                parseDelimitedURIs(TypeConverters.parseString(it), complexRelationDelimiter)
            } ?: emptySet(),
            
            subjectMatters = bindingSet.getBinding("subjectMatters")?.value?.let {
                parseDelimitedURIs(TypeConverters.parseString(it), complexRelationDelimiter)
            } ?: emptySet(),
            eurovocConcepts = bindingSet.getBinding("eurovocConcepts")?.value?.let {
                parseDelimitedURIs(TypeConverters.parseString(it), complexRelationDelimiter)
            } ?: emptySet(),
            directoryCodes = bindingSet.getBinding("directoryCodes")?.value?.let {
                parseDelimitedURIs(TypeConverters.parseString(it), complexRelationDelimiter)
            } ?: emptySet(),

            expressions = bindingSet.getBinding("expressions")?.value?.let {
                parseDelimitedURIs(TypeConverters.parseString(it), complexRelationDelimiter)
            } ?: emptySet(),
            subdivisions = bindingSet.getBinding("subdivisions")?.value?.let {
                parseDelimitedURIs(TypeConverters.parseString(it), complexRelationDelimiter)
            } ?: emptySet(),
            relatedWorks = bindingSet.getBinding("relatedWorks")?.value?.let {
                parseDelimitedURIs(TypeConverters.parseString(it), complexRelationDelimiter)
            } ?: emptySet()
        )
    }

    fun mapToLegalResource(bindingSet: BindingSet): LegalResource {
        val work = mapToWork(bindingSet)
        
        return LegalResource(
            amends = bindingSet.getBinding("amendsRelations")?.value?.let { // Updated name
                parseDelimitedAmendmentRelations(TypeConverters.parseString(it))
            } ?: emptySet(),
            amendedBy = bindingSet.getBinding("amendedByRelations")?.value?.let { // Updated name
                parseDelimitedAmendmentRelations(TypeConverters.parseString(it))
            } ?: emptySet(),
            
            consolidates = bindingSet.getBinding("consolidatesRelations")?.value?.let { // Updated name
                parseDelimitedConsolidationRelations(TypeConverters.parseString(it))
            } ?: emptySet(),
            consolidatedBy = bindingSet.getBinding("consolidatedByRelations")?.value?.let { // Updated name
                parseDelimitedConsolidationRelations(TypeConverters.parseString(it))
            } ?: emptySet(),
            
            corrections = bindingSet.getBinding("corrections")?.value?.let {
                parseDelimitedURIs(TypeConverters.parseString(it), complexRelationDelimiter)
            } ?: emptySet(),
            correctedBy = bindingSet.getBinding("correctedByResources")?.value?.let {
                parseDelimitedURIs(TypeConverters.parseString(it), complexRelationDelimiter)
            } ?: emptySet(),
            
            uri = work.uri,
            creationDate = work.creationDate,
            lastModificationDate = work.lastModificationDate,
            title = work.title,
            version = work.version,
            dateCreation = work.dateCreation,
            dateDocument = work.dateDocument,
            embargo = work.embargo,
            createdBy = work.createdBy,
            idDocuments = work.idDocuments,
            celexNumber = work.celexNumber,
            sectorId = work.sectorId,
            eli = work.eli,
            inForce = work.inForce,
            numberCorrigendum = work.numberCorrigendum,
            dateEntryIntoForce = work.dateEntryIntoForce,
            dateEndOfValidity = work.dateEndOfValidity,
            basedOnTreaty = work.basedOnTreaty,
            basedOnResource = work.basedOnResource,
            signatory = work.signatory,
            signatoryFunction = work.signatoryFunction,
            legalType = work.legalType,
            legalYear = work.legalYear,
            eea = work.eea,
            repealsResource = work.repealsResource,
            implicitlyRepealsResource = work.implicitlyRepealsResource,
            citesWork = work.citesWork,
            citedByWork = work.citedByWork,
            subjectMatters = work.subjectMatters,
            eurovocConcepts = work.eurovocConcepts,
            directoryCodes = work.directoryCodes,
            expressions = work.expressions,
            subdivisions = work.subdivisions,
            relatedWorks = work.relatedWorks
        )
    }

    private fun parseDelimitedValues(value: String?, delimiter: String): Set<String> {
        return value?.split(delimiter)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
    }

    private fun parseDelimitedURIs(value: String?, delimiter: String): Set<URI> {
        return parseDelimitedValues(value, delimiter)
            .mapNotNull { 
                try {
                    URI.create(it)
                } catch (e: Exception) {
                    // Consider logging this error for malformed URIs
                    null // Skip invalid URIs
                }
            }
            .toSet()
    }

    private fun parseDelimitedAmendmentRelations(value: String?): Set<AmendmentRelation> {
        return parseDelimitedValues(value, complexRelationDelimiter).mapNotNull { relationString ->
            val parts = relationString.split(subDelimiter)
            if (parts.size >= 1) { // URI is mandatory
                try {
                    val targetUri = URI.create(parts[0])
                    val dateOfAmendment = if (parts.size > 1 && parts[1].isNotBlank()) TypeConverters.parseRawDate(parts[1]) else null
                    val specificProvisions = if (parts.size > 2 && parts[2].isNotBlank()) parts[2] else null
                    AmendmentRelation(targetUri, dateOfAmendment, specificProvisions)
                } catch (e: Exception) {
                    // Consider logging this error
                    null // Skip malformed relations
                }
            } else {
                null
            }
        }.toSet()
    }

    private fun parseDelimitedConsolidationRelations(value: String?): Set<ConsolidationRelation> {
        return parseDelimitedValues(value, complexRelationDelimiter).mapNotNull { relationString ->
            val parts = relationString.split(subDelimiter)
            if (parts.size >= 1) { // URI is mandatory
                try {
                    val targetUri = URI.create(parts[0])
                    val dateOfConsolidation = if (parts.size > 1 && parts[1].isNotBlank()) TypeConverters.parseRawDate(parts[1]) else null
                    ConsolidationRelation(targetUri, dateOfConsolidation)
                } catch (e: Exception) {
                    // Consider logging this error
                    null // Skip malformed relations
                }
            } else {
                null
            }
        }.toSet()
    }
}