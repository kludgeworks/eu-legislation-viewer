package com.kludgeworks.eu.legislation.cli.backup;

public record WorkHasResourceType(
        URI uri,
        String opCode,
        String identifier,
        String prefLabel,
        String type
) {}