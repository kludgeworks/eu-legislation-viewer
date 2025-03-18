package com.kludgeworks.eu.legislation.cli.backup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName = "BASED_ON")
public record BasedOn(
        @JacksonXmlProperty(localName = "URI") URI uri,
        @JacksonXmlProperty(localName = "SAMEAS")  List<SameAs> sameAs,
        @JacksonXmlProperty(localName = "ANNOTATION") Annotation annotation,
        @JacksonXmlProperty(isAttribute = true) String type
) {}