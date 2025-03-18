package com.kludgeworks.eu.legislation.cli.backup;

import java.util.List;

public record ResourceLegalBasedOnResourceLegal(
        URI uri,
        List<SameAs> sameAs,
        Annotation annotation,
        String type
) {}
