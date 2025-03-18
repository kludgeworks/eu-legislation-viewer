package com.kludgeworks.eu.legislation.cli.backup;

public record ResourceLegalDateEndOfValidity(
        String value,
        String year,
        String month,
        String day,
        String type
) {}