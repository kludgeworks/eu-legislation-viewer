// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * Where the text of an act is not the law. The text that a reader gets on a day is always a version
 * of the act, unchanged. A divergence tells on which days that text and the law differ, and why.
 *
 * <p>For example, 2017/815 applies from 1 June 2017, and the first version of 2015/1998 that
 * includes it starts on 7 June 2017. The text is late from 1 to 6 June 2017.
 *
 * @param kind whether the text is late or early
 * @param act the act that the text lacks, for a late text. For an early text, the act whose text
 *     does not apply yet.
 * @param applies the date from which {@code act} applies, with the clause that sets it. For a text
 *     whose date is not confirmed, the date from which {@code act} can apply: its entry into force.
 * @param included the first version that includes the text of {@code act}. Null for a late text
 *     that no version includes yet, and for a text whose date is not confirmed.
 * @param places the provisions where the text differs, as Cellar annotates them. Empty where the
 *     whole act differs, or where Cellar does not say where.
 */
@ValueObject
public record Divergence(
    Kind kind,
    ActIdentifier act,
    ActDate applies,
    @Nullable Version included,
    List<Amendment> places) {

  public Divergence {
    places = List.copyOf(places);
    Objects.requireNonNull(applies.date(), "A divergence needs the day from which the act applies");
    if (kind == Kind.EARLY && included == null) {
      throw new IllegalArgumentException("An early text is in a version");
    }
    if (kind == Kind.UNCONFIRMED && included != null) {
      throw new IllegalArgumentException("A text whose date is not confirmed is in no version");
    }
  }

  /** How a text differs from the law. */
  public enum Kind {
    /** The act applies, but the text does not include it. */
    LATE,
    /** The text is included, but it does not apply yet. */
    EARLY,
    /**
     * The act is in force, and the text does not include it. Cellar does not give the day from
     * which it applies, so it can apply already. For example, 2021/1133 changes 2018/1862 from a
     * date that its Article 6 sets, and no version includes it.
     */
    UNCONFIRMED
  }

  /**
   * Returns whether the whole text of {@code base} differs: this is the early text of {@code base}
   * itself, which is in force but does not apply yet. For example, 2016/679 on 1 March 2017.
   */
  public boolean whole(ActIdentifier base) {
    return kind == Kind.EARLY && act.equals(base);
  }

  /**
   * Returns the units of a text that this divergence touches, in the order of the text, each once.
   * {@code eIds} are the units of the text, in document order.
   *
   * <ul>
   *   <li>A place that the text has is each of its units.
   *   <li>A place that the text does not have is in the unit that holds it, {@link
   *       Location#around}. For example, a point that a late act inserts is in the point above it.
   *   <li>A place for which the text has no unit is left out.
   * </ul>
   */
  public List<Unit> in(List<String> eIds) {
    var found = new LinkedHashMap<String, Unit>();
    for (var place : places) {
      var exact = place.location().in(eIds);
      if (exact.isPresent()) {
        exact.get().eIds().forEach(eId -> found.put(eId, new Unit(eId, false)));
      } else {
        place
            .location()
            .around(eIds)
            .ifPresent(
                around ->
                    around.eIds().forEach(eId -> found.putIfAbsent(eId, new Unit(eId, true))));
      }
    }
    return found.values().stream()
        .sorted(Comparator.comparingInt(unit -> eIds.indexOf(unit.eId())))
        .toList();
  }

  /**
   * A unit of a text that a divergence touches.
   *
   * @param eId the eId of the unit
   * @param holds whether the place is not in the text, and this unit holds it. Only the heading of
   *     the unit is then marked, not all of it.
   */
  @ValueObject
  public record Unit(String eId, boolean holds) {}

  /** Returns the day from which {@code act} applies. */
  public LocalDate appliesFrom() {
    return Objects.requireNonNull(applies.date());
  }

  /**
   * Returns whether the text and the law differ on {@code day}.
   *
   * <ul>
   *   <li>A text whose date is not confirmed can differ on each day from the entry into force of
   *       the act.
   *   <li>A late text differs from the day the act applies to the day before the version that
   *       includes it. If no version includes it, it differs on each day from the day it applies.
   *   <li>An early text differs from the first day of the version that includes it to the day
   *       before the act applies.
   * </ul>
   */
  public boolean on(LocalDate day) {
    return switch (kind) {
      case LATE ->
          !day.isBefore(appliesFrom()) && (included == null || day.isBefore(included.date()));
      case EARLY ->
          !day.isBefore(Objects.requireNonNull(included).date()) && day.isBefore(appliesFrom());
      case UNCONFIRMED -> !day.isBefore(appliesFrom());
    };
  }
}
