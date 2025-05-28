package com.kludgeworks.eu.legislation.rdf.mapper

import com.kludgeworks.eu.legislation.rdf.CorporateBody
import com.kludgeworks.eu.legislation.rdf.Expression
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

    /**
     * Maps a BindingSet from SPARQL query results to a Work domain object
     * Handles all optional fields gracefully using Arrow-style null safety
     */
    fun mapToWork(bindingSet: BindingSet): Work {
        return Work(
            // URI is required
            uri = bindingSet.getBinding("work")?.value?.let {
                URI.create(it.stringValue())
                // TODO throw better exception
            } ?: throw IllegalArgumentException("Work URI is required"),

            // CMR metadata properties
            creationDate = bindingSet.getBinding("creationDate")?.value?.let {
                TypeConverters.parseDateTime(it)
            },
            lastModificationDate = bindingSet.getBinding("lastModificationDate")?.value?.let {
                TypeConverters.parseDateTime(it)
            },

            // Core Work properties
            title = bindingSet.getBinding("title")?.value?.let {
                TypeConverters.parseString(it)
            } ?: "",
            version = bindingSet.getBinding("version")?.value?.let {
                TypeConverters.parseString(it)
            } ?: "",
            dateCreation = bindingSet.getBinding("dateCreation")?.value?.let {
                TypeConverters.parseDate(it)
            },
            dateDocument = bindingSet.getBinding("dateDocument")?.value?.let {
                TypeConverters.parseDate(it)
                // TODO throw better exception
            } ?: throw IllegalArgumentException("Work dateDocument is required"),
            embargo = bindingSet.getBinding("embargo")?.value?.let {
                TypeConverters.parseDateTime(it)
            },
            createdBy = bindingSet.getBinding("createdBy")?.value?.let {
                val uri = URI.create(it.stringValue())
                // Try to find a matching CorporateBody enum value
                CorporateBody.values().find { body -> body.uri == uri }
                    // TODO throw better exception
                    ?: throw IllegalArgumentException("Unknown CorporateBody URI: $uri")
            } ?: throw IllegalArgumentException("Work createdBy is required"),

            // Document IDs - collect all idDocument values into a set
            idDocuments = collectDocumentIds(bindingSet),

            // Legal Resource properties
            celexNumber = bindingSet.getBinding("celexNumber")?.value?.let {
                TypeConverters.parseString(it)
            },
            sectorId = bindingSet.getBinding("sectorId")?.value?.let {
                val sectorIdStr = TypeConverters.parseString(it) ?: ""
                // Find matching LegalSector enum by id
                LegalSector.values().find { sector -> sector.id == sectorIdStr }
                    ?: LegalSector.SECONDARY_LEGISLATION // Default to SECONDARY_LEGISLATION if not found
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
            }

            // Related entities are left empty - will be lazy-loaded if needed
        )
    }

    /**
     * Collects all document IDs from the binding set
     * Since there might be multiple document IDs, we need to handle them as a collection
     */
    private fun collectDocumentIds(bindingSet: BindingSet): Set<String> {
        return bindingSet.getBinding("idDocument")?.value?.let {
            setOf(TypeConverters.parseString(it) ?: return@let emptySet())
        } ?: emptySet()
    }
}
