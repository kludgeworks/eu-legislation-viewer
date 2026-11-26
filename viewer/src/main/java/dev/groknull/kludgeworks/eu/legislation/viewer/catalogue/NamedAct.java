// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.net.URI;

/**
 * What the viewer has for words that name one act: its CELEX number or its ELI. {@link NamedActs}
 * tells which case the words are in. A page shows the case: it decides nothing.
 */
public sealed interface NamedAct {

  /** Returns how the words name the act. */
  Name name();

  /**
   * How a reader named one act.
   *
   * @param as how the words name the act, for the log. For example, "CELEX number".
   * @param number the number of the act, as a reader reads it. For example, "2015/1998" for a CELEX
   *     number, or "reg_impl/2015/1998" for an ELI.
   * @param eurLex the act on EUR-Lex, in English
   */
  record Name(String as, String number, URI eurLex) {}

  /** The catalogue has the act, and the act has a page here. */
  record Found(Name name, Eli eli) implements NamedAct {}

  /** No act has the number, or the act has no ELI: an act without an ELI has no page here. */
  record NoAct(Name name) implements NamedAct {}

  /** The catalogue does not have the act yet, and the viewer looks it up. */
  record LookingUp(Name name) implements NamedAct {}

  /** The Publications Office did not answer the lookup of the act. */
  record Unanswered(Name name) implements NamedAct {}
}
