package com.kludgeworks.eu.legislation.cli.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import lombok.Builder;
import lombok.With;

/**
 * Represents a legal document work identified by CELEX number
 */
@Builder
public record LegalDocument(
    String celexNumber,
    String uri,
    String title,
    @With LocalDate documentDate,
    List<Expression> expressions
) {

    public LegalDocument {
        expressions = List.copyOf( Objects.requireNonNullElse( expressions, List.of() ) );
    }

}