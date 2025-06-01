package com.kludgeworks.eu.legislation.shell

import com.kludgeworks.eu.legislation.cellarclient.CellarApiClient
import com.kludgeworks.eu.legislation.cellarclient.models.Notice
import com.kludgeworks.eu.legislation.cellarclient.models.Work
import com.kludgeworks.eu.legislation.cellarclient.models.ResourceReference
import com.kludgeworks.eu.legislation.cellarclient.models.EmbeddedNotice
import com.kludgeworks.eu.legislation.cellarclient.sparql.SparqlApiClient
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import picocli.CommandLine.*
import java.util.concurrent.Callable

@Component
@Command(
    name = "work",
    mixinStandardHelpOptions = true,
    version = ["1.0"],
    description = ["Retrieves and displays Work information from EU Cellar"]
)
class WorkCommand(
    private val cellarApiClient: CellarApiClient,
    private val sparqlApiClient: SparqlApiClient
) : Callable<Int> {

    class IdentifierGroup {
        @Option(
            names = ["-r", "--resource-id"],
            description = ["The resource ID to retrieve"]
        )
        var resourceId: String? = null

        @Option(
            names = ["-c", "--celex"],
            description = ["The CELEX number to retrieve"]
        )
        var celexNumber: String? = null
    }

    @ArgGroup(exclusive = true, multiplicity = "1")
    lateinit var identifierGroup: IdentifierGroup

    @Option(
        names = ["-l", "--language"],
        description = ["Language code for the title (default: en)"],
        defaultValue = "en"
    )
    var languageCode: String = "eng"

    override fun call(): Int {
        return runBlocking {
            try {
                val identifier = identifierGroup.resourceId ?: identifierGroup.celexNumber!!
                val byResourceId = identifierGroup.resourceId != null

                println("Fetching work information for ${if (byResourceId) "resource ID" else "CELEX number"}: $identifier")
                println("=" * 60)

                val notice = if (byResourceId) {
                    cellarApiClient.fetchNotice(identifier)
                } else {
                    cellarApiClient.fetchNoticeByCelex(identifier)
                }

                if (notice == null) {
                    System.err.println("Error: Resource not found with ${if (byResourceId) "ID" else "CELEX number"}: $identifier")
                    return@runBlocking 1
                }

                // Call the suspend function from within the runBlocking context
                displayWorkInformation(notice)
                0
            } catch (e: Exception) {
                System.err.println("Error fetching work information: ${e.message}")
                e.printStackTrace()
                1
            }
        }
    }

    private suspend fun displayWorkInformation(notice: Notice) {
        val work = notice.work

        // Display title from expression
        val title = findTitle(notice, languageCode)
        println("Title: ${title ?: "N/A"}")
        println()

        // Display date of entry into force
        val entryIntoForceDate = work.dateEntryIntoForce.firstOrNull()?.value
        println("Date came into force: ${formatDate(entryIntoForceDate) ?: "N/A"}")
        println()

        // Display amendments
        displayAmendments(work)

        // Display consolidations
        displayConsolidations(work)
    }

    private fun findTitle(notice: Notice, languageCode: String): String? {
        // First try to find expression with matching language
        val expression = notice.expression.find { 
            it.language?.value == languageCode || it.usesLanguage?.identifier == languageCode 
        }

        // Try expression title first, then titleAlt
        return expression?.title?.value 
            ?: expression?.titleAlt?.value
            ?: notice.expression.firstOrNull()?.title?.value
            ?: notice.expression.firstOrNull()?.titleAlt?.value
    }

    private suspend fun displayAmendments(work: Work) {
        println("Amendments:")

        // Extract CELEX number from the work
        val celexNumber = work.idCelex?.value ?: work.resourceLegalIdCelex?.value

        if (celexNumber == null) {
            println("  - Cannot fetch amendments: No CELEX number available")
            println()
            return
        }

        try {
            val amendments = sparqlApiClient.fetchAmendments(celexNumber)

            if (amendments.isEmpty()) {
                println("  - None")
            } else {
                amendments.forEach { amendment ->
                    println("  - CELEX: ${amendment.celex}")
                    println("    URI: ${amendment.uri}")
                    println("    Adoption Date: ${amendment.adoptionDate}")
                    println("    Entry Force Dates: ${amendment.entryForceDates
                                                        .joinToString(", ")
                                                        .ifBlank { "N/A" }
                                                    }"
                    )
                    println()
                }
            }
        } catch (e: Exception) {
            println("  - Error fetching amendments: ${e.message}")
            e.printStackTrace()
        }

        println()
    }

    private fun displayConsolidations(work: Work) {
        println("Consolidations:")
        if (work.basisForActConsolidated.isEmpty()) {
            println("  - None")
        } else {
            work.basisForActConsolidated.forEach { consolidation ->
                val celex = extractIdentifier(consolidation)
                println("  - ${formatAmendmentConsolidation(celex, null)}")
            }
        }
    }

    private fun extractCelex(ref: ResourceReference): String? {
        return ref.sameAs.find { it.uri.type == "celex" }?.uri?.identifier
    }

    private fun extractIdentifier(notice: EmbeddedNotice): String? {
        return notice.sameAs.find { it.uri.type == "consolidation" }?.uri?.identifier
    }

    private fun extractDate(ref: ResourceReference): String? {
        return ref.annotation?.startOfValidity
    }


    private fun formatAmendmentConsolidation(id: String?, date: String?): String {
        return when {
            id != null && date != null -> "$id - ${formatDate(date)}"
            id != null -> id
            else -> "Unknown"
        }
    }

    private fun formatDate(dateString: String?): String? {
        if (dateString == null) return null

        return try {
            // Assuming ISO date format (YYYY-MM-DD)
            val parts = dateString.split("-")
            if (parts.size >= 3) {
                "${parts[2]}/${parts[1]}/${parts[0]}" // Convert to DD/MM/YYYY
            } else {
                dateString
            }
        } catch (e: Exception) {
            dateString
        }
    }
}

// Extension operator for String multiplication (for separator lines)
private operator fun String.times(count: Int): String = this.repeat(count)
