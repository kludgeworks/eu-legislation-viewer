// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NoSuchAct;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NotCatalogued;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ActLookupView;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletRequest;
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

  /**
   * The header of the request that the waiting page sends when the act is found. An address that
   * redirects answers it with {@code HX-Redirect}, so that the browser goes to the address that it
   * redirects to: htmx follows a redirect, but keeps the address that it asked for.
   */
  static final String FOUND = "X-Act-Found";

  private ActLookupPages() {}

  /**
   * Returns the redirect to {@code location}. For the request of the waiting page when the act is
   * found ({@link #FOUND}), it is {@code HX-Redirect}.
   */
  static <T> ResponseEntity<T> seeOther(String location, HttpServletRequest request) {
    return request.getHeader(FOUND) != null
        ? ResponseEntity.ok().header("HX-Redirect", location).build()
        : ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(location)).build();
  }

  /** Returns the page that waits for the lookup of {@code notCatalogued}'s act. */
  static ResponseEntity<String> lookingUp(
      Templates templates, NotCatalogued notCatalogued, HttpServletRequest request) {
    var eli = Objects.requireNonNull(notCatalogued.eli());
    var query = request.getQueryString();
    var self = request.getRequestURI() + (query == null ? "" : "?" + query);
    return ResponseEntity.ok()
        .contentType(Requests.HTML)
        .cacheControl(CacheControl.noStore())
        .body(
            templates
                .catalogueActlookup(
                    Frame.of(request),
                    "Looking up " + eli.path() + " · Amendment viewer",
                    ActLookupView.of(eli, self))
                .render());
  }

  /** Returns the page that says that no act has the address, with 404. */
  static ResponseEntity<String> missing(
      Templates templates, NoSuchAct missing, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(Requests.HTML)
        .body(
            templates
                .catalogueNosuchact(Frame.of(request), Objects.requireNonNull(missing.eli()).path())
                .render());
  }
}
