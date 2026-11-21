// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * An act's page while the viewer looks the act up at the Publications Office, because the catalogue
 * does not have it yet. The page keeps the address that the reader asked for. When the lookup ends,
 * the page becomes the page of that address, or says that no act has it, or that the Publications
 * Office did not answer.
 *
 * @param line what the page says while it waits. For example, "Looking up reg/2005/396 at the
 *     Publications Office…".
 * @param eli the ELI of the act, as the page names it. For example, reg/2005/396.
 * @param self the address of the page, which it asks for again when the act is found
 * @param events the stream that says when the lookup ends
 * @param unanswered what can help if the Publications Office does not answer
 */
public record ActLookupView(
    String line, String eli, String self, String events, HomeView.Unanswered unanswered) {

  /** Returns the page of {@code self}, an address of the act that {@code eli} names. */
  public static ActLookupView of(Eli eli, String self) {
    return new ActLookupView(
        "Looking up " + eli.path() + " at the Publications Office…",
        eli.path(),
        self,
        Links.actLookup(eli) + "?page=" + URLEncoder.encode(self, StandardCharsets.UTF_8),
        new HomeView.Unanswered(self, eli.eurLex().toString()));
  }
}
