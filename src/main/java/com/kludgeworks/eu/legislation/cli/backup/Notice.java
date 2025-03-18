package com.kludgeworks.eu.legislation.cli.backup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "NOTICE")
public record Notice(
        @JacksonXmlProperty(isAttribute = true) String decoding,
        @JacksonXmlProperty(isAttribute = true) String type,
        @JacksonXmlProperty(localName = "WORK") Work work) {}