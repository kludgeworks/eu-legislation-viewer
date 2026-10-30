// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** The citations ("Having regard to …"): an introduction, then the citation units. */
public record Citations(List<Block> intro, List<Block> units) implements PreambleContent {

  public Citations {
    intro = List.copyOf(intro);
    units = List.copyOf(units);
  }
}
