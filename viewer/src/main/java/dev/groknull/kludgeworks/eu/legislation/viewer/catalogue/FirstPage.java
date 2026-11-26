// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.time.LocalDate;

/**
 * The page that the address of an act without a page opens. {@link Readings#first} tells which.
 *
 * <ul>
 *   <li>An act that has amending acts opens on its history.
 *   <li>An act that no act changed opens on the law on today.
 *   <li>If the reader gives a day, the act opens on the law on that day.
 * </ul>
 */
public sealed interface FirstPage {

  /** The history of the act. */
  record History() implements FirstPage {}

  /**
   * The law of the act on a day.
   *
   * @param day the day: the day that the reader gave, or today
   */
  record Read(LocalDate day) implements FirstPage {}
}
