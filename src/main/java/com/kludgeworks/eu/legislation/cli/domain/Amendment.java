package com.kludgeworks.eu.legislation.cli.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import lombok.Builder;
import lombok.With;

/**
 * Represents an amendment to a legal document
 */
@Builder
public record Amendment(
    String uri,
    String celexNumber,
    @With LocalDate date,
    List<Consolidation> consolidations
) {
    public Amendment {
        consolidations = List.copyOf( Objects.requireNonNullElse( consolidations, List.of() ) );
    }
}