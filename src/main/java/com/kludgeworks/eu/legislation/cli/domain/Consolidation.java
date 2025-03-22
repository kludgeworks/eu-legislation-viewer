package com.kludgeworks.eu.legislation.cli.domain;

import java.time.LocalDate;

import lombok.Builder;

/**
 * Represents a consolidated version of a legal document
 */
@Builder
public record Consolidation(
    String uri,
    String celexNumber,
    LocalDate consolidationDate,
    String eli
) {}