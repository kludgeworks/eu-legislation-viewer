// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** A signature: the place and date line, and the signatories. */
public record Signature(List<Inline> placeAndDate, List<Signatory> signatories) {

  public Signature {
    placeAndDate = List.copyOf(placeAndDate);
    signatories = List.copyOf(signatories);
  }
}
