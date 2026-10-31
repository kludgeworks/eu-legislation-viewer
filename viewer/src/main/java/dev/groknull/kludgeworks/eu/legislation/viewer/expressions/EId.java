// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * An AKN4EU-style identifier: segments joined by "/", each an abbreviation plus the unit's own
 * label taken literally (brackets and trailing dots dropped, whitespace as "-", letters and digits
 * of any script kept), e.g. art_3/par_2/pnt_a, anx/pnt_1.2.2.6, pbl_1/rct_12. Units without a label
 * are numbered by position. The codes are AKN4EU's, which are ELI's subdivision codes, and where
 * ELI has none Akoma Ntoso's. Besides the units' own, segments name where numbering restarts:
 * tab_N/tr_N/td_N (units in a table cell), list_N (a unit's second and later lists), qstr_N (quoted
 * text), old and new (the sides of a correction), ann_N (an annotation), fgr_N (a figure's
 * description), def_N (an unlabelled definition).
 */
@ValueObject
public record EId(String value) {

  @Override
  public String toString() {
    return value;
  }
}
