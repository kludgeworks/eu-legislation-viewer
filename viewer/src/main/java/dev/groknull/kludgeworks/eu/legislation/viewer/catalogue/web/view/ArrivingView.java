// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The versions of a prepared act that appeared since, being prepared (Design: Revisiting, D-61): a
 * notice on its pages, which stay as they are while the reader reads; the versions are there the
 * next time the page loads.
 *
 * @param notice what appeared: A new version has appeared: 02015R1998-20261201, applies from 1
 *     December 2026. It's being prepared and will be here the next time you load this page.
 */
public record ArrivingView(String notice) {

  /** The notice on a page of the act being prepared in {@code preparation}, if any arrive. */
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

  /** 02015R1998-20261201, applies from 1 December 2026. */
  private static String version(Version version) {
    return version.celex() + ", applies from " + Wording.date(version.date());
  }
}
