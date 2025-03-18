package com.kludgeworks.eu.legislation.cli;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTPBuilder;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CellarService {

    private static final String SPARQL_ENDPOINT = "http://publications.europa.eu/webapi/rdf/sparql";

    /**
     * Creates the SPARQL query for basic legal resource information.
     */
    private static String createResourceLegalQuery(String celexNumber) {
        return """
                PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
                PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                
                SELECT DISTINCT ?title ?date_document ?date_entry_force ?in_force 
                               ?type ?author ?eli ?celex
                WHERE {
                  # Retrieve the work by its CELEX number
                  ?work cdm:resource_legal_id_celex "%s"^^xsd:string .
                  ?work cdm:resource_legal_id_celex ?celex .
                
                  # Get title from English expression
                  {
                    SELECT ?title WHERE {
                      ?work cdm:resource_legal_id_celex "%s"^^xsd:string .
                      ?expression cdm:expression_belongs_to_work ?work ;
                                  cdm:expression_uses_language <http://publications.europa.eu/resource/authority/language/ENG> .
                      ?expression ?p ?title .
                      FILTER(CONTAINS(STR(?p), "title"))
                      FILTER(isLiteral(?title))
                    } LIMIT 1
                  }
                
                  # Basic metadata
                  OPTIONAL { ?work cdm:work_date_document ?date_document }
                  OPTIONAL { ?work cdm:resource_legal_date_entry-into-force ?date_entry_force }
                  OPTIONAL { ?work cdm:resource_legal_in-force ?in_force }
                
                  # Get type information
                  OPTIONAL { 
                    ?work rdf:type ?type . 
                    FILTER(?type = cdm:legislation_secondary)
                  }
                
                  # Get author information
                  OPTIONAL { ?work cdm:work_created_by_agent ?author }
                
                  # Get ELI identifier
                  OPTIONAL { ?work cdm:resource_legal_eli ?eli }
                }
                LIMIT 1
                """.formatted(celexNumber, celexNumber);
    }

    /**
     * Enhanced query for amendments with additional metadata
     */
    private static String createAmendmentsQuery(String celexNumber) {
        return """
                PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
                PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX owl: <http://www.w3.org/2002/07/owl#>
                PREFIX j.2: <http://publications.europa.eu/ontology/annotation#>
                
                SELECT DISTINCT ?amendment ?amendmentCelex ?title ?date_document
                               ?date_entry_force ?date_applicability ?author
                               ?amendment_type ?location
                WHERE {
                  # Retrieve the work by its CELEX number
                  ?work cdm:resource_legal_id_celex "%s"^^xsd:string .
                
                  # Find amendments to the work
                  ?amendment cdm:resource_legal_amends_resource_legal ?work ;
                             cdm:resource_legal_id_celex ?amendmentCelex .

                  # Get annotation details about the amendment action
                  OPTIONAL {
                    # Look for axioms that describe the relationship between the amendment and the work
                    ?axiom rdf:type owl:Axiom ;
                           owl:annotatedSource ?amendment ;
                           owl:annotatedProperty ?amendmentProperty ;
                           owl:annotatedTarget ?work .

                    # Only consider certain amendment types
                    FILTER(?amendmentProperty IN (
                      cdm:resource_legal_amends_resource_legal,
                      cdm:resource_legal_does_insertion_of_resource_legal,
                      cdm:resource_legal_does_replacement_of_resource_legal,
                      cdm:resource_legal_does_deletion_of_resource_legal,
                      cdm:resource_legal_repeals_resource_legal
                    ))

                    # The amendment property itself indicates the type (replacement, deletion, etc.)
                    BIND(replace(str(?amendmentProperty), "http://publications.europa.eu/ontology/cdm#resource_legal_does_", "") as ?amendment_type)

                    # Get subdivision concerned (i.e., the specific location being amended)
                    OPTIONAL { ?axiom j.2:reference_to_modified_location ?location }
                  }

                  # Get title from English expression
                  OPTIONAL {
                    ?expression cdm:expression_belongs_to_work ?amendment ;
                               cdm:expression_uses_language <http://publications.europa.eu/resource/authority/language/ENG> .
                    ?expression ?p ?title .
                    FILTER(CONTAINS(STR(?p), "title"))
                    FILTER(isLiteral(?title))
                  }

                  # Get document date
                  OPTIONAL { ?amendment cdm:work_date_document ?date_document }

                  # Get entry into force date
                  OPTIONAL { ?amendment cdm:resource_legal_date_entry-into-force ?date_entry_force }

                  # Get date of applicability
                  OPTIONAL { ?amendment cdm:resource_legal_date_applicability ?date_applicability }

                  # Get author
                  OPTIONAL { ?amendment cdm:work_created_by_agent ?author }
                }
                ORDER BY ?amendmentCelex
                """.formatted(celexNumber);
    }

    /**
     * Enhanced query for corrections with additional metadata
     */
    private static String createCorrectionsQuery(String celexNumber) {
        return """
                PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
                PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                
                SELECT DISTINCT ?correction ?correctionCelex ?title ?date_document 
                               ?date_entry_force ?comment
                WHERE {
                  # Retrieve the work by its CELEX number
                  ?work cdm:resource_legal_id_celex "%s"^^xsd:string .
                
                  # Find corrections to the work
                  ?correction cdm:resource_legal_corrects_resource_legal ?work ;
                              cdm:resource_legal_id_celex ?correctionCelex .
                  
                  # Get additional metadata about the correction
                  OPTIONAL {
                    # Get title from English expression
                    ?expression cdm:expression_belongs_to_work ?correction ;
                               cdm:expression_uses_language <http://publications.europa.eu/resource/authority/language/ENG> .
                    ?expression ?p ?title .
                    FILTER(CONTAINS(STR(?p), "title"))
                    FILTER(isLiteral(?title))
                  }
                  
                  # Get document date
                  OPTIONAL { ?correction cdm:work_date_document ?date_document }
                  
                  # Get entry into force date
                  OPTIONAL { ?correction cdm:resource_legal_date_entry-into-force ?date_entry_force }
                  
                  # Get any comments
                  OPTIONAL { ?correction cdm:resource_legal_comment ?comment }
                  
                  # Try getting comments from internal fields as well
                  OPTIONAL { ?correction cdm:resource_legal_comment_internal ?comment }
                }
                ORDER BY ?correctionCelex
                """.formatted(celexNumber);
    }

    /**
     * Retrieves comprehensive information about a legal resource with the given CELEX number.
     */
    public ResourceLegalData retrieveResourceLegal(String celexNumber) {
        return ResourceLegalDataBuilder.builder()
                .work(retrieveResourceLegalWork(celexNumber))
                .amendments(retrieveResourceLegalAmendments(celexNumber))
                .corrections(retrieveResourceLegalCorrections(celexNumber))
                .build();
    }

    /**
     * Retrieves basic information about the legal resource.
     */
    private ResourceLegal retrieveResourceLegalWork(String celexNumber) {
        String query = createResourceLegalQuery(celexNumber);
        System.out.println("retrieveResourceLegalWork.query = " + query);
        QueryExecutionHTTPBuilder queryBuilder = QueryExecution.service(SPARQL_ENDPOINT).query(query);
        try (QueryExecution qExec = queryBuilder.build()) {
            ResultSet resultSet = qExec.execSelect();

            if (resultSet.hasNext()) {
                QuerySolution solution = resultSet.next();

                return ResourceLegalBuilder.builder()
                        .title(solution.getLiteral("title").getString())
                        .workDateDocument(LocalDate.parse(solution.getLiteral("date_document").getString()))
                        .resourceLegalDateEntryIntoForce(LocalDate.parse(solution.getLiteral("date_entry_force").getString()))
                        .resourceLegalInForce(solution.getLiteral("in_force").getBoolean())
                        .type(URI.create(solution.getResource("type").getURI()))
                        .workCreatedByAgent(URI.create(solution.getResource("author").getURI()))
                        .resourceLegalEli(URI.create(solution.getLiteral("eli").getString()))
                        .resourceLegalIdCelex(solution.getLiteral("celex").getString())
                        .build();
            } else {
                throw new RuntimeException("No data found for CELEX: " + celexNumber);
            }
        }
    }

    /**
     * Enhanced method to retrieve and process amendments with additional metadata
     */
    private List<ResourceLegalAmendment> retrieveResourceLegalAmendments(String celexNumber) {
        Map<String, ResourceLegalAmendmentBuilder> amendmentBuilders = new HashMap<>();

        // First, get the basic amendment information
        processBasicAmendmentInfo(celexNumber, amendmentBuilders);


        // Build all amendments and return the list
        List<ResourceLegalAmendment> amendments = amendmentBuilders.values().stream()
                .map(ResourceLegalAmendmentBuilder::build)
                .collect(Collectors.toList());

        System.out.println("Total amendments found: " + amendments.size());
        return amendments;
    }

    /**
     * Process basic amendment information (title, dates, author)
     */
    private void processBasicAmendmentInfo(String celexNumber, Map<String, ResourceLegalAmendmentBuilder> amendmentBuilders) {
        String query = createAmendmentsQuery(celexNumber);
        System.out.println("processBasicAmendmentInfo.query = " + query);

        QueryExecutionHTTPBuilder queryBuilder = QueryExecution.service(SPARQL_ENDPOINT).query(query);
        try (QueryExecution qExec = queryBuilder.build()) {
            ResultSet resultSet = qExec.execSelect();

            while (resultSet.hasNext()) {
                QuerySolution solution = resultSet.next();

                String celex = solution.get("amendmentCelex").asLiteral().toString();
                RDFNode amendmentNode = solution.get("amendment");

                // Create or get a builder for this amendment
                ResourceLegalAmendmentBuilder builder = amendmentBuilders.computeIfAbsent(
                        celex,
                        key -> ResourceLegalAmendmentBuilder.builder()
                                .resourceLegalIdCelex(celex)
                                .amendmentUri(URI.create(amendmentNode.asResource().getURI()))
                );
                // Create a new builder for each amendment
                builder
//                        .amendmentUri(URI.create(solution.getResource("amendment").getURI()))
                        .title(solution.getLiteral("title").getString())
                        .workDateDocument(LocalDate.parse(solution.getLiteral("date_document").getString()))
                        .resourceLegalDateEntryIntoForce(LocalDate.parse(solution.getLiteral("date_entry_force").getString()))
                        .workCreatedByAgent(URI.create(solution.getResource("author").getURI()))
                        .amendmentType(URI.create((solution.getLiteral("amendment_type").getDatatypeURI())))
                        .subdivisionConcerned(solution.getLiteral("location").getString());
            }
        }
    }

    /**
     * Enhanced method to retrieve and process corrections with additional metadata
     */
    private List<ResourceLegalCorrection> retrieveResourceLegalCorrections(String celexNumber) {
        List<ResourceLegalCorrection> corrections = new ArrayList<>();

        String query = createCorrectionsQuery(celexNumber);
        System.out.println("retrieveResourceLegalCorrections.query = " + query);
        QueryExecutionHTTPBuilder queryBuilder = QueryExecution.service(SPARQL_ENDPOINT).query(query);
        try (QueryExecution qExec = queryBuilder.build()) {
            ResultSet resultSet = qExec.execSelect();

            while (resultSet.hasNext()) {
                QuerySolution solution = resultSet.next();

                // Create a new builder for each correction
                ResourceLegalCorrectionBuilder builder = ResourceLegalCorrectionBuilder.builder()
                        .correctionUri(URI.create(solution.get("correction").asResource().getURI()))
                        .resourceLegalIdCelex(solution.get("correctionCelex").asLiteral().getString())
                        .title(solution.getLiteral("title") == null ? null : solution.getLiteral("title").getString())
                        .workDateDocument(LocalDate.parse(solution.getLiteral("date_document").getString()))
                        .resourceLegalDateEntryIntoForce(solution.getLiteral("date_entry_force") == null ? null : LocalDate.parse(solution.getLiteral("date_entry_force").getString()))
                        .resourceLegalComment(solution.getLiteral("comment").getString());

                // Add the correction to the result list
                corrections.add(builder.build());
            }
        }

        return corrections;
    }
}