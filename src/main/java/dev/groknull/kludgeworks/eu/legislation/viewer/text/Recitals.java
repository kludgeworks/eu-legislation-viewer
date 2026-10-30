// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** The recitals ("Whereas:"): an introduction, then the recital units and groups. */
public record Recitals(List<Block> intro, List<Block> units) implements PreambleContent {

  public Recitals {
    intro = List.copyOf(intro);
    units = List.copyOf(units);
  }
}
