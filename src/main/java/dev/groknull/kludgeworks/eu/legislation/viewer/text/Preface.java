// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** What precedes the preamble: the long title, in blocks. */
public record Preface(List<Block> longTitle) {

  public Preface {
    longTitle = List.copyOf(longTitle);
  }
}
