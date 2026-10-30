// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SparqlTermsTest {
  static final String CDM = "http://publications.europa.eu/ontology/cdm#";
  static final String TDM = "http://publications.europa.eu/ontology/tdm#";

  static final String QUERY =
      """
      # cdm:in_a_comment, <http://publications.europa.eu/ontology/cdm#also_in_a_comment>
      PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
      PREFIX tdm: <http://publications.europa.eu/ontology/tdm#>
      PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
      SELECT ?work WHERE {
        ?work cdm:resource_legal_id_celex "cdm:in_a_string {{celex}}"^^xsd:string ;
              cdm:work_has_resource-type ?type .
        ?expr <http://publications.europa.eu/ontology/cdm#expression_belongs_to_work> ?work ;
              cdm:expression_uses_language <http://publications.europa.eu/resource/authority/language/{{language}}> .
        ?item tdm:stream_name ?name .
        FILTER (?n < 3 && ?n >= 1)
        ?work cdm:work_date_document ?date.
      }
      """;

  @Test
  void findsTheTermsAQueryUsesAndOnlyThose() {
    var used = SparqlTerms.Usage.of(QUERY);

    assertThat(used.terms())
        .containsExactly(
            CDM + "expression_belongs_to_work",
            CDM + "expression_uses_language",
            CDM + "resource_legal_id_celex",
            CDM + "work_date_document",
            CDM + "work_has_resource-type",
            TDM + "stream_name",
            "http://publications.europa.eu/resource/authority/language/{{language}}",
            "http://www.w3.org/2001/XMLSchema#string");
    assertThat(used.undeclaredPrefixes()).isEmpty();
  }

  @Test
  void reportsTermsTheOntologyDoesNotDeclareOrDeprecates() {
    var terms =
        new SparqlTerms(
            Map.of(
                CDM + "resource_legal_id_celex",
                false,
                CDM + "work_date_document",
                true,
                "http://www.w3.org/2002/07/owl#sameAs",
                false),
            Set.of("http://publications.europa.eu/ontology/cdm"),
            Set.of(TDM + "stream_name"));

    assertThat(
            terms.problems(
                """
                PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
                PREFIX tdm: <http://publications.europa.eu/ontology/tdm#>
                PREFIX owl: <http://www.w3.org/2002/07/owl#>
                SELECT * WHERE {
                  ?w cdm:resource_legal_id_celex ?c ; cdm:work_date_document ?d ; cdm:no_such_term ?x .
                  ?i tdm:stream_name ?n ; tdm:stream_size ?s ; owl:sameAs ?same ; owl:other ?o .
                  ?i dc:title ?t .
                }
                """))
        .containsExactly(
            "dc: isn't a declared prefix",
            CDM + "no_such_term isn't declared",
            CDM + "work_date_document is deprecated",
            TDM + "stream_size isn't declared");
  }

  @Test
  void aNamespaceIsTheIriUpToItsLastHashOrSlash() {
    assertThat(SparqlTerms.namespace(CDM + "work")).isEqualTo(CDM);
    assertThat(SparqlTerms.namespace("http://publications.europa.eu/ontology/cdm/cmr#lang"))
        .isEqualTo("http://publications.europa.eu/ontology/cdm/cmr#");
  }
}
