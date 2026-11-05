// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Cellar's amendments of an act's provisions between two of its versions, at the units of their
 * texts (Act.amended).
 *
 * @param byUnit the amendments at each unit, by its eId, in the order found: an addition or
 *     replacement at the later text's, a deletion or repeal at the earlier's
 * @param unplaced how many of them have a location no unit of those texts is
 */
@ValueObject
public record Amended(Map<String, List<Amendment>> byUnit, int unplaced) {

  /** A point numbered as legal-text keeps the points under it beside it: pnt_3, pnt_11.2. */
  private static final Pattern NUMBERED = Pattern.compile("pnt_\\d+(\\.\\d+)*");

  public Amended {
    var copy = new LinkedHashMap<String, List<Amendment>>();
    byUnit.forEach((unit, amendments) -> copy.put(unit, List.copyOf(amendments)));
    byUnit = Collections.unmodifiableMap(copy);
  }

  /**
   * The amendments of the unit {@code eId}: at it, or at a unit it is in (an article replaced
   * whole, for its paragraph); and, {@code withItsUnits} (a unit inserted or deleted whole, with
   * its units), at one in it. Each once.
   */
  public List<Amendment> of(String eId, boolean withItsUnits) {
    var found = new LinkedHashSet<Amendment>();
    byUnit.forEach(
        (unit, amendments) -> {
          if (unit.equals(eId) || in(eId, unit) || (withItsUnits && in(unit, eId))) {
            found.addAll(amendments);
          }
        });
    return List.copyOf(found);
  }

  /**
   * Whether the unit {@code eId} is in {@code unit}: below it, or, for a numbered point, one
   * numbered under it that legal-text keeps beside it (anx/pnt_3.0.1, and its points, in
   * anx/pnt_3).
   */
  private static boolean in(String eId, String unit) {
    if (eId.startsWith(unit + "/")) {
      return true;
    }
    return NUMBERED.matcher(unit.substring(unit.lastIndexOf('/') + 1)).matches()
        && eId.startsWith(unit + ".");
  }

  /** How many amendments are at a unit. */
  public int placed() {
    return (int) byUnit.values().stream().flatMap(List::stream).distinct().count();
  }
}
