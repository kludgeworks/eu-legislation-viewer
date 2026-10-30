// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** A formula of the preamble: the acting entity, or the enacting formula. */
public record Formula(Name name, List<Block> content) implements PreambleContent {

  public Formula {
    content = List.copyOf(content);
  }

  /** Which formula. */
  public enum Name {
    ACTING_ENTITY,
    ENACTING_FORMULA
  }
}
