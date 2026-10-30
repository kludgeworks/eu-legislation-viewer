// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * The terms SPARQL queries use, checked against the ontologies that declare them (the macro
 * sparql_terms_test): a term in an ontology's own namespaces (those under its owl:Ontology IRI:
 * cdm#, cdm/cmr#) must be declared there, and not deprecated. Other namespaces aren't checked, even
 * where the files declare a few of their terms (owl:, dc:, the authority tables); terms known to
 * exist that no ontology we can pin declares (tdm:) are allowed by name.
 */
public final class SparqlTerms {
  private static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
  private static final String OWL = "http://www.w3.org/2002/07/owl#";
  private static final Pattern PREFIX =
      Pattern.compile("(?i)\\bPREFIX\\s+([A-Za-z][\\w.-]*)?:\\s*<(\\d+)>");
  private static final Pattern BASE = Pattern.compile("(?i)\\bBASE\\s*<(\\d+)>");

  /** A prefixed name: ON_PREFIX, then a ON_LOCAL that may hold '.' and '-' but not end with '.'. */
  private static final Pattern PREFIXED =
      Pattern.compile("(?<![\\w?$:.-])((?:[A-Za-z](?:[\\w.-]*[\\w-])?)?):(\\w(?:[\\w.-]*[\\w-])?)");

  private final Map<String, Boolean> deprecated;
  private final Set<String> allowed;
  private final Set<String> namespaces = new HashSet<>();

  /**
   * @param deprecated every declared term, and whether it is deprecated
   * @param ontologies the ontologies' IRIs (owl:Ontology), whose namespaces are checked
   * @param allowed terms no ontology declares that are known to exist
   */
  SparqlTerms(Map<String, Boolean> deprecated, Set<String> ontologies, Set<String> allowed) {
    this.deprecated = Map.copyOf(deprecated);
    this.allowed = Set.copyOf(allowed);
    for (var term : deprecated.keySet()) {
      var namespace = namespace(term);
      if (ontologies.stream().anyMatch(namespace::startsWith)) {
        namespaces.add(namespace);
      }
    }
    allowed.forEach(term -> namespaces.add(namespace(term)));
  }

  /** The macro's ontologies, queries and allowed terms, passed as system properties. */
  static SparqlTerms fromProperties() throws IOException {
    var declared = new HashMap<String, Boolean>();
    var ontologies = new HashSet<String>();
    for (var ontology : paths("sparql-terms.ontologies")) {
      declared.putAll(declared(ontology, ontologies));
    }
    return new SparqlTerms(declared, ontologies, Set.copyOf(list("sparql-terms.allowed")));
  }

  static List<Path> queries() {
    return paths("sparql-terms.queries");
  }

  int declaredTerms() {
    return deprecated.size();
  }

  /** What is wrong with the terms {@code query} uses; empty when nothing is. */
  List<String> problems(String query) {
    var problems = new ArrayList<String>();
    var used = Usage.of(query);
    used.undeclaredPrefixes().forEach(prefix -> problems.add(prefix + ": isn't a declared prefix"));
    for (var term : used.terms()) {
      if (!namespaces.contains(namespace(term)) || allowed.contains(term)) {
        continue;
      }
      var isDeprecated = deprecated.get(term);
      if (isDeprecated == null) {
        problems.add(term + " isn't declared");
      } else if (isDeprecated) {
        problems.add(term + " is deprecated");
      }
    }
    return problems;
  }

  /**
   * The terms an ontology file declares (rdf:about on the elements under rdf:RDF), and whether each
   * is deprecated; the IRIs of the ontologies it declares (owl:Ontology) go into {@code
   * ontologies}.
   */
  static Map<String, Boolean> declared(Path ontology, Set<String> ontologies) throws IOException {
    var factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    var declared = new HashMap<String, Boolean>();
    try (InputStream in = Files.newInputStream(ontology)) {
      var xml = factory.createXMLStreamReader(in);
      String term = null;
      var depth = 0;
      while (xml.hasNext()) {
        switch (xml.next()) {
          case XMLStreamConstants.START_ELEMENT -> {
            depth++;
            if (depth == 2) {
              term = xml.getAttributeValue(RDF, "about");
              if (term != null
                  && OWL.equals(xml.getNamespaceURI())
                  && "Ontology".equals(xml.getLocalName())) {
                ontologies.add(term);
                term = null;
              } else if (term != null) {
                declared.putIfAbsent(term, false);
              }
            } else if (depth == 3 && term != null && isDeprecated(xml)) {
              declared.put(term, true);
            }
          }
          case XMLStreamConstants.END_ELEMENT -> depth--;
          default -> {}
        }
      }
    } catch (XMLStreamException e) {
      throw new IOException(ontology + ": not RDF/XML", e);
    }
    return declared;
  }

  private static boolean isDeprecated(XMLStreamReader xml) throws XMLStreamException {
    return OWL.equals(xml.getNamespaceURI())
        && "deprecated".equals(xml.getLocalName())
        && "true".equals(xml.getElementText().strip());
  }

  /** The namespace of a term: its IRI up to the last '#' or '/'. */
  static String namespace(String term) {
    return term.substring(0, Math.max(term.lastIndexOf('#'), term.lastIndexOf('/')) + 1);
  }

  private static List<String> list(String property) {
    var value = System.getProperty(property, "").strip();
    return value.isEmpty() ? List.of() : Arrays.asList(value.split("\\s+"));
  }

  private static List<Path> paths(String property) {
    return list(property).stream().map(Path::of).toList();
  }

  /**
   * The absolute IRIs a query names, prefixed names expanded and the PREFIX and BASE declarations'
   * own IRIs left out, and the prefixes it uses without declaring. Strings and comments name
   * nothing; the {@code {{placeholders}}} our queries are filled in by may stand inside IRIs and
   * strings.
   */
  record Usage(SortedSet<String> terms, SortedSet<String> undeclaredPrefixes) {

    static Usage of(String query) {
      var iris = new ArrayList<String>();
      var code = new StringBuilder();
      var i = 0;
      while (i < query.length()) {
        var c = query.charAt(i);
        if (c == '#') {
          while (i < query.length() && query.charAt(i) != '\n') {
            i++;
          }
          code.append(' ');
        } else if (c == '"' || c == '\'') {
          i = afterString(query, i);
          code.append(' ');
        } else if (c == '<' && iriEnd(query, i) > 0) {
          var end = iriEnd(query, i);
          code.append(" <").append(iris.size()).append("> ");
          iris.add(query.substring(i + 1, end));
          i = end + 1;
        } else {
          code.append(c);
          i++;
        }
      }

      var prefixes = new HashMap<String, String>();
      var declarations = new HashSet<Integer>();
      var declared = PREFIX.matcher(code);
      while (declared.find()) {
        var iri = Integer.parseInt(declared.group(2));
        declarations.add(iri);
        prefixes.put(declared.group(1) == null ? "" : declared.group(1), iris.get(iri));
      }
      var base = BASE.matcher(code);
      while (base.find()) {
        declarations.add(Integer.parseInt(base.group(1)));
      }

      var terms = new TreeSet<String>();
      for (var n = 0; n < iris.size(); n++) {
        if (!declarations.contains(n) && iris.get(n).contains(":")) {
          terms.add(iris.get(n));
        }
      }
      var undeclared = new TreeSet<String>();
      var names =
          PREFIXED.matcher(BASE.matcher(PREFIX.matcher(code).replaceAll(" ")).replaceAll(" "));
      while (names.find()) {
        var namespace = prefixes.get(names.group(1));
        if (namespace == null) {
          undeclared.add(names.group(1));
        } else {
          terms.add(namespace + names.group(2));
        }
      }
      return new Usage(terms, undeclared);
    }

    /** The index of the '>' ending an IRI that starts at {@code start}, or -1 if it's not one. */
    private static int iriEnd(String query, int start) {
      for (var i = start + 1; i < query.length(); i++) {
        var c = query.charAt(i);
        if (c == '>') {
          return i > start + 1 ? i : -1;
        }
        if (Character.isWhitespace(c) || "<\"|^`\\".indexOf(c) >= 0) {
          return -1;
        }
      }
      return -1;
    }

    /** The index after the string literal that starts at {@code start}: short or long, escapes. */
    private static int afterString(String query, int start) {
      var quote = query.charAt(start);
      var delimiter = query.startsWith(String.valueOf(quote).repeat(3), start) ? 3 : 1;
      var i = start + delimiter;
      while (i < query.length()) {
        if (query.charAt(i) == '\\') {
          i += 2;
        } else if (query.startsWith(String.valueOf(quote).repeat(delimiter), i)) {
          return i + delimiter;
        } else {
          i++;
        }
      }
      return i;
    }
  }
}
