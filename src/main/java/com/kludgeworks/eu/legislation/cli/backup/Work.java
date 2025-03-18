package com.kludgeworks.eu.legislation.cli.backup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName = "WORK")
public record Work(
        @JacksonXmlProperty(localName = "URI") URI uri,
        @JacksonXmlProperty(localName = "SAMEAS") List<SameAs> sameAs,
        @JacksonXmlProperty(localName = "DATE_CREATION_LEGACY") DateCreationLegacy dateCreationLegacy,
        @JacksonXmlProperty(localName = "BASED_ON") List<BasedOn> basedOn,
        List<WorkHasExpression> workHasExpression,
        List<IsAbout> isAbout,
        List<Identifier> identifiers,
        ResourceLegalNumberNaturalCelex resourceLegalNumberNaturalCelex,
        LastModificationDate lastModificationDate,
        ResourceLegalEli resourceLegalEli,
        List<WorkIdDocument> workIdDocuments,
        ResourceLegalRepertoire resourceLegalRepertoire,
        Version version,
        ResourceLegalDateEntryIntoForce resourceLegalDateEntryIntoForce,
        ResourceLegalCommentInternal resourceLegalCommentInternal,
        List<DateDocument> dateDocuments,
        CreatedBy createdBy,
        WorkDateDocument workDateDocument,
        List<ResourceLegalBasedOnResourceLegal> resourceLegalBasedOnResourceLegals,
        WorkIsAboutConceptEurovoc workIsAboutConceptEurovoc,
        List<Date> dates,
        ResourceLegalDateEndOfValidity resourceLegalDateEndOfValidity,
        CreationDate creationDate,
        ResourceLegalIsAboutSubjectMatter resourceLegalIsAboutSubjectMatter,
        WorkCreatedByAgent workCreatedByAgent,
        ResourceLegalType resourceLegalType,
        ResourceLegalIsAboutConceptDirectoryCode resourceLegalIsAboutConceptDirectoryCode,
        WorkHasResourceType workHasResourceType,
        Year year,
        List<String> types
) {}
