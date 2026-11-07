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
 * Cellar's amendments of an act's provisions between two of its versions, placed at the units of
 * their texts. {@link Act#amended} makes it.
 *
 * @param byUnit the amendments at each unit, by the eId of the unit, in the order found. An
 *     addition or a replacement is at a unit of the later text. A deletion or a repeal is at a unit
 *     of the earlier text.
 * @param unplaced how many of the amendments have a location that is not a unit of those texts
 */
@ValueObject
public record Amended(Map<String, List<Amendment>> byUnit, int unplaced) {

  /**
   * Matches the eId of a numbered point, for example {@code pnt_3} or {@code pnt_11.2}. legal-text
   * keeps the points numbered under such a point beside it, not in it.
   */
  private static final Pattern NUMBERED = Pattern.compile("pnt_\\d+(\\.\\d+)*");

  public Amended {
    var copy = new LinkedHashMap<String, List<Amendment>>();
    byUnit.forEach((unit, amendments) -> copy.put(unit, List.copyOf(amendments)));
    byUnit = Collections.unmodifiableMap(copy);
  }

  /**
   * Returns the amendments of the unit {@code eId}. Each amendment appears once.
   *
   * <ul>
   *   <li>The amendments at the unit itself.
   *   <li>The amendments at a unit that contains it. For example, an article replaced whole is an
   *       amendment of its paragraph.
   *   <li>If {@code withItsUnits} is true, the amendments at a unit in it. Use this for a unit
   *       inserted or deleted whole, with its units.
   * </ul>
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
   * Returns whether the unit {@code eId} is in {@code unit}. This is true if it is below the unit.
   * For a numbered point, it is also true for a point numbered under it that legal-text keeps
   * beside it. For example, {@code anx/pnt_3.0.1} and its points are in {@code anx/pnt_3}.
   */
  private static boolean in(String eId, String unit) {
    if (eId.startsWith(unit + "/")) {
      return true;
    }
    return NUMBERED.matcher(unit.substring(unit.lastIndexOf('/') + 1)).matches()
        && eId.startsWith(unit + ".");
  }

  /** Returns how many amendments are at a unit. Each amendment counts once. */
  public int placed() {
    return (int) byUnit.values().stream().flatMap(List::stream).distinct().count();
  }
}
