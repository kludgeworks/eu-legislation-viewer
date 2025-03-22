package com.kludgeworks.eu.legislation.cli.domain;

import java.util.ArrayList;
import java.util.List;

import com.kludgeworks.eu.legislation.cli.domain.mapper.SparqlResultMapper;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LegislationService {

    private static final String SPARQL_ENDPOINT = "https://publications.europa.eu/webapi/rdf/sparql";
    private static final Logger logger = LoggerFactory.getLogger( LegislationService.class);

    private final SparqlResultMapper mapper;

    @Autowired
    public LegislationService(SparqlResultMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Find the English expression for a given CELEX number
     *
     * @param celexNumber The CELEX number
     * @return The URI of the English expression, or null if not found
     */
    public String findEnglishExpression(String celexNumber) {
        String queryString = String.format("""
            PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            
            SELECT DISTINCT ?expression ?language
            WHERE {
              # Retrieve the work by its CELEX number
              ?work cdm:resource_legal_id_celex "%s"^^xsd:string .
            
              # Find all expressions that belong to this work
              ?expression cdm:expression_belongs_to_work ?work ;
                          cdm:expression_uses_language ?language .
            }""", celexNumber);

        try (QueryExecution qexec = QueryExecution.service(SPARQL_ENDPOINT).query(queryString).build()) {
            ResultSet results = qexec.execSelect();

            while (results.hasNext()) {
                QuerySolution solution = results.nextSolution();
                String language = mapper.getResourceUri(solution, "language");

                if (language != null && language.endsWith("/ENG")) {
                    return mapper.getResourceUri(solution, "expression");
                }
            }
        } catch (Exception e) {
            logger.error("Error finding English expression for CELEX {}: {}", celexNumber, e.getMessage());
        }

        return null;
    }

    /**
     * Get all manifestations (fmx4 and xhtml) for a given expression
     *
     * @param expressionUri The URI of the expression
     * @return A list of Manifestation objects
     */
    public List<Manifestation> getManifestations(String expressionUri) {
        String queryString = String.format("""
           PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>

           SELECT DISTINCT ?manifestation ?type
           WHERE {
             # Find all manifestations of the specified expression
             ?manifestation cdm:manifestation_manifests_expression <http://publications.europa.eu/resource/cellar/c38f361d-8a99-11e5-b8b7-01aa75ed71a1.0006> .
             ?manifestation cdm:manifestation_type ?type
           }""", expressionUri);

        List<Manifestation> manifestations = new ArrayList<>();

        try (QueryExecution qexec = QueryExecution.service(SPARQL_ENDPOINT).query(queryString).build()) {
            ResultSet results = qexec.execSelect();

            while (results.hasNext()) {
                QuerySolution solution = results.nextSolution();
                Manifestation manifestation = mapper.toManifestation(solution);

                // Only include fmx4 and xhtml manifestations
                if (manifestation != null &&
                    ("fmx4".equals(manifestation.type()) || "xhtml".equals(manifestation.type()))) {
                    manifestations.add(manifestation);
                }
            }
        } catch (Exception e) {
            logger.error("Error getting manifestations for expression {}: {}", expressionUri, e.getMessage());
        }


        return manifestations;
    }

    /**
     * Get content items for a given manifestation with direct MIME type querying
     *
     * @param manifestation The manifestation object
     * @return A list of ContentItem objects
     */
    public List<ContentItem> getContentItems(Manifestation manifestation) {
        // This query directly asks for the MIME type, making processing easier
        String queryString = String.format("""
        PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
        PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>
        
        SELECT DISTINCT ?item ?identifier ?mimeType
        WHERE {
          # Find all items of the specified manifestation
          ?item cdm:item_belongs_to_manifestation <%s> .

          # Get the item's identifier if available
          OPTIONAL { ?item cdm:item_identifier ?identifier }

          # Get MIME type information - required field
          ?item cmr:manifestationMimeType ?mimeType
        }""", manifestation.uri());

        List<ContentItem> contentItems = new ArrayList<>();

        try (QueryExecution qexec = QueryExecution.service(SPARQL_ENDPOINT).query(queryString).build()) {
            ResultSet results = qexec.execSelect();

            while (results.hasNext()) {
                QuerySolution solution = results.nextSolution();

                // Use the mapper to convert the solution to a ContentItem
                ContentItem item = mapper.toContentItem(solution, manifestation.type());

                if (item != null && item.uri() != null) {
                    contentItems.add(item);
                    logger.debug("Added content item: URI={}, ID={}, MIME={}",
                        item.uri(), item.identifier(), item.mimeType());
                }
            }

            if (contentItems.isEmpty()) {
                logger.warn("No content items found for manifestation {}", manifestation.uri());
            } else {
                logger.info("Found {} content items for manifestation {}",
                    contentItems.size(), manifestation.uri());
            }
        } catch (Exception e) {
            logger.error("Error getting content items for manifestation {}: {}",
                manifestation.uri(), e.getMessage());
        }

        return contentItems;
    }

    /**
     * Get amendments for a given CELEX number
     *
     * @param celexNumber The CELEX number
     * @return A list of Amendment objects
     */
    public List<Amendment> getAmendments(String celexNumber) {
        String queryString = String.format("""
            PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            
            SELECT DISTINCT ?amendment ?amendmentCelex ?title ?date
            WHERE {
              # Retrieve the original regulation by its CELEX number
              ?regulation cdm:resource_legal_id_celex "%s"^^xsd:string .
            
              # Find all amendments to this regulation
              ?amendment cdm:resource_legal_amends_resource_legal ?regulation .
            
              # Get CELEX number of the amendment
              ?amendment cdm:resource_legal_id_celex ?amendmentCelex .
            
              # Get optional title and date metadata
              OPTIONAL {
                ?expression cdm:expression_belongs_to_work ?amendment ;
                            cdm:expression_uses_language <http://publications.europa.eu/resource/authority/language/ENG> ;
                            cdm:expression_title ?title .
              }
              OPTIONAL { ?amendment cdm:work_date_document ?date }
            }
            ORDER BY ?date""", celexNumber);

        List<Amendment> amendments = new ArrayList<>();

        try (QueryExecution qexec = QueryExecution.service(SPARQL_ENDPOINT).query(queryString).build()) {
            ResultSet results = qexec.execSelect();

            while (results.hasNext()) {
                QuerySolution solution = results.nextSolution();
                Amendment amendment = mapper.toAmendment(solution);

                if (amendment != null && amendment.celexNumber() != null) {
                    amendments.add(amendment);
                }
            }
        } catch (Exception e) {
            logger.error("Error getting amendments for CELEX {}: {}", celexNumber, e.getMessage());
        }

        return amendments;
    }

    /**
     * Get consolidations for a given amendment CELEX number
     *
     * @param amendmentCelex The amendment CELEX number
     * @return A list of Consolidation objects
     */
    public List<Consolidation> getConsolidations(String amendmentCelex) {
        String queryString = String.format("""
            PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            
            SELECT ?consolidation ?consDate ?celexId ?eli
            WHERE {
              # Get the amendment regulation by its CELEX number
              ?amendment cdm:resource_legal_id_celex "%s"^^xsd:string .
            
              # Find consolidated versions that incorporate this amendment
              ?consolidation cdm:act_consolidated_consolidates_resource_legal ?amendment ;
                            cdm:act_consolidated_date ?consDate ;
                            cdm:resource_legal_id_celex ?celexId .
            
              # Optional ELI identifier
              OPTIONAL { ?consolidation cdm:resource_legal_eli ?eli }
            }
            ORDER BY ?consDate""", amendmentCelex);

        List<Consolidation> consolidations = new ArrayList<>();

        try (QueryExecution qexec = QueryExecution.service(SPARQL_ENDPOINT).query(queryString).build()) {
            ResultSet results = qexec.execSelect();

            while (results.hasNext()) {
                QuerySolution solution = results.nextSolution();
                Consolidation consolidation = mapper.toConsolidation(solution);

                if (consolidation != null && consolidation.celexNumber() != null) {
                    consolidations.add(consolidation);
                }
            }
        } catch (Exception e) {
            logger.error("Error getting consolidations for amendment CELEX {}: {}", amendmentCelex, e.getMessage());
        }


        return consolidations;
    }
}