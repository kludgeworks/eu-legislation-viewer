package com.kludgeworks.eu.legislation.cli;

import io.soabase.recordbuilder.core.RecordBuilder;

import java.net.URI;
import java.time.LocalDate;

/**
 * Represents a Work entity (cdm:work) that is also a legal resource (cdm:resource_legal).
 * This follows the Common Data Model (CDM) terminology used in the EU Publications Office.
 */
@RecordBuilder
public record ResourceLegal(
        String title,
        LocalDate workDateDocument,
        LocalDate resourceLegalDateEntryIntoForce,
        boolean resourceLegalInForce,
        URI type,
        URI workCreatedByAgent,
        URI resourceLegalEli,
        String resourceLegalIdCelex
) {}