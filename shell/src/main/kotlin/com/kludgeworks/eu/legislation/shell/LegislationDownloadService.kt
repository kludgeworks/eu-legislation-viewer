package com.kludgeworks.eu.legislation.shell

import com.kludgeworks.eu.legislation.cellarclient.ContentDownloadService
import com.kludgeworks.eu.legislation.cellarclient.sparql.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

@Service
class LegislationDownloadService(
    private val sparqlApiClient: SparqlApiClient,
    private val contentDownloadService: ContentDownloadService,
    private val xmlFormattingService: XmlFormattingService
) {
    private val logger = LoggerFactory.getLogger(LegislationDownloadService::class.java)
    private val json = Json { prettyPrint = true }

    /**
     * Downloads all content items for a work, its amendments, and consolidations
     * @param celexNumber The CELEX number of the main work
     * @param baseDirectory The base directory where files will be saved
     * @param languageCode The language code (default: "eng" for English)
     */
    suspend fun downloadAllContent(
        celexNumber: String,
        baseDirectory: String,
        languageCode: String = "eng"
    ) {
        val workDir = Paths.get(baseDirectory, celexNumber).toFile()
        workDir.mkdirs()

        logger.info("Starting download for CELEX: $celexNumber")

        // Download original work content
        downloadOriginalWork(celexNumber, workDir, languageCode)

        // Download amendments (ordered by entry force date)
        downloadAmendments(celexNumber, workDir, languageCode)

        // Download consolidations (ordered by entry force date)
        downloadConsolidations(celexNumber, workDir, languageCode)

        logger.info("Download completed for CELEX: $celexNumber")
    }

    private suspend fun downloadOriginalWork(
        celexNumber: String,
        workDir: File,
        languageCode: String
    ) {
        logger.info("Downloading original work content for CELEX: $celexNumber")

        val originalDir = File(workDir, "original")
        originalDir.mkdirs()

        try {
            val contentItems = sparqlApiClient.fetchContentItems(celexNumber, languageCode)
            downloadContentItems(contentItems, originalDir, celexNumber)
        } catch (e: Exception) {
            logger.error("Error downloading original work: ${e.message}", e)
        }
    }

    private suspend fun downloadAmendments(
        celexNumber: String,
        workDir: File,
        languageCode: String
    ) {
        logger.info("Fetching amendments for CELEX: $celexNumber")

        val amendmentsDir = File(workDir, "amendments")
        amendmentsDir.mkdirs()

        try {
            val amendments = sparqlApiClient.fetchAmendments(celexNumber, languageCode)
                .sortedBy { it.entryForceDates.firstOrNull() ?: it.adoptionDate }

            logger.info("Found ${amendments.size} amendments")

            coroutineScope {
                amendments.map { amendment ->
                    async {
                        downloadAmendment(amendment, amendmentsDir, languageCode)
                    }
                }.awaitAll()
            }
        } catch (e: Exception) {
            logger.error("Error downloading amendments: ${e.message}", e)
        }
    }

    private suspend fun downloadAmendment(
        amendment: AmendmentInfo,
        amendmentsDir: File,
        languageCode: String
    ) {
        val date = amendment.entryForceDates.firstOrNull() ?: amendment.adoptionDate
        val dirName = "${date}_${amendment.celex}"
        val amendmentDir = File(amendmentsDir, dirName)
        amendmentDir.mkdirs()

        logger.info("Downloading amendment: ${amendment.celex}")

        try {

            File(amendmentDir, "metadata.json").writeText(json.encodeToString(amendment))

            // Download content items
            val contentItems = sparqlApiClient.fetchContentItems(amendment.celex, languageCode)
            downloadContentItems(contentItems, amendmentDir, amendment.celex)
        } catch (e: Exception) {
            logger.error("Error downloading amendment ${amendment.celex}: ${e.message}", e)
        }
    }

    private suspend fun downloadConsolidations(
        celexNumber: String,
        workDir: File,
        languageCode: String
    ) {
        logger.info("Fetching consolidations for CELEX: $celexNumber")

        val consolidationsDir = File(workDir, "consolidations")
        consolidationsDir.mkdirs()

        try {
            val consolidations = sparqlApiClient.fetchConsolidations(celexNumber)
                .sortedBy { it.consolidationDate }

            logger.info("Found ${consolidations.size} consolidations")

            coroutineScope {
                consolidations.map { consolidation ->
                    async {
                        downloadConsolidation(consolidation, consolidationsDir, languageCode)
                    }
                }.awaitAll()
            }
        } catch (e: Exception) {
            logger.error("Error downloading consolidations: ${e.message}", e)
        }
    }

    private suspend fun downloadConsolidation(
        consolidation: ConsolidationInfo,
        consolidationsDir: File,
        languageCode: String
    ) {
        val dirName = "${consolidation.consolidationDate}_${consolidation.consolidationId.replace("/", "_")}"
        val consolidationDir = File(consolidationsDir, dirName)
        consolidationDir.mkdirs()

        logger.info("Downloading consolidation: ${consolidation.consolidationId}")

        try {
            // Save metadata
            File(consolidationDir, "metadata.json").writeText(json.encodeToString(consolidation))

            // Download content items if CELEX is available
            if (consolidation.celex != null) {
                val contentItems = sparqlApiClient.fetchContentItems(consolidation.celex!!, languageCode)
                downloadContentItems(contentItems, consolidationDir, consolidation.celex!!)
            } else {
                logger.warn("No CELEX available for consolidation: ${consolidation.consolidationId}")
            }
        } catch (e: Exception) {
            logger.error("Error downloading consolidation ${consolidation.consolidationId}: ${e.message}", e)
        }
    }

    private suspend fun downloadContentItems(
        contentItems: List<ContentItem>,
        targetDir: File,
        identifier: String
    ) {
        if (contentItems.isEmpty()) {
            logger.warn("No content items found for: $identifier")
            return
        }

        coroutineScope {
            contentItems.map { item ->
                async {
                    downloadContentItem(item, targetDir)
                }
            }.awaitAll()
        }
    }

    private suspend fun downloadContentItem(
        item: ContentItem,
        targetDir: File
    ) {
        // Use appropriate extensions for different manifestation types
        val extension = when (item.manifestationType) {
            "pdfa1a" -> "pdf"
            "fmx4" -> "xml"
            else -> item.manifestationType
        }
        val filename = "${item.identifier}.$extension"
        val targetFile = File(targetDir, filename)

        try {
            logger.info("Downloading: $filename")

            val tempFile = contentDownloadService.download(item.itemUri) { downloaded, total ->
                val progress = if (total != null) {
                    " (${(downloaded * 100 / total)}%)"
                } else {
                    ""
                }
                logger.debug("Downloading $filename: $downloaded bytes$progress")
            }

            if (extension == "xml") {
                logger.info("Formatting XML file: $filename")
                xmlFormattingService.prettyPrint(tempFile, tempFile)
            }

            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )

            logger.info("Downloaded: $filename to ${targetFile.absolutePath}")
        } catch (e: Exception) {
            logger.error("Error downloading $filename: ${e.message}", e)
        }
    }
}
