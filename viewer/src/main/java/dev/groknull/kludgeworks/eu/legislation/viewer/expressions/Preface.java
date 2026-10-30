// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;

/**
 * What comes before the preamble: the long title.
 *
 * @param longTitle The long title.
 */
public record Preface(List<Block> longTitle) {

  public Preface {
    longTitle = List.copyOf(longTitle);
  }
}
