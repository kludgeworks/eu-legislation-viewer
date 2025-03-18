package com.kludgeworks.eu.legislation.cli.backup;

import java.net.URI;
import java.util.List;

public record WorkHasExpression(
        URI uri,
        List<SameAs> sameAs,
        String type
) {}