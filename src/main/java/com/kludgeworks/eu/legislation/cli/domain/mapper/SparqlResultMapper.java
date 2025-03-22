package com.kludgeworks.eu.legislation.cli.domain.mapper;

import com.kludgeworks.eu.legislation.cli.domain.Amendment;
import com.kludgeworks.eu.legislation.cli.domain.Consolidation;
import com.kludgeworks.eu.legislation.cli.domain.ContentItem;
import com.kludgeworks.eu.legislation.cli.domain.Expression;
import com.kludgeworks.eu.legislation.cli.domain.LegalDocument;
import com.kludgeworks.eu.legislation.cli.domain.Manifestation;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.mapstruct.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Optional;

/**
 * MapStruct mapper for converting SPARQL query results to model objects
 */
@Mapper(componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    imports = {LocalDate.class, Optional.class})
public interface SparqlResultMapper {

    Logger log = LoggerFactory.getLogger(SparqlResultMapper.class);

    /**
     * Maps a QuerySolution to an Expression object
     */
    @Mapping(target = "uri", expression = "java(getResourceUri(solution, \"expression\"))")
    @Mapping(target = "language", expression = "java(getResourceUri(solution, \"language\"))")
    @Mapping(target = "manifestations", ignore = true)
    Expression toExpression(QuerySolution solution);

    /**
     * Maps a QuerySolution to a Manifestation object
     */
    @Mapping(target = "uri", expression = "java(getResourceUri(solution, \"manifestation\"))")
    @Mapping(target = "type", expression = "java(getLiteralString(solution, \"type\"))")
    @Mapping(target = "items", ignore = true)
    Manifestation toManifestation(QuerySolution solution);

    /**
     * Maps a QuerySolution to a ContentItem object
     */
    @Mapping(target = "uri", expression = "java(getResourceUri(solution, \"item\"))")
    @Mapping(target = "identifier", expression = "java(getLiteralString(solution, \"identifier\"))")
    @Mapping(target = "manifestationType", source = "manifestationType")
    @Mapping(target = "mimeType", expression = "java(getLiteralString(solution, \"mimeType\"))")
    ContentItem toContentItem(QuerySolution solution, String manifestationType);

    /**
     * Maps a QuerySolution to an Amendment object
     */
    @Mapping(target = "uri", expression = "java(getResourceUri(solution, \"amendment\"))")
    @Mapping(target = "celexNumber", expression = "java(getLiteralString(solution, \"amendmentCelex\"))")
    @Mapping(target = "date", expression = "java(parseDate(getLiteralString(solution, \"date\")))")
    @Mapping(target = "consolidations", ignore = true)
    Amendment toAmendment(QuerySolution solution);

    /**
     * Maps a QuerySolution to a Consolidation object
     */
    @Mapping(target = "uri", expression = "java(getResourceUri(solution, \"consolidation\"))")
    @Mapping(target = "celexNumber", expression = "java(getLiteralString(solution, \"celexId\"))")
    @Mapping(target = "consolidationDate", expression = "java(parseDate(getLiteralString(solution, \"consDate\")))")
    @Mapping(target = "eli", expression = "java(getLiteralString(solution, \"eli\"))")
    Consolidation toConsolidation(QuerySolution solution);

    /**
     * Maps a QuerySolution to a LegalDocument object
     */
    @Mapping(target = "uri", expression = "java(getResourceUri(solution, \"work\"))")
    @Mapping(target = "celexNumber", expression = "java(getLiteralString(solution, \"celex\"))")
    @Mapping(target = "title", expression = "java(getLiteralString(solution, \"title\"))")
    @Mapping(target = "documentDate", expression = "java(parseDate(getLiteralString(solution, \"date\")))")
    @Mapping(target = "expressions", ignore = true)
    LegalDocument toLegalDocument(QuerySolution solution);

    /**
     * Helper method to safely extract a URI from a Resource in a QuerySolution
     */
    default String getResourceUri(QuerySolution solution, String varName) {
        try {
            Resource resource = solution.getResource(varName);
            return resource != null ? resource.getURI() : null;
        } catch (Exception e) {
            log.warn("Failed to get resource URI for variable {}: {}", varName, e.getMessage());
            return null;
        }
    }

    /**
     * Helper method to safely extract a String from a Literal in a QuerySolution
     */
    default String getLiteralString(QuerySolution solution, String varName) {
        try {
            Literal literal = solution.getLiteral(varName);
            return literal != null ? literal.getString() : null;
        } catch (Exception e) {
            log.warn("Failed to get literal string for variable {}: {}", varName, e.getMessage());
            return null;
        }
    }

    /**
     * Helper method to parse date strings from SPARQL results
     */
    default LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }

        try {
            // Handle different date formats that might come from SPARQL
            String normalizedDate = dateString.length() >= 10 ? dateString.substring(0, 10) : dateString;
            return LocalDate.parse(normalizedDate);
        } catch (Exception e) {
            log.warn("Failed to parse date {}: {}", dateString, e.getMessage());
            return null;
        }
    }
}