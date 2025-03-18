package com.kludgeworks.eu.legislation.cli.backup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "URI")
public record URI(
        @JacksonXmlProperty(localName = "VALUE") String value,
        @JacksonXmlProperty(localName = "IDENTIFIER") String identifier,
        @JacksonXmlProperty(localName = "TYPE") String type) {}
