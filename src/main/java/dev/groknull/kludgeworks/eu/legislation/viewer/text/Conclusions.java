// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** The closing block: its paragraphs and the signatures. */
public record Conclusions(List<Block> content, List<Signature> signatures) {

  public Conclusions {
    content = List.copyOf(content);
    signatures = List.copyOf(signatures);
  }
}
