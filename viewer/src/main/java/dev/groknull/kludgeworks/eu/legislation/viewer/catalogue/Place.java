// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The subdivision of an act that an amendment modifies, named in words as EUR-Lex names it. For
 * example:
 *
 * <ul>
 *   <li>annex point 1.3.1.1 point (g)
 *   <li>article 114 paragraph 1 unnumbered paragraph 1
 *   <li>annex chapter 5 APP 5-A, where an older annotation of Cellar does not code the appendix
 * </ul>
 *
 * <p>Its {@link Location} finds it among the occurrences of a text. The rule {@link
 * Places#aPageNamesAPlaceAsEurLexDoes} gives the name.
 *
 * @param words the name of the place
 */
@ValueObject
public record Place(String words) {

  /** {@inheritDoc} It is the name of the place. */
  @Override
  public String toString() {
    return words;
  }
}
