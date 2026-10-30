// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/**
 * The recitals of the preamble.
 *
 * @param intro The words before the first recital: "Whereas:".
 */
public record Recitals(List<Block> intro, List<Block> units) implements PreambleContent {

  public Recitals {
    intro = List.copyOf(intro);
    units = List.copyOf(units);
  }
}
