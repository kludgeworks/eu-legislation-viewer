// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.web;

import static java.nio.charset.StandardCharsets.UTF_8;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.web.ActLinks;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.web.TimelineLinks;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expression;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.stereotype.Component;

/**
 * A version's text in a language as HAL (URLs and API: public API): the legal text as the
 * resource's own state (LegalTextJsonModule), with its links: up to the version; the versions
 * either side, the first and the latest among those in the same language (Timeline.in,
 * TimelineLinks), a version without it passed over; what changed since the one before (av:changes,
 * planned: 501); and as alternates the version in each other language it is in (hreflang), and the
 * text on EUR-Lex and the expression in Cellar, its Formex among its manifestations.
 */
@Component
class TextAssembler {
  private final URI cellarResources;

  /**
   * @param cellarResources where Cellar's resource URIs start (viewer.cellar.resources)
   */
  TextAssembler(@Value("${viewer.cellar.resources}") URI cellarResources) {
    this.cellarResources = cellarResources;
  }

  /**
   * The entity tag of each text of {@code act}, made with the converter {@code converterVersion}: a
   * weak one, as the HAL and the plain JSON are tagged alike, over all a text and its links are
   * made from: the converter's version and the act's timeline, each version's document, date,
   * languages and when its Formex was modified. So it changes whenever a text of the act might,
   * without the text being read.
   */
  static String etag(Act act, String converterVersion) {
    var made = new StringBuilder(converterVersion);
    for (var version : act.timeline().versions()) {
      made.append('\n')
          .append(version.celex())
          .append(' ')
          .append(version.date())
          .append(' ')
          .append(version.formexModified())
          .append(' ')
          .append(version.languages());
    }
    try {
      var digest = MessageDigest.getInstance("SHA-256").digest(made.toString().getBytes(UTF_8));
      return "W/\"" + HexFormat.of().formatHex(digest, 0, 16) + "\"";
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("every JVM has SHA-256", e);
    }
  }

  /**
   * {@code expression}, the text of {@code version} of {@code act} in {@code language}.
   *
   * @throws java.util.NoSuchElementException if {@code version} isn't in {@code language}
   */
  RepresentationModel<?> toModel(
      Act act, Version version, Language language, Expression expression) {
    var id = act.id();
    var date = version.date();
    var inLanguage = act.timeline().in(language).orElseThrow();
    var links = new ArrayList<Link>();
    links.add(ActLinks.text(id, date, language, IanaLinkRelations.SELF));
    links.add(ActLinks.version(id, date, IanaLinkRelations.UP));
    links.addAll(TimelineLinks.around(inLanguage, version, ActLinks.toText(id, language)));
    inLanguage
        .predecessor(version)
        .ifPresent(
            v ->
                links.add(
                    ActLinks.comparisonText(
                        id, v.date(), date, language, VersionAssembler.CHANGES)));
    for (var other : version.languages()) {
      if (other != language) {
        links.add(
            ActLinks.text(id, date, other, IanaLinkRelations.ALTERNATE).withHreflang(other.iso()));
      }
    }
    links.add(ActLinks.eurLex(version.celex(), language, "Read on EUR-Lex"));
    links.add(
        ActLinks.cellar(
            cellarResources,
            version.celex() + "." + language.code(),
            "The expression in Cellar, its Formex among its manifestations"));
    return HalModelBuilder.halModelOf(expression).links(links).build();
  }
}
