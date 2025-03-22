package com.kludgeworks.eu.legislation.cli.domain;

import lombok.Builder;

/**
 * Represents a content item of a manifestation
 */
@Builder
public record ContentItem(
    String uri,
    String identifier,
    String manifestationType,
    String mimeType
) {}
