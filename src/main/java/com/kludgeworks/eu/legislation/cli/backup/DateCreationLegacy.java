package com.kludgeworks.eu.legislation.cli.backup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public record DateCreationLegacy(
        @JacksonXmlProperty(localName = "VALUE") String value,
        @JacksonXmlProperty(localName = "YEAR") String year,
        @JacksonXmlProperty(localName = "MONTH") String month,
        @JacksonXmlProperty(localName = "DAY") String day,
        @JacksonXmlProperty(isAttribute = true) String type
) {}