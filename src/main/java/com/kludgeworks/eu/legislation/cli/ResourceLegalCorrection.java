package com.kludgeworks.eu.legislation.cli;

import io.soabase.recordbuilder.core.RecordBuilder;

import java.net.URI;
import java.time.LocalDate;

/**
 * Represents a legal resource that corrects another legal resource.
 * Maps to the cdm:resource_legal_corrects_resource_legal relationship.
 */
@RecordBuilder
public record ResourceLegalCorrection(
        URI correctionUri,
        String resourceLegalIdCelex,
        String title,
        LocalDate workDateDocument,
        LocalDate resourceLegalDateEntryIntoForce,
        String resourceLegalComment
) {}