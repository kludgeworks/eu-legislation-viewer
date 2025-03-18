package com.kludgeworks.eu.legislation.cli.backup;

public record WorkDateDocument(
        String value,
        String year,
        String month,
        String day,
        Annotation annotation,
        String type
) {}