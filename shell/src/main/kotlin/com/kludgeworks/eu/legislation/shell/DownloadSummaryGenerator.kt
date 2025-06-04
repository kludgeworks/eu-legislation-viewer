package com.kludgeworks.eu.legislation.shell

import com.kludgeworks.eu.legislation.cellarclient.sparql.AmendmentInfo
import com.kludgeworks.eu.legislation.cellarclient.sparql.ConsolidationInfo
import kotlinx.datetime.LocalDate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Component
class DownloadSummaryGenerator {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Generates a summary report of all downloaded content
     */
    fun generateSummaryReport(workDirectory: File): String {
        val report = StringBuilder()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy")

        report.appendLine("# EU LEGISLATION DOWNLOAD SUMMARY")
        report.appendLine("Generated: ${dateFormat.format(Date())}")
        report.appendLine("=====================================")
        report.appendLine()

        // Original work
        val originalDir = File(workDirectory, "original")
        if (originalDir.exists()) {
            report.appendLine("## ORIGINAL WORK")
            report.appendLine("Location: original")
            val originalFiles = originalDir.listFiles { file -> file.name.endsWith(".fmx4") || file.name.endsWith(".pdf") || file.name.endsWith(".html") }
            report.appendLine("Content Items: ${originalFiles?.size ?: 0}")
            originalFiles?.forEach { file ->
                report.appendLine("  - ${file.name} (${formatFileSize(file.length())})")
            }
            report.appendLine()
        }

        // Amendments
        val amendmentsDir = File(workDirectory, "amendments")
        if (amendmentsDir.exists()) {
            report.appendLine("## AMENDMENTS (${amendmentsDir.listFiles()?.filter { it.isDirectory }?.size ?: 0})")
            report.appendLine("Location: amendments")
            report.appendLine()

            val amendmentDirs = amendmentsDir.listFiles { it.isDirectory }
                ?.sortedBy { it.name } ?: emptyList()

            amendmentDirs.forEach { amendmentDir ->
                val metadataFile = File(amendmentDir, "metadata.json")
                if (metadataFile.exists()) {
                    try {
                        val metadata = json.decodeFromString<AmendmentInfo>(metadataFile.readText())
                        report.appendLine("### ${metadata.celex}")
                        report.appendLine("Title: ${metadata.title}")
                        report.appendLine("Adoption Date: ${formatDate(metadata.adoptionDate)}")
                        report.appendLine("Entry Force Date(s): ${metadata.entryForceDates.joinToString(", ") { formatDate(it) }}")
                    } catch (e: Exception) {
                        report.appendLine("### ${amendmentDir.name}")
                    }
                } else {
                    report.appendLine("### ${amendmentDir.name}")
                }

                val contentFiles = amendmentDir.listFiles { file ->
                    file.name != "metadata.json" && (file.name.endsWith(".fmx4") || file.name.endsWith(".pdf") || file.name.endsWith(".html"))
                }
                report.appendLine("Content Items: ${contentFiles?.size ?: 0}")
                contentFiles?.forEach { file ->
                    report.appendLine("  - ${file.name} (${formatFileSize(file.length())})")
                }
                report.appendLine()
            }
        }

        // Consolidations
        val consolidationsDir = File(workDirectory, "consolidations")
        if (consolidationsDir.exists()) {
            report.appendLine("## CONSOLIDATIONS (${consolidationsDir.listFiles()?.filter { it.isDirectory }?.size ?: 0})")
            report.appendLine("Location: consolidations")
            report.appendLine()

            val consolidationDirs = consolidationsDir.listFiles { it.isDirectory }
                ?.sortedBy { it.name } ?: emptyList()

            consolidationDirs.forEach { consolidationDir ->
                val metadataFile = File(consolidationDir, "metadata.json")
                if (metadataFile.exists()) {
                    try {
                        val metadata = json.decodeFromString<ConsolidationInfo>(metadataFile.readText())
                        report.appendLine("### ${metadata.consolidationId}")
                        report.appendLine("Date: ${formatDate(metadata.consolidationDate)}")
                        if (metadata.celex != null) {
                            report.appendLine("CELEX: ${metadata.celex}")
                        }
                        if (metadata.layer != null || metadata.version != null) {
                            report.appendLine("Layer: ${metadata.layer ?: "N/A"}, Version: ${metadata.version ?: "N/A"}")
                        }
                        report.appendLine("Incorporated Amendments: ${metadata.amendments.size}")
                    } catch (e: Exception) {
                        report.appendLine("### ${consolidationDir.name}")
                    }
                } else {
                    report.appendLine("### ${consolidationDir.name}")
                }

                val contentFiles = consolidationDir.listFiles { file ->
                    file.name != "metadata.json" && (file.name.endsWith(".fmx4") || file.name.endsWith(".pdf") || file.name.endsWith(".html"))
                }
                report.appendLine("Content Items: ${contentFiles?.size ?: 0}")
                contentFiles?.forEach { file ->
                    report.appendLine("  - ${file.name} (${formatFileSize(file.length())})")
                }
                report.appendLine()
            }
        }

        // Statistics
        report.appendLine("## STATISTICS")
        val totalSize = calculateDirectorySize(workDirectory)
        val totalFiles = countFiles(workDirectory)
        report.appendLine("Total Size: ${formatFileSize(totalSize)}")
        report.appendLine("Total Files: $totalFiles")

        return report.toString()
    }

    private fun formatDate(date: LocalDate): String {
        return "${date.dayOfMonth.toString().padStart(2, '0')}/${date.monthNumber.toString().padStart(2, '0')}/${date.year}"
    }

    private fun formatFileSize(size: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var unitIndex = 0
        var fileSize = size.toDouble()

        while (fileSize >= 1024 && unitIndex < units.size - 1) {
            fileSize /= 1024
            unitIndex++
        }

        return String.format("%.2f %s", fileSize, units[unitIndex])
    }

    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        directory.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }

    private fun countFiles(directory: File): Int {
        return directory.walkTopDown().count { it.isFile }
    }
}
