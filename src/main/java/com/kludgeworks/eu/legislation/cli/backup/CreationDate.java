package com.kludgeworks.eu.legislation.cli.backup;

public record CreationDate(
        String value,
        String year,
        String month,
        String day,
        String type
) {}