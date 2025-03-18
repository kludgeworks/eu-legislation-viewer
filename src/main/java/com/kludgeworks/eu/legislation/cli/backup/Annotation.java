package com.kludgeworks.eu.legislation.cli.backup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "ANNOTATION")
public record Annotation(
        @JacksonXmlProperty(localName = "PARAGRAPH") String paragraph,
        @JacksonXmlProperty(localName = "ARTICLE") String article,
        @JacksonXmlProperty(localName = "COMMENT_ON_LEGAL_BASIS") String commentOnLegalBasis
) {}