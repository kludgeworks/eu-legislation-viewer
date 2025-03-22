package com.kludgeworks.eu.legislation.cli.domain;

import java.util.List;
import java.util.Objects;

import lombok.Builder;

/**
 * Represents a manifestation of an expression (specific format)
 */
@Builder
public record Manifestation(
    String uri,
    String type,
    List<ContentItem> items
) {
    public Manifestation {
        items = List.copyOf( Objects.requireNonNullElse( items, List.of() ) );
    }
}
