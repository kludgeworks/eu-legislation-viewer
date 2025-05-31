package com.kludgeworks.eu.legislation.shell

import com.kludgeworks.eu.legislation.cellarclient.models.Notice
import com.kludgeworks.eu.legislation.cellarclient.models.Work
import com.kludgeworks.eu.legislation.cellarclient.models.ResourceReference
import com.kludgeworks.eu.legislation.cellarclient.models.EmbeddedNotice
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
    private val cellarService: CellarService
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
                    cellarService.fetchNotice(identifier)
                } else {
                    cellarService.fetchNoticeByCelex(identifier)
                }

                if (notice == null) {
                    System.err.println("Error: Resource not found with ${if (byResourceId) "ID" else "CELEX number"}: $identifier")
                    return@runBlocking 1
                }

                displayWorkInformation(notice)
                0
            } catch (e: Exception) {
                System.err.println("Error fetching work information: ${e.message}")
                e.printStackTrace()
                1
            }
        }
    }

    private fun displayWorkInformation(notice: Notice) {
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

    private fun displayAmendments(work: Work) {
        println("Amendments:")
        if (work.amendedBy.isEmpty()) {
            println("  - None")
        } else {
            work.amendedBy.forEach { amendment ->
                val celex = extractCelex(amendment)
                val date = extractDate(amendment)
                println("  - ${formatAmendmentConsolidation(celex, date)}")
            }
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
