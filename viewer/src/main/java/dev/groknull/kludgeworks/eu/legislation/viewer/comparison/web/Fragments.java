// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActAddress;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Ends;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.DifferVersion;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
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
  private final Acts acts;
  private final Texts texts;
  private final Release release;
  private final LegalRenderer html;

  Fragments(Acts acts, Texts texts, Release release, LegalRenderer html) {
    this.acts = acts;
    this.texts = texts;
    this.release = release;
    this.html = html;
  }

  /** Returns the ends of the comparison at a canonical address of the compare page. */
  Ends ends(ActAddress address, LocalDate from, LocalDate to, @Nullable String only) {
    var act = acts.find(address.eli()).orElseThrow(Requests::notFound);
    var preparation = texts.preparation(act, address.language());
    return Ends.of(act, address.language(), from, to, only, preparation, texts.tags(preparation))
        .orElseThrow(Requests::notFound);
  }

  /**
   * Returns the two texts of a fragment of the page of an amending act, or of a comparison: the
   * text before {@code ends.only()} and the first text with it, or the texts of the two ends.
   * Throws 404 if either text is not built.
   */
  static ReadyTexts ready(Ends ends, Preparation preparation) {
    var earlier = ends.earlier();
    var later = ends.later();
    if (!(preparation.texts().get(earlier) instanceof Text.Ready before)
        || !(preparation.texts().get(later) instanceof Text.Ready after)) {
      throw Requests.notFound();
    }
    return new ReadyTexts(earlier, later, before.text(), after.text());
  }

  /**
   * Returns whether the browser has the fragment for {@code texts} already. Then the answer is "not
   * modified", with no body.
   */
  boolean notModified(HttpServletRequest request, HttpServletResponse response, ReadyTexts texts) {
    return Release.notModified(
        request,
        response,
        Release.REVALIDATED,
        release.etag(texts.before(), texts.after(), DifferVersion.value()));
  }

  /**
   * The two built texts of a fragment.
   *
   * @param earlier the version of the earlier text
   * @param later the version of the later text
   * @param before the earlier text
   * @param after the later text
   */
  record ReadyTexts(Version earlier, Version later, TextId before, TextId after) {}

  /** Returns {@code element} rendered as the legal text renders it. */
  String render(Element element) {
    var out = new StringOutput();
    html.element(element).writeTo(out);
    return out.toString();
  }
}
