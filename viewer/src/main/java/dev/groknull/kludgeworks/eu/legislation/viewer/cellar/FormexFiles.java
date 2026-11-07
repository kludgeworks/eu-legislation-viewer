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
 * Finds the Formex files of a version in Cellar's RDF. The path is work → expression →
 * manifestation → items. The work has a CELEX number, the expression is a language, and the
 * manifestation is {@code fmx4}. Cellar uses two layouts:
 *
 * <ul>
 *   <li>One item per file. Older OJ acts use this layout. Each item is a file. Its name is the
 *       {@code tdm:stream_name}. If the technical metadata is missing, its name is the suffix of
 *       its {@code owl:sameAs} after {@code ".fmx4."}.
 *   <li>One zip item. OJ acts and consolidations from 2023 use this layout. The RDF says nothing
 *       about the entries of the zip, so this class reads them from Cellar's content-stream list.
 *       It fetches each entry separately at {@code …/DOC_1/{entry}}.
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
   * Returns the Formex files of the version that {@code celex} names, in {@code language}. The
   * version is an OJ act, for example 32015R1998, or a consolidated version, for example
   * 02015R1998-20260322. The language is an EU authority code, for example ENG. If Cellar has more
   * than one Formex manifestation, it returns the one modified last.
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
   * Reads the entries of a zip item from the content-stream list. It gets the list from the CELEX
   * URI with {@code Accept: application/list;mtype=fmx4}. The list is an XHTML page that links each
   * entry at {@code {zip item}/{entry name}}. The CELEX URI encodes the slash in a treaty's CELEX
   * number, as Cellar's {@code owl:sameAs} does. For example, {@code celex/02016E%2FTXT-20250315}.
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

  /**
   * Returns the media type of a zip entry from its name. Cellar labels every entry {@code
   * application/zip}, so the label does not help.
   */
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
