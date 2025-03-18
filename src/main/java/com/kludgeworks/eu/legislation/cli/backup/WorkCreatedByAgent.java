package com.kludgeworks.eu.legislation.cli.backup;

import java.util.List;

public record WorkCreatedByAgent(
        URI uri,
        String opCode,
        String identifier,
        String prefLabel,
        List<String> altLabels,
        String type
) {}