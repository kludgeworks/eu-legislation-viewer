// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.time.LocalDate;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The deferred application of an act on a day: the act is in force, but it does not apply in full
 * on the day yet. Its application is deferred, as the Joint Practical Guide calls it (20.11). If a
 * part of it applies already, its application is partial (20.12). {@link
 * LawOnADay#deferredApplication} gives it.
 *
 * <p>It is a state of the act, not a divergence: the act is not an amendment of itself. For
 * example:
 *
 * <ul>
 *   <li>2016/679 enters into force on 24 May 2016, and applies from 25 May 2018 (Article 99). On 1
 *       March 2017 its application is deferred.
 *   <li>Parts of 2018/1862 apply from earlier days, and the whole act applies from 7 March 2023
 *       (Article 79(5)). On 1 August 2022 its application is deferred and partial.
 * </ul>
 *
 * @param inFull the date from which the act applies in full, with the clause that sets it. It has a
 *     day.
 * @param partial whether a part of the act applies on the day already
 */
@ValueObject
public record DeferredApplication(ActDate inFull, boolean partial) {

  public DeferredApplication {
    Objects.requireNonNull(inFull.date(), "a deferred application has the day it ends");
  }

  /** Returns the day from which the act applies in full. */
  public LocalDate day() {
    return Objects.requireNonNull(inFull.date());
  }
}
