package com.kludgeworks.eu.legislation.rdf.mapper

import com.kludgeworks.eu.legislation.rdf.Amendment
import com.kludgeworks.eu.legislation.rdf.Consolidation
import com.kludgeworks.eu.legislation.rdf.ContentItem
import com.kludgeworks.eu.legislation.rdf.LegalDocument
import com.kludgeworks.eu.legislation.rdf.Manifestation
import com.kludgeworks.eu.legislation.util.DateUtils
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.apache.jena.query.QuerySolution
import org.springframework.stereotype.Component

/**
 * Extension functions for mapping SPARQL query results to domain objects
 */

// Extension function to safely get a string from a literal
private fun QuerySolution.getString(name: String): String = 
    getLiteral(name)?.string ?: ""

// Extension function to safely get a URI from a resource
private fun QuerySolution.getUri(name: String): String = 
    getResource(name)?.uri ?: ""

// Extension function to parse a date with fallback to current date
private fun QuerySolution.getDate(name: String, pattern: String = "yyyy-MM-dd"): LocalDate {
    val dateStr = getString(name)
    return if (dateStr.isNotEmpty()) DateUtils.parse(dateStr, pattern) else Clock.System.todayIn(TimeZone.UTC)
}

/**
 * Component for mapping SPARQL query results to domain objects
 */
@Component
class SparqlResultMapper {

    private val datePattern = "yyyy-MM-dd"

    /**
     * Parse a date string into a LocalDate
     *
     * @param dateStr The date string to parse
     * @return The parsed date
     */
    fun parseDate(dateStr: String): LocalDate {
        return DateUtils.parse(dateStr, datePattern)
    }

    /**
     * Map a SPARQL query solution to a LegalDocument
     */
    fun mapToLegalDocument(solution: QuerySolution): LegalDocument = 
        LegalDocument(
            celexNumber = if (solution.contains("celexNumber")) solution.getString("celexNumber") else solution.getString("celex"),
            uri = solution.getUri("work"),
            title = solution.getString("title"),
            documentDate = solution.getDate("date", datePattern),
            expressions = emptyList()
        )

    /**
     * Map a SPARQL query solution to a Manifestation
     */
    fun mapToManifestation(solution: QuerySolution): Manifestation =
        Manifestation(
            uri = solution.getUri("manifestation"),
            type = solution.getString("type"),
            items = emptyList()
        )

    /**
     * Map a SPARQL query solution to a ContentItem
     */
    fun mapToContentItem(solution: QuerySolution): ContentItem {
        // Try to get manifestationType as a URI first, then as a string if that fails
        val manifestationTypeUri = try {
            solution.getUri("manifestationType")
        } catch (e: ClassCastException) {
            // If it's a literal, just get the string value
            solution.getString("manifestationType")
        }

        // Extract the type code from the URI, defaulting to "fmx4" if not found
        val typeCode = if (manifestationTypeUri.isNotEmpty()) {
            manifestationTypeUri.substringAfterLast("/").lowercase()
        } else {
            "fmx4"
        }

        return ContentItem(
            uri = solution.getUri("item"),
            identifier = solution.getString("identifier"),
            manifestationType = typeCode,
            mimeType = solution.getString("mimeType")
        )
    }

    /**
     * Map a SPARQL query solution to an Amendment
     */
    fun mapToAmendment(solution: QuerySolution): Amendment =
        Amendment(
            uri = solution.getUri("amendment"),
            celexNumber = solution.getString("celex"),
            date = solution.getDate("date", datePattern),
            consolidations = emptyList()
        )

    /**
     * Map a SPARQL query solution to a Consolidation
     */
    fun mapToConsolidation(solution: QuerySolution): Consolidation =
        Consolidation(
            uri = solution.getUri("consolidation"),
            celexNumber = solution.getString("celex"),
            consolidationDate = solution.getDate("date", datePattern),
            eli = solution.getString("eli")
        )
}
