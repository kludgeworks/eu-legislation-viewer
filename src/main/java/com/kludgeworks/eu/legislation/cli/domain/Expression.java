package com.kludgeworks.eu.legislation.cli.domain;

import java.util.List;
import java.util.Objects;

import lombok.Builder;

/**
 * Represents a language expression of a legal document
 */
@Builder
public record Expression(
    String uri,
    String language,
    List<Manifestation> manifestations
) {
    public Expression {
        manifestations = List.copyOf( Objects.requireNonNullElse( manifestations, List.of() ) );
    }
}