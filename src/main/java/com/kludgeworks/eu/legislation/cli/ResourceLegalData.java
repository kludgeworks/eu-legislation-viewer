package com.kludgeworks.eu.legislation.cli;

import io.soabase.recordbuilder.core.RecordBuilder;

import java.util.List;

/**
 * A comprehensive record containing all information about a legal resource.
 * This aggregates the work/resource_legal with its amendments and corrections.
 */
@RecordBuilder
public record ResourceLegalData(
        ResourceLegal work,
        List<ResourceLegalAmendment> amendments,
        List<ResourceLegalCorrection> corrections
) {}