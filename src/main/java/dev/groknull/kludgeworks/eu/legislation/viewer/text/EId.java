// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

/**
 * An AKN4EU-style identifier: segments joined by "/", each an abbreviation plus the unit's own
 * label taken literally (brackets and trailing dots dropped, whitespace as "-", letters and digits
 * of any script kept), e.g. art_3/par_2/pnt_a, anx/pnt_1.2.2.6, pbl_1/rct_12. Units without a label
 * are numbered by position.
 */
public record EId(String value) {

  @Override
  public String toString() {
    return value;
  }
}
