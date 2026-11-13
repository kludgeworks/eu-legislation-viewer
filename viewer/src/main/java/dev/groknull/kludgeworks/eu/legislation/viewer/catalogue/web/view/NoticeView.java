// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Dates;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * A notice that the act of an open page changed while the reader reads it. The server pushes it to
 * the page. The page does not change by itself: the notice says what changed, and the reader loads
 * the page again to see it.
 *
 * @param notice what changed. For example, "A newer version exists: it applies from 1 December
 *     2026. Load this page again to see it."
 */
public record NoticeView(String notice) {

  /** Returns the notice that a version that applies from {@code date} appeared. */
  public static NoticeView newerVersion(LocalDate date) {
    return new NoticeView(
        "A newer version exists: it applies from "
            + Dates.inWords(date)
            + ". Load this page again to see it.");
  }

  /**
   * Returns the notice that the text of a version on the page was replaced. {@code republished} is
   * when the Publications Office last published the version's Formex in the language, if the
   * catalogue knows it.
   */
  public static NoticeView replaced(@Nullable LocalDate republished) {
    return new NoticeView(
        republished == null
            ? "This text was prepared again. Load this page again to read the new text."
            : "This text was republished by the Publications Office on "
                + Dates.inWords(republished)
                + ". Load this page again to read the new text.");
  }
}
