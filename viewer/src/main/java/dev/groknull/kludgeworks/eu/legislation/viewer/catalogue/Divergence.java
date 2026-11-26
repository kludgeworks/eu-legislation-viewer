// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.time.LocalDate;
import java.util.ArrayList;
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
 * includes it starts on 7 June 2017. From 1 to 6 June 2017, 2017/815 is a pending amendment: the
 * text does not include it.
 *
 * @param kind whether {@code act} is a pending, a prospective or an undated pending amendment
 * @param act the act that the text lacks, for a pending amendment. For a prospective amendment, the
 *     act whose text does not apply yet.
 * @param applies the date from which {@code act} applies, with the clause that sets it. For an
 *     undated pending amendment, the date from which {@code act} can apply: its entry into force.
 * @param included the first version that includes the text of {@code act}. Null for a pending
 *     amendment that no version includes yet, and for an undated pending amendment.
 * @param from the first day on which the text and the law differ
 * @param until the first day on which they agree again. Null if no such day is known: for a pending
 *     amendment that no version includes yet, and for an undated pending amendment.
 * @param places the provisions where the text differs, as Cellar annotates them. Empty where the
 *     whole act differs, or where Cellar does not say where.
 */
@ValueObject
public record Divergence(
    Kind kind,
    ActIdentifier act,
    ActDate applies,
    @Nullable Version included,
    LocalDate from,
    @Nullable LocalDate until,
    List<Amendment> places) {

  public Divergence {
    places = List.copyOf(places);
    Objects.requireNonNull(applies.date(), "A divergence needs the day from which the act applies");
    if (kind == Kind.PROSPECTIVE && included == null) {
      throw new IllegalArgumentException("A prospective amendment is in a version");
    }
    if (kind == Kind.UNDATED && included != null) {
      throw new IllegalArgumentException("An undated pending amendment is in no version");
    }
  }

  /** How a text differs from the law. */
  public enum Kind {
    /** A pending amendment: the act applies, but the text does not include it. */
    PENDING,
    /** A prospective amendment: the text includes it, but it does not apply yet. */
    PROSPECTIVE,
    /**
     * An undated pending amendment: the act is in force, and the text does not include it. Cellar
     * does not give the day from which it applies, so it can apply already. For example, 2021/1133
     * changes 2018/1862 from a date that its Article 6 sets, and no version includes it.
     */
    UNDATED
  }

  /**
   * Returns the occurrences of a text that this divergence touches, in the order of the text, each
   * once. {@code eIds} are the occurrences of the text, in document order.
   *
   * <ul>
   *   <li>A place that the text has is each of its occurrences.
   *   <li>A place that the text does not have is in the occurrence that holds it, {@link
   *       Location#around}. For example, a point that a pending amendment inserts is in the point
   *       above it.
   *   <li>A place for which the text has no occurrence is left out.
   * </ul>
   */
  public List<Occurrence> in(List<String> eIds) {
    var found = new LinkedHashMap<String, Occurrence>();
    var text = TextOccurrences.of(eIds);
    for (var place : places) {
      var exact = place.location().in(text);
      var occurrences = exact.or(() -> place.location().around(text));
      for (var eId : occurrences.map(Location.Found::eIds).orElseGet(List::of)) {
        found.merge(eId, new Occurrence(eId, exact.isEmpty(), List.of(place)), Occurrence::with);
      }
    }
    return found.values().stream()
        .sorted(Comparator.comparingInt(occurrence -> eIds.indexOf(occurrence.eId())))
        .toList();
  }

  /**
   * An occurrence of a text that a divergence touches.
   *
   * @param eId the eId of the occurrence
   * @param holds whether the place is not in the text, and this occurrence holds it. The divergence
   *     divergence note is then beside the heading of the occurrence only, not beside all of it.
   * @param places the places of the divergence that are this occurrence, or that this occurrence
   *     holds
   */
  @ValueObject
  public record Occurrence(String eId, boolean holds, List<Amendment> places) {
    public Occurrence {
      places = List.copyOf(places);
    }

    /**
     * Returns this occurrence with the places of {@code other}, the same occurrence for another
     * place. An occurrence that is a place itself gets its divergence note whole, also if it holds
     * another place.
     */
    Occurrence with(Occurrence other) {
      var all = new ArrayList<>(places);
      all.addAll(other.places);
      return new Occurrence(eId, holds && other.holds, all);
    }
  }

  /** Returns the day from which {@code act} applies. */
  public LocalDate appliesFrom() {
    return Objects.requireNonNull(applies.date());
  }

  /**
   * Returns the day from which {@code act} applies, or null if {@code act} is an undated amendment.
   */
  public @Nullable LocalDate appliesFromDate() {
    return kind == Kind.UNDATED ? null : appliesFrom();
  }

  /**
   * Returns whether the text and the law differ on {@code day}: the day is in the period of this
   * divergence. The rules of {@link TheTextAndTheLawOnADay} set the period.
   */
  public boolean on(LocalDate day) {
    return !day.isBefore(from) && (until == null || day.isBefore(until));
  }
}
