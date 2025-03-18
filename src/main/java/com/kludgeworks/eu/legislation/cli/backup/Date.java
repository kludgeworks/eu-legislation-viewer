package com.kludgeworks.eu.legislation.cli.backup;

import java.util.List;

public record Date(
        String value,
        String year,
        String month,
        String day,
        List<Annotation> annotations,
        String type
) {}