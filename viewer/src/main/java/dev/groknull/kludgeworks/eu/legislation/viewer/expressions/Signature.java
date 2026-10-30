// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;

/**
 * Place, date and signatories.
 *
 * @param placeAndDate "Done at Brussels, 5 November 2015.": the date is typed, the place stays text
 *     (Formex does not mark it, and recognising it would mean parsing the wording of 24 languages).
 * @param signatories One signatory; acts of Parliament and Council have two.
 */
public record Signature(List<Inline> placeAndDate, List<Signatory> signatories) {

  public Signature {
    placeAndDate = List.copyOf(placeAndDate);
    signatories = List.copyOf(signatories);
  }
}
