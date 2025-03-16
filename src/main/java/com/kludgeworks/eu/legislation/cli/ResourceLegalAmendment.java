package com.kludgeworks.eu.legislation.cli;

import io.soabase.recordbuilder.core.RecordBuilder;

import java.net.URI;
import java.time.LocalDate;

/**
 * Represents a legal resource that amends another legal resource.
 * Maps to the cdm:resource_legal_amends_resource_legal relationship.
 */
@RecordBuilder
public record ResourceLegalAmendment(
        URI amendmentUri,
        String resourceLegalIdCelex,
        String title,
        LocalDate workDateDocument,
        LocalDate resourceLegalDateEntryIntoForce,
        LocalDate resourceLegalDateApplicability,
        URI workCreatedByAgent,
        String amendmentType,
        String subdivisionConcerned
) {}