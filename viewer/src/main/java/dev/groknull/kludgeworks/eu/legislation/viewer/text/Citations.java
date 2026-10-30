// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/**
 * The citations of the preamble.
 *
 * @param intro Words before the first citation (Formex GR.VISA.INIT).
 */
public record Citations(List<Block> intro, List<Block> units) implements PreambleContent {

  public Citations {
    intro = List.copyOf(intro);
    units = List.copyOf(units);
  }
}
