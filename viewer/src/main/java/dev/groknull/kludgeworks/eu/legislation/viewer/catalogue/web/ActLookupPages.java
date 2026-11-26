// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NoSuchAct;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NotCatalogued;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ActLookupView;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.generated.precompiled.Templates;
import java.net.URI;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The answers of an act's page whose act the catalogue does not have yet. The controllers of the
 * act's pages answer {@link NotCatalogued} and {@link NoSuchAct} with these.
 *
 * <ul>
 *   <li>While the viewer looks the act up, the page waits for the lookup, at the address asked for
 *       ({@link ActLookupView}).
 *   <li>If no act has the address, the page says so, with 404.
 * </ul>
 */
final class ActLookupPages {

  private ActLookupPages() {}

  /**
   * Returns the redirect to {@code location}. The waiting page follows it too: htmx puts the
   * address that it got in the address bar.
   */
  static <T> ResponseEntity<T> seeOther(String location) {
    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(location)).build();
  }

  /** Returns the page that waits for the lookup of {@code notCatalogued}'s act. */
  static ResponseEntity<String> lookingUp(
      Templates templates, NotCatalogued notCatalogued, Frame frame) {
    var eli = Objects.requireNonNull(notCatalogued.eli());
    return ResponseEntity.ok()
        .contentType(Requests.HTML)
        .cacheControl(CacheControl.noStore())
        .body(
            templates
                .catalogueActlookup(
                    frame,
                    "Looking up " + eli.path() + " · Amendment viewer",
                    ActLookupView.of(eli, frame.here()))
                .render());
  }

  /** Returns the page that says that no act has the address, with 404. */
  static ResponseEntity<String> missing(Templates templates, NoSuchAct missing, Frame frame) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(Requests.HTML)
        .body(
            templates
                .catalogueNosuchact(frame, Objects.requireNonNull(missing.eli()).path())
                .render());
  }
}
