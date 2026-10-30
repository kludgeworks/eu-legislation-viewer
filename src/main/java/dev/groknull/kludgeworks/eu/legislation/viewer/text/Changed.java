// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/**
 * A text-level amendment: the words an amending act inserted or replaced inside a paragraph (an
 * amendment marker that opens or closes inside text). Inside a num, it shows a renumbering.
 */
public record Changed(Provenance provenance, List<Inline> content) implements Inline {

  public Changed {
    content = List.copyOf(content);
  }
}
