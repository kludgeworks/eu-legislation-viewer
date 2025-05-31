package com.kludgeworks.eu.legislation.shell

import com.kludgeworks.eu.legislation.cellarclient.CellarApiClient
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
    private val cellarApiClient: CellarApiClient
) : Callable<Int> {

    @Parameters(
        index = "0",
        description = ["The resource ID (e.g., CELEX number) to retrieve"]
    )
    lateinit var resourceId: String

    @Option(
        names = ["-l", "--language"],
        description = ["Language code for the title (default: eng)"],
        defaultValue = "eng"
    )
    var languageCode: String = "eng"

    override fun call(): Int {
        return runBlocking {
            try {
                println("Fetching work information for: $resourceId")
                println("=" * 60)

                val notice = cellarApiClient.fetchNotice(resourceId)
                if (notice == null) {
                    System.err.println("Error: Resource not found with ID: $resourceId")
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
                val celex = extractCelexFromEmbedded(consolidation)
                val date = extractDateFromEmbedded(consolidation)
                println("  - ${formatAmendmentConsolidation(celex, date)}")
            }
        }
    }

    private fun extractCelex(ref: ResourceReference): String? {
        // Try to extract CELEX from URI or identifier
        val uriValue = ref.uri?.value
        return uriValue?.substringAfterLast('/')
            ?: ref.identifier
    }

    private fun extractCelexFromEmbedded(notice: EmbeddedNotice): String? {
        return notice.notice.work.idCelex?.value
            ?: notice.notice.work.idCelexWork?.value
            ?: notice.uri.value.substringAfterLast('/')
    }

    private fun extractDate(ref: ResourceReference): String? {
        // Date information might not be available in ResourceReference
        // In a real implementation, you might need to fetch the referenced resource
        return null
    }

    private fun extractDateFromEmbedded(notice: EmbeddedNotice): String? {
        return notice.notice.work.workDateDocument?.value
    }

    private fun formatAmendmentConsolidation(celex: String?, date: String?): String {
        return when {
            celex != null && date != null -> "$celex - ${formatDate(date)}"
            celex != null -> celex
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
