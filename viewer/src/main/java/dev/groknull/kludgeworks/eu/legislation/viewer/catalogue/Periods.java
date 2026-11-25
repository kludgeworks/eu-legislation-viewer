// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * What is true of something over time: periods in order, with no gap, that have each day. For
 * example, the states of an act: not in force until its entry into force, then in force and not
 * applying, then applying.
 *
 * @param periods the periods, in order. The first has no start, and the last has no end.
 * @param <T> what is true in a period
 */
public record Periods<T>(List<Period<T>> periods) {

  public Periods {
    periods = List.copyOf(periods);
    if (periods.isEmpty()) {
      throw new IllegalArgumentException("No period");
    }
  }

  /**
   * A period.
   *
   * @param from its first day, or null if it has no start
   * @param until the first day of the next period, or null if it has no end
   * @param value what is true in it
   */
  public record Period<T>(@Nullable LocalDate from, @Nullable LocalDate until, T value) {}

  /** Returns what is true on {@code day}. */
  public T on(LocalDate day) {
    return periods.stream()
        .filter(period -> period.until() == null || day.isBefore(period.until()))
        .findFirst()
        .orElseThrow()
        .value();
  }

  /** Returns the first day of each period that has a start, in order. */
  public List<LocalDate> starts() {
    return periods.stream().skip(1).map(Period::from).toList();
  }

  /** Starts with {@code value} on each day. */
  public static <T> Builder<T> always(T value) {
    return new Builder<>(value);
  }

  /** Builds periods. A later line overrides an earlier line. */
  public static final class Builder<T> {
    private T first;
    private final NavigableMap<LocalDate, T> starts = new TreeMap<>();

    private Builder(T first) {
      this.first = first;
    }

    /** From {@code day} on, {@code value} is true. */
    public Builder<T> from(LocalDate day, T value) {
      starts.tailMap(day, true).clear();
      starts.put(day, value);
      return this;
    }

    /** Before {@code day}, {@code value} is true. */
    public Builder<T> before(LocalDate day, T value) {
      var on = on(day);
      starts.headMap(day, true).clear();
      starts.put(day, on);
      first = value;
      return this;
    }

    private T on(LocalDate day) {
      var entry = starts.floorEntry(day);
      return entry == null ? first : entry.getValue();
    }

    /** Returns the periods. Two periods in sequence with the same value are one period. */
    public Periods<T> build() {
      var periods = new ArrayList<Period<T>>();
      LocalDate from = null;
      var value = first;
      for (Map.Entry<LocalDate, T> start : starts.entrySet()) {
        if (!start.getValue().equals(value)) {
          periods.add(new Period<>(from, start.getKey(), value));
          from = start.getKey();
          value = start.getValue();
        }
      }
      periods.add(new Period<>(from, null, value));
      return new Periods<>(periods);
    }
  }
}
