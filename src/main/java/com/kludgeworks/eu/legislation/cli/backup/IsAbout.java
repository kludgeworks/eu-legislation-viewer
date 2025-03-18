package com.kludgeworks.eu.legislation.cli.backup;

import java.util.List;

public record IsAbout(
        URI uri,
        String identifier,
        String prefLabel,
        List<String> altLabels,
        String opCode,
        String type
) {}