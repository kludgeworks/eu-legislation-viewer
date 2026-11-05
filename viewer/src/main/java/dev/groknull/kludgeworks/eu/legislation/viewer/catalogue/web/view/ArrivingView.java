// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The versions of a prepared act that appeared since, being prepared (Design: Revisiting): a notice
 * on its pages, which stay as they are, listening to the act's preparation; once the versions are
 * ready, the page asks its address again and becomes itself with them, in place.
 *
 * @param notice what appeared: A new version has appeared: 02015R1998-20261201, applies from 1
 *     December 2026. It's being prepared; everything else is ready.
 * @param self the page's address, asked again once they are ready
 * @param events the stream of the act's preparation
 */
public record ArrivingView(String notice, String self, String events) {

  /** The notice on {@code act}'s page at {@code self}, if versions of it are arriving. */
  public static Optional<ArrivingView> of(Act act, Preparation preparation, String self) {
    var arriving = preparation.arriving();
    if (arriving.isEmpty()) {
      return Optional.empty();
    }
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var versions = arriving.stream().map(ArrivingView::version).collect(Collectors.joining("; "));
    return Optional.of(
        new ArrivingView(
            arriving.size() == 1
                ? "A new version has appeared: "
                    + versions
                    + ". It's being prepared; everything else is ready."
                : arriving.size()
                    + " new versions have appeared: "
                    + versions
                    + ". They're being prepared; everything else is ready.",
            self,
            Links.arriving(eli, preparation.language())));
  }

  /** 02015R1998-20261201, applies from 1 December 2026. */
  private static String version(Version version) {
    return version.celex() + ", applies from " + Wording.date(version.date());
  }
}
