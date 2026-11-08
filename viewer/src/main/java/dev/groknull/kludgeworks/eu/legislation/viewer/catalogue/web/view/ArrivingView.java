// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.chrome.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A notice about new versions of a prepared act. These versions appeared after the act was
 * prepared, and they are being prepared now. The notice shows on the pages of the act. The pages do
 * not change while the reader reads them. The new versions are there the next time the page loads.
 *
 * @param notice the text that says what appeared. For example: "A new version has appeared:
 *     02015R1998-20261201, applies from 1 December 2026. It's being prepared and will be here the
 *     next time you load this page."
 */
public record ArrivingView(String notice) {

  /**
   * Returns the notice for a page of the act that {@code preparation} prepares. Returns empty if no
   * new versions arrive.
   */
  public static Optional<ArrivingView> of(Preparation preparation) {
    var arriving = preparation.arriving();
    if (arriving.isEmpty()) {
      return Optional.empty();
    }
    var versions = arriving.stream().map(ArrivingView::version).collect(Collectors.joining("; "));
    return Optional.of(
        new ArrivingView(
            arriving.size() == 1
                ? "A new version has appeared: "
                    + versions
                    + ". It's being prepared and will be here the next time you load this page."
                : arriving.size()
                    + " new versions have appeared: "
                    + versions
                    + ". They're being prepared and will be here the next time you load this"
                    + " page."));
  }

  /**
   * Returns the CELEX of the version and the date it applies from. For example,
   * "02015R1998-20261201, applies from 1 December 2026".
   */
  private static String version(Version version) {
    return version.celex() + ", applies from " + Dates.inWords(version.date());
  }
}
