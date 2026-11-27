// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActAddress;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.ComparedTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Ends;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.DifferVersion;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Release;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.output.StringOutput;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * What the fragments of the compare page share: the ends of a comparison from its canonical
 * address, its two built texts, their entity tag, and the rendering of an element of the legal
 * text. {@link ComparisonViewsController} and {@link DifferenceController} use it.
 */
final class Fragments {
  private final ComparedTexts compared;
  private final Release release;
  private final LegalRenderer html;

  Fragments(ComparedTexts compared, Release release, LegalRenderer html) {
    this.compared = compared;
    this.release = release;
    this.html = html;
  }

  /** Returns the ends of the comparison at a canonical address of the compare page. */
  Ends ends(ActAddress address, LocalDate from, LocalDate to, @Nullable String only) {
    return compared
        .ends(address.eli(), address.language(), from, to, only)
        .orElseThrow(Requests::notFound);
  }

  /**
   * Returns the two texts of a fragment of the page of an amending act, or of a comparison, once
   * both are built ({@link Ends#texts}). Throws 404 if either text is not built.
   */
  static Ends.Built ready(Ends ends) {
    if (ends.texts() instanceof Ends.Built built) {
      return built;
    }
    throw Requests.notFound();
  }

  /**
   * Returns whether the browser has the fragment for {@code texts} already. Then the answer is "not
   * modified", with no body.
   */
  boolean notModified(HttpServletRequest request, HttpServletResponse response, Ends.Built texts) {
    return Release.notModified(
        request,
        response,
        Release.REVALIDATED,
        release.etag(texts.before(), texts.after(), DifferVersion.value()));
  }

  /** Returns a renderer bound to the two ends of {@code ends}, or {@link #html} if not built. */
  LegalRenderer renderer(Ends ends) {
    return ends.texts() instanceof Ends.Built built ? renderer(built) : html;
  }

  /** Returns a renderer bound to {@code texts} so deleted and current images resolve. */
  LegalRenderer renderer(Ends.Built texts) {
    return html.forComparison(texts.before(), texts.after());
  }

  /** Returns {@code element} rendered as the legal text renders it. */
  String render(Element element, Ends.Built texts, boolean deleted) {
    var out = new StringOutput();
    var renderer = renderer(texts);
    (deleted ? renderer.deleted() : renderer).element(element).writeTo(out);
    return out.toString();
  }
}
