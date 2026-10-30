// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * An act's versions in date order, each applying from its date until the next one's. Which applies
 * on a date: the last that starts on or before it (Design: Choosing the version to show), interim
 * ones included once their date has passed; after a repeal, the last.
 */
@ValueObject
public record Timeline(List<Version> versions) {

  public Timeline {
    versions = List.copyOf(versions);
    if (versions.isEmpty()) {
      throw new IllegalArgumentException("A timeline has at least the original text");
    }
    for (var i = 1; i < versions.size(); i++) {
      if (!versions.get(i).date().isAfter(versions.get(i - 1).date())) {
        throw new IllegalArgumentException(
            "Versions out of order: " + versions.get(i - 1) + ", then " + versions.get(i));
      }
    }
  }

  /** The version that applies on {@code date}; none before the first applies. */
  public Optional<Version> applicableOn(LocalDate date) {
    Version applicable = null;
    for (var version : versions) {
      if (version.date().isAfter(date)) {
        break;
      }
      applicable = version;
    }
    return Optional.ofNullable(applicable);
  }

  /** The latest version, which may apply only in the future. */
  public Version latest() {
    return versions.getLast();
  }

  /** The version before {@code version}, none for the first. */
  public Optional<Version> predecessor(Version version) {
    var i = indexOf(version);
    return i == 0 ? Optional.empty() : Optional.of(versions.get(i - 1));
  }

  /** The version after {@code version}, none for the latest. */
  public Optional<Version> successor(Version version) {
    var i = indexOf(version);
    return i == versions.size() - 1 ? Optional.empty() : Optional.of(versions.get(i + 1));
  }

  private int indexOf(Version version) {
    var i = versions.indexOf(version);
    if (i < 0) {
      throw new IllegalArgumentException("Not a version of this act: " + version);
    }
    return i;
  }
}
