// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A reference to another document.
 *
 * @param kind How the target is given.
 * @param href The target's URI, for kind uri.
 * @param series For kind oj: the OJ series.
 * @param number For kind oj: the issue or document number.
 * @param date For kind oj: the publication date.
 * @param page For kind oj: the first page.
 */
public record Ref(
    Kind kind,
    @Nullable String href,
    @Nullable String series,
    @Nullable String number,
    @Nullable LocalDate date,
    @Nullable String page,
    List<Inline> content)
    implements Inline {

  public Ref {
    content = List.copyOf(content);
  }

  /** How a reference gives its target. */
  public enum Kind {
    /** An Official Journal reference, as data (Formex REF.DOC.OJ); the renderer builds the link. */
    OJ,
    /** A URI, usually the ELI (Formex REF.DOC@URI, LINK; 06.x). */
    URI
  }
}
