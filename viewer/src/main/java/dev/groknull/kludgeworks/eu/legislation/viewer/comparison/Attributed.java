// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.LawOverTime;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Prospective;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * A difference with who made it, and from when the change of each of its acts applies. The sidenote
 * of the difference says it in words.
 *
 * @param difference the difference
 * @param attribution who made it ({@link Attribution#of}). {@link Attribution#NONE} where no act is
 *     named.
 * @param applies from when the change of each act of {@code attribution} applies ({@link
 *     Applicability}), in the order of its steps, or of its candidates. An act whose date is not
 *     known has no entry.
 * @param appeared the text where the change first appears, where no act is named ({@link
 *     Appearance}). Null where an act is named, or where nothing shows where it appears.
 * @param notApplying the act whose change at the occurrence does not apply on the later day of the
 *     comparison yet, a prospective amendment. Null where each change applies.
 */
@ValueObject
public record Attributed(
    Difference difference,
    Attribution attribution,
    List<Applicability> applies,
    @Nullable Appeared appeared,
    @Nullable NotApplying notApplying) {

  public Attributed {
    applies = List.copyOf(applies);
  }

  /** Returns {@code difference} with who made it only. */
  public Attributed(Difference difference, Attribution attribution) {
    this(difference, attribution, List.of(), null, null);
  }

  /**
   * The text where a change that no act is known to have made first appears.
   *
   * @param version the version of the text
   * @param consolidates the acts that the version includes ({@link Inclusions#of})
   */
  @ValueObject
  public record Appeared(Version version, List<ActIdentifier> consolidates) {
    public Appeared {
      consolidates = List.copyOf(consolidates);
    }
  }

  /**
   * A change that does not apply on the later day of the comparison yet.
   *
   * @param act the act of the change
   * @param day the later day of the comparison
   */
  @ValueObject
  public record NotApplying(ActIdentifier act, LocalDate day) {}

  /**
   * Returns who made {@code difference} in the comparison of the version {@code from} of {@code
   * act} with the version {@code to}, and from when the change of each of its acts applies.
   *
   * @param included the acts that each version includes: Cellar's lists, and the acts that the
   *     built texts tag first
   * @param amendments Cellar's amendments of the occurrence, by the acts between the two versions
   *     ({@link Attribution#amendmentsOf})
   * @param removals Cellar's amendments that take the occurrence out of the text ({@link
   *     Attribution#removalsOf})
   */
  public static Attributed of(
      Act act,
      Inclusions included,
      Version from,
      Version to,
      Difference difference,
      List<Amendment> amendments,
      List<Amendment> removals) {
    var attribution = Attribution.of(act, included, from, to, difference, amendments, removals);
    var applies = new ArrayList<Applicability>();
    if (attribution.steps().isEmpty()) {
      for (var each : attribution.candidates()) {
        Applicability.of(act, each, Set.of()).ifPresent(applies::add);
      }
    } else {
      for (var step : attribution.steps()) {
        for (var each : step.acts()) {
          Applicability.of(act, included, from, to, each, amendments, step.forPart().contains(each))
              .ifPresent(applies::add);
        }
      }
    }
    return new Attributed(difference, attribution, applies, null, null);
  }

  /**
   * Returns this difference, which no act is named for, as first appearing in the text of {@code
   * version}. It names no act as the one that made the change.
   */
  public Attributed appearedIn(Version version, Inclusions included) {
    return new Attributed(
        difference,
        Attribution.NONE,
        List.of(),
        new Appeared(version, included.of(version)),
        notApplying);
  }

  /**
   * Returns this difference with the change at its occurrence that does not apply on the day of
   * {@code prospective} yet, if there is one: a change that the later text includes before its act
   * applies ({@link LawOverTime#changesThatDoNotApplyYet}). The change is at the occurrence of the
   * difference, at an occurrence that it is in, or at an occurrence in it. Returns this difference
   * if each change there applies.
   */
  @BusinessRule(group = RuleGroup.WHEN_A_CHANGE_APPLIES, name = "change-that-does-not-apply-yet")
  public Attributed notApplying(Prospective prospective) {
    return prospective
        .at(difference.eId())
        .map(act -> notApplyingYet(act, prospective.day()))
        .orElse(this);
  }

  /**
   * Returns this difference with the change of {@code act} that does not apply on {@code day} yet.
   */
  public Attributed notApplyingYet(ActIdentifier act, LocalDate day) {
    return new Attributed(difference, attribution, applies, appeared, new NotApplying(act, day));
  }
}
