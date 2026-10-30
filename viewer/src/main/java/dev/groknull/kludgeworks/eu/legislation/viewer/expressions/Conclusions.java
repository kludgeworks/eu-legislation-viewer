// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;

/**
 * The closing block: final formula and signature.
 *
 * @param signatures The signatures: one, one per party to an agreement, or none (consolidated
 *     versions).
 */
public record Conclusions(List<Block> content, List<Signature> signatures) {

  public Conclusions {
    content = List.copyOf(content);
    signatures = List.copyOf(signatures);
  }
}
