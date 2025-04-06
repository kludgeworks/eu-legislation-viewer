package com.kludgeworks.eu.legislation.cli;

import com.kludgeworks.eu.legislation.cli.domain.Amendment;
import com.kludgeworks.eu.legislation.cli.domain.Consolidation;
import com.kludgeworks.eu.legislation.cli.domain.ContentItem;
import com.kludgeworks.eu.legislation.cli.domain.LegislationService;
import com.kludgeworks.eu.legislation.cli.domain.LegislationServiceException;
import com.kludgeworks.eu.legislation.cli.domain.Manifestation;
import com.kludgeworks.eu.legislation.cli.local.ContentDownloadService;
import com.kludgeworks.eu.legislation.cli.local.DownloadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shell commands for downloading and managing EU legislation
 */
@ShellComponent
class LegislationCommands {

    private static final Logger logger = LoggerFactory.getLogger(LegislationCommands.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LegislationService legislationService;
    private final ContentDownloadService contentDownloadService;

    @Autowired
    LegislationCommands(LegislationService legislationService, ContentDownloadService contentDownloadService) {
        this.legislationService = legislationService;
        this.contentDownloadService = contentDownloadService;
    }

    /**
     * Download legislation by CELEX number
     *
     * @param celexNumber The CELEX number
     * @param baseDir Base directory for downloaded files (optional)
     * @param includeAmendments Whether to include amendments (optional)
     * @param includeConsolidations Whether to include consolidations (optional)
     * @return Status message
     */
    @ShellMethod(value = "Download legislation by CELEX number", key = "download")
    String downloadLegislation(
        @ShellOption(help = "CELEX number") String celexNumber,
        @ShellOption(help = "Base directory", defaultValue = "./legislation") String baseDir,
        @ShellOption(help = "Include amendments", defaultValue = "true") boolean includeAmendments,
        @ShellOption(help = "Include consolidations", defaultValue = "true") boolean includeConsolidations
    ) {
        try {
            Path outputDir = Paths.get(baseDir, celexNumber);
            Files.createDirectories(outputDir);

            logger.info("Downloading legislation: {} to {}", celexNumber, outputDir);

            // Step 1: Get English expression
            logger.info("Step 1: Finding English expression...");
            Optional<String> engExpression = legislationService.findExpression(celexNumber, "ENG");

            if ( engExpression.isEmpty() ) {
                return "Error: Could not find English expression for CELEX " + celexNumber;
            }

            logger.info("English expression URI: {}", engExpression);

            // Step 2: Get manifestations
            logger.info("Step 2: Finding manifestations");
            List<Manifestation> manifestations = legislationService.getManifestations( engExpression.orElse( null ) );

            if (manifestations.isEmpty()) {
                return "Error: No manifestations found for expression " + engExpression;
            }

            logger.info("Found {} manifestations", manifestations.size());
            manifestations.forEach(m -> logger.info("- Type: {}, URI: {}", m.type(), m.uri()));

            // Step 3: Process each manifestation type
            List<ContentItem> allContentItems = new ArrayList<>();

            for (Manifestation manifestation : manifestations) {
                logger.info("Step 3: Finding content items for {} manifestation...", manifestation.type());
                List<ContentItem> contentItems = legislationService.getContentItems(manifestation);
                allContentItems.addAll(contentItems);
            }

            if (allContentItems.isEmpty()) {
                return "Error: No content items found for manifestations";
            }

            // Step 4: Download content
            List<DownloadResult> results = new ArrayList<>();
            for (ContentItem item : allContentItems) {
                DownloadResult result = contentDownloadService.downloadContent(item, outputDir);
                results.add(result);
            }

            logger.info("Downloaded {} files", results.size());

            StringBuilder result = new StringBuilder();
            result.append("Legislation ").append(celexNumber).append(" downloaded successfully to ").append(outputDir).append("\n");

            // Process amendments and consolidations if requested
            if (includeAmendments) {
                String amendmentsResult = processAmendments(celexNumber, baseDir, includeConsolidations);
                result.append("\n").append(amendmentsResult);
            }

            return result.toString();
        } catch (Exception e) {
            logger.error("Error downloading legislation {}: {}", celexNumber, e.getMessage(), e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Process amendments for a legislation
     *
     * @param celexNumber The CELEX number
     * @param baseDir Base directory for downloaded files
     * @param includeConsolidations Whether to include consolidations
     * @return Status message
     */
//    @ShellMethod(value = "Process amendments for legislation", key = "amendments")
    String processAmendments(
        @ShellOption(help = "CELEX number") String celexNumber,
        @ShellOption(help = "Base directory", defaultValue = "./legislation") String baseDir,
        @ShellOption(help = "Include consolidations", defaultValue = "true") boolean includeConsolidations
    ) {
        try {
            Path originalDir = Paths.get(baseDir, celexNumber);
            Files.createDirectories(originalDir);

            logger.info("Finding amendments to {}...", celexNumber);
            List<Amendment> amendments = legislationService.getAmendments(celexNumber);

            if (amendments.isEmpty()) {
                return "No amendments found for CELEX " + celexNumber;
            }

            logger.info("Found {} amendments to {}", amendments.size(), celexNumber);

            // Create a summary file
            Path summaryFile = originalDir.resolve("amendments_summary.md");
            StringBuilder summary = new StringBuilder();
            summary.append("# Amendments to ").append(celexNumber).append("\n");
            summary.append("Total amendments found: ").append(amendments.size()).append("\n\n");
            summary.append("| # | CELEX | Date | Title | Consolidations |\n");
            summary.append("| --- | --- | --- | --- | --- |\n");

            int counter = 0;
            for (Amendment amendment : amendments) {
                counter++;

                logger.info("\nProcessing amendment {}/{}: {}", counter, amendments.size(), amendment.celexNumber());
                logger.info("Date: {}", amendment.date() != null ? amendment.date().format(DATE_FORMATTER) : "(No date)");

                // Step 1: Finding English expression for amendment
                logger.info("Step 1: Finding English expression for amendment...");
                Optional<String> engExpression = legislationService.findExpression(amendment.celexNumber(), "ENG");

                if ( engExpression.isEmpty() ) {
                    logger.warn("Could not find English expression for amendment CELEX {}", amendment.celexNumber());
                    continue;
                }

                logger.info("English expression URI: {}", engExpression);

                // Step 2: Finding manifestations for amendment
                logger.info("Step 2: Finding manifestations (fmx4 and xhtml) for amendment...");
                List<Manifestation> manifestations = legislationService.getManifestations( engExpression.orElse( null ) );

                if (manifestations.isEmpty()) {
                    logger.warn("No manifestations found for amendment expression {}", engExpression);
                    continue;
                }

                logger.info("Found manifestations for amendment:");
                manifestations.forEach(m -> logger.info("- Type: {}, URI: {}", m.type(), m.uri()));

                // Step 3: Process each manifestation type
                List<ContentItem> allContentItems = new ArrayList<>();

                for (Manifestation manifestation : manifestations) {
                    logger.info("Step 3: Finding content items for {} manifestation...", manifestation.type());
                    List<ContentItem> contentItems = legislationService.getContentItems(manifestation);
                    allContentItems.addAll(contentItems);
                }

                if (allContentItems.isEmpty()) {
                    logger.warn("No content items found for amendment manifestations");
                    continue;
                }

                // Create directory for amendment
                Path amendmentDir = originalDir.resolve(amendment.celexNumber());
                Files.createDirectories(amendmentDir);

                // Step 4: Download amendment content
                logger.info("Step 4: Downloading amendment content...");
                List<DownloadResult> results = new ArrayList<>();
                for (ContentItem item : allContentItems) {
                    DownloadResult result = contentDownloadService.downloadContent(item, amendmentDir);
                    results.add(result);
                }

                logger.info("Downloaded {} files", results.size());

                // Process consolidations for this amendment if requested
                int consolidationsCount = 0;
                if (includeConsolidations) {
                    logger.info("Looking for consolidations that incorporate this amendment...");
                    consolidationsCount = processConsolidations(amendment.celexNumber(), amendmentDir);
                }

                // Add to summary
                summary.append("| ")
                    .append(counter).append(" | ")
                    .append(amendment.celexNumber()).append(" | ")
                    .append(amendment.date() != null ? amendment.date().format(DATE_FORMATTER) : "(No date)").append(" | ")
                    .append(consolidationsCount).append(" |\n");
            }

            // Write summary to file
            Files.writeString(summaryFile, summary.toString());

            return "All amendments processed successfully!\nSummary written to: " + summaryFile;
        } catch (Exception e) {
            logger.error("Error processing amendments for {}: {}", celexNumber, e.getMessage(), e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Process consolidations for an amendment
     *
     * @param amendmentCelex The amendment CELEX number
     * @param amendmentDir The amendment directory
     * @return Number of consolidations processed
     */
    private int processConsolidations(String amendmentCelex, Path amendmentDir)
        throws IOException, LegislationServiceException {
        logger.info("Finding consolidations that incorporate amendment {}...", amendmentCelex);
        List<Consolidation> consolidations = legislationService.getConsolidations(amendmentCelex);

        if (consolidations.isEmpty()) {
            logger.info("No consolidations found that incorporate amendment {}", amendmentCelex);
            return 0;
        }

        logger.info("Found {} consolidations incorporating amendment {}", consolidations.size(), amendmentCelex);

        // Create a summary file
        Path summaryFile = amendmentDir.resolve("consolidations_summary.md");
        StringBuilder summary = new StringBuilder();
        summary.append("# Consolidations incorporating amendment ").append(amendmentCelex).append("\n");
        summary.append("Total consolidations found: ").append(consolidations.size()).append("\n\n");
        summary.append("| # | CELEX | Date | ELI |\n");
        summary.append("| --- | --- | --- | --- |\n");

        int counter = 0;
        for (Consolidation consolidation : consolidations) {
            counter++;

            logger.info("\nProcessing consolidation {}/{}: {}", counter, consolidations.size(), consolidation.celexNumber());
            logger.info("Date: {}", consolidation.consolidationDate() != null ?
                consolidation.consolidationDate().format(DATE_FORMATTER) : "(No date)");
            logger.info("ELI: {}", consolidation.eli() != null ? consolidation.eli() : "(No ELI available)");

            // Add to summary
            summary.append("| ")
                .append(counter).append(" | ")
                .append(consolidation.celexNumber()).append(" | ")
                .append(consolidation.consolidationDate() != null ?
                    consolidation.consolidationDate().format(DATE_FORMATTER) : "(No date)").append(" | ")
                .append(consolidation.eli() != null ? consolidation.eli() : "(No ELI available)").append(" |\n");

            // Download this consolidation
            Path consolidationDir = amendmentDir.resolve(consolidation.celexNumber());
            Files.createDirectories(consolidationDir);

            // Step 1: Get English expression
            Optional<String> engExpression = legislationService.findExpression(consolidation.celexNumber(), "ENG");

            if ( engExpression.isPresent() ) {
                // Step 2: Get manifestations
                List<Manifestation> manifestations = legislationService.getManifestations( engExpression.orElse( null ) );

                // Step 3: Process each manifestation type
                List<ContentItem> allContentItems = new ArrayList<>();

                for (Manifestation manifestation : manifestations) {
                    List<ContentItem> contentItems = legislationService.getContentItems(manifestation);
                    allContentItems.addAll(contentItems);
                }

                // Step 4: Download content
                List<DownloadResult> results = new ArrayList<>();
                for (ContentItem item : allContentItems) {
                    DownloadResult result = contentDownloadService.downloadContent(item, consolidationDir);
                    results.add(result);
                }
                logger.info("Downloaded {} files", results.size());
            }
        }

        // Write summary to file
        Files.writeString(summaryFile, summary.toString());

        logger.info("All consolidations processed successfully!");
        return consolidations.size();
    }

    /**
     * List all amendments for a legislation
     *
     * @param celexNumber The CELEX number
     * @return List of amendments
     */
//    @ShellMethod(value = "List amendments for legislation", key = "list-amendments")
    String listAmendments(@ShellOption(help = "CELEX number") String celexNumber) {
        try {
            List<Amendment> amendments = legislationService.getAmendments(celexNumber);

            if (amendments.isEmpty()) {
                return "No amendments found for CELEX " + celexNumber;
            }

            StringBuilder result = new StringBuilder();
            result.append("Found ").append(amendments.size()).append(" amendments for CELEX ").append(celexNumber).append(":\n\n");

            int counter = 0;
            for (Amendment amendment : amendments) {
                counter++;
                result.append(counter).append(". CELEX: ").append(amendment.celexNumber()).append("\n");
                result.append("   Date: ").append(amendment.date() != null ?
                    amendment.date().format(DATE_FORMATTER) : "(No date)").append("\n\n");
            }

            return result.toString();
        } catch (Exception e) {
            logger.error("Error listing amendments for {}: {}", celexNumber, e.getMessage(), e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * List all consolidations for an amendment
     *
     * @param amendmentCelex The amendment CELEX number
     * @return List of consolidations
     */
//    @ShellMethod(value = "List consolidations for amendment", key = "list-consolidations")
    String listConsolidations(@ShellOption(help = "Amendment CELEX number") String amendmentCelex) {
        try {
            List<Consolidation> consolidations = legislationService.getConsolidations(amendmentCelex);

            if (consolidations.isEmpty()) {
                return "No consolidations found for amendment CELEX " + amendmentCelex;
            }

            StringBuilder result = new StringBuilder();
            result.append("Found ").append(consolidations.size()).append(" consolidations for amendment CELEX ").append(amendmentCelex).append(":\n\n");

            int counter = 0;
            for (Consolidation consolidation : consolidations) {
                counter++;
                result.append(counter).append(". CELEX: ").append(consolidation.celexNumber()).append("\n");
                result.append("   Date: ").append(consolidation.consolidationDate() != null ?
                    consolidation.consolidationDate().format(DATE_FORMATTER) : "(No date)").append("\n");
                result.append("   ELI: ").append(consolidation.eli() != null ? consolidation.eli() : "(No ELI available)").append("\n\n");
            }

            return result.toString();
        } catch (Exception e) {
            logger.error("Error listing consolidations for {}: {}", amendmentCelex, e.getMessage(), e);
            return "Error: " + e.getMessage();
        }
    }
}