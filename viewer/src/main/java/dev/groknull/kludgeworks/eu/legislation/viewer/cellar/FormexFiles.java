// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The Formex files of a version, found from Cellar's RDF: work (CELEX) → expression (language) →
 * manifestation (fmx4) → items. Two layouts:
 *
 * <ul>
 *   <li>one item per file (older OJ acts): each item is a file, named by tdm:stream_name or, where
 *       the technical metadata is missing, by the suffix of its owl:sameAs after ".fmx4.";
 *   <li>one zip item (OJ acts and consolidations from 2023): the RDF says nothing about its
 *       entries, so they come from Cellar's content-stream list, and each is fetched on its own at
 *       {@code …/DOC_1/{entry}}.
 * </ul>
 */
@Component
public class FormexFiles {
  private static final Logger LOG = LoggerFactory.getLogger(FormexFiles.class);
  private static final Pattern CELEX = Pattern.compile("\\S+");
  private static final Pattern LANGUAGE = Pattern.compile("[A-Z]{3}");

  private static final String QUERY = Sparql.template(FormexFiles.class, "formex-files.rq");

  private final Sparql sparql;
  private final RestClient client;
  private final CellarProperties properties;

  FormexFiles(
      Sparql sparql,
      @Qualifier(CellarConfiguration.CLIENT) RestClient client,
      CellarProperties properties) {
    this.sparql = sparql;
    this.client = client;
    this.properties = properties;
  }

  /**
   * The Formex files of the version {@code celex} names (an OJ act, 32015R1998, or a consolidated
   * version, 02015R1998-20260322) in {@code language} (an EU authority code: ENG). Where Cellar has
   * more than one Formex manifestation, the latest modified.
   */
  public Optional<FormexManifestation> manifestation(String celex, String language) {
    if (!CELEX.matcher(celex).matches() || !LANGUAGE.matcher(language).matches()) {
      throw new IllegalArgumentException(
          "Not a CELEX number and language code: " + celex + ", " + language);
    }
    var rows =
        sparql.select(Sparql.fill(QUERY, Map.of("celex", celex, "language", language))).rows();
    var latest = rows.stream().max(Comparator.comparing(r -> modified(r.get("modified"))));
    if (latest.isEmpty()) {
      LOG.debug("No Formex manifestation of {} in {}", celex, language);
      return Optional.empty();
    }
    var uri = latest.get().get("manifestation");
    var items = rows.stream().filter(r -> r.get("manifestation").equals(uri)).toList();
    if (items.size() == 1 && items.getFirst().get("mime").startsWith("application/zip")) {
      var zip = URI.create(items.getFirst().get("item"));
      var entries = entries(celex, language, zip);
      LOG.debug(
          "Formex of {} in {}: {} entries of the zip {}", celex, language, entries.size(), zip);
      return Optional.of(new FormexManifestation(URI.create(uri), entries));
    }
    var files =
        items.stream()
            .map(
                r ->
                    new FormexManifestation.File(
                        r.find("file")
                            .orElseThrow(
                                () -> new CellarException("No file name for " + r.get("item"))),
                        URI.create(r.get("item")),
                        r.get("mime")))
            .toList();
    LOG.debug("Formex of {} in {}: {} items of {}", celex, language, files.size(), uri);
    return Optional.of(new FormexManifestation(URI.create(uri), files));
  }

  /**
   * A zip item's entries, from the content-stream list (Accept: application/list;mtype=fmx4 on the
   * CELEX URI): an XHTML page linking each entry at {@code {zip item}/{entry name}}. The CELEX URI
   * writes a treaty's slash encoded, as Cellar's owl:sameAs does: celex/02016E%2FTXT-20250315.
   */
  private List<FormexManifestation.File> entries(String celex, String language, URI zip) {
    var page =
        client
            .get()
            .uri(properties.resources().resolve("celex/" + celex.replace("/", "%2F")))
            .accept(MediaType.parseMediaType("application/list;mtype=fmx4"))
            .header("Accept-Language", language.toLowerCase(Locale.ROOT))
            .retrieve()
            .body(byte[].class);
    if (page == null) {
      throw new CellarException("Empty content-stream list for " + celex);
    }
    return entries(page, zip);
  }

  static List<FormexManifestation.File> entries(byte[] page, URI zip) {
    var prefix = zip + "/";
    var files = new ArrayList<FormexManifestation.File>();
    try {
      var factory = XMLInputFactory.newFactory();
      factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
      var reader = factory.createXMLStreamReader(new ByteArrayInputStream(page));
      while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.START_ELEMENT
            && reader.getLocalName().equals("a")) {
          var href = reader.getAttributeValue(null, "href");
          if (href != null && href.startsWith(prefix)) {
            var name = URLDecoder.decode(href.substring(prefix.length()), StandardCharsets.UTF_8);
            files.add(new FormexManifestation.File(name, URI.create(href), mediaType(name)));
          }
        }
      }
    } catch (XMLStreamException e) {
      throw new CellarException("Unreadable content-stream list for " + zip, e);
    }
    return files;
  }

  /** A zip entry's type, from its name: Cellar labels every entry application/zip. */
  private static String mediaType(String name) {
    var lower = name.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".xml")) {
      return "application/xml;type=fmx4";
    }
    if (lower.endsWith(".tif") || lower.endsWith(".tiff")) {
      return "image/tiff";
    }
    return "application/octet-stream";
  }

  private static Instant modified(String value) {
    return OffsetDateTime.parse(value).toInstant();
  }
}
