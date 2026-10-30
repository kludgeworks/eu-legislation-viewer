// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Every term the queries use is declared, and not deprecated, in the ontologies ({@link
 * SparqlTerms}): one test per query file. The macro passes the ontologies, the queries and the
 * allowed terms as system properties.
 */
public class SparqlTermsTests {

  @TestFactory
  Stream<DynamicTest> queriesUseOnlyDeclaredTerms() throws IOException {
    var terms = SparqlTerms.fromProperties();
    assertTrue(terms.declaredTerms() > 0, "no terms in the ontologies");
    assertFalse(SparqlTerms.queries().isEmpty(), "no queries");
    return SparqlTerms.queries().stream()
        .map(
            query ->
                DynamicTest.dynamicTest(
                    query.getFileName().toString(),
                    () ->
                        assertEquals(
                            List.of(),
                            terms.problems(Files.readString(query, StandardCharsets.UTF_8)),
                            query + ": terms the ontologies don't declare, or deprecate")));
  }
}
