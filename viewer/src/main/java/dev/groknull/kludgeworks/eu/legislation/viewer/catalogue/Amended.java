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
 * Cellar's amendments of an act's provisions between two of its versions, placed at the occurrences
 * of their texts. {@link Act#amended} makes it.
 *
 * @param byOccurrence the amendments at each occurrence, by the eId of the occurrence, in the order
 *     found. An addition or a replacement is at an occurrence of the later text. A deletion or a
 *     repeal is at an occurrence of the earlier text.
 * @param unplaced how many of the amendments have a location that is not an occurrence of those
 *     texts
 */
@ValueObject
public record Amended(Map<String, List<Amendment>> byOccurrence, int unplaced) {

  /**
   * Matches the eId of a numbered point, for example {@code pnt_3} or {@code pnt_11.2}. legal-text
   * keeps the points numbered under such a point beside it, not in it.
   */
  private static final Pattern NUMBERED = Pattern.compile("pnt_\\d+(\\.\\d+)*");

  public Amended {
    var copy = new LinkedHashMap<String, List<Amendment>>();
    byOccurrence.forEach((occurrence, amendments) -> copy.put(occurrence, List.copyOf(amendments)));
    byOccurrence = Collections.unmodifiableMap(copy);
  }

  /**
   * Returns the amendments of the occurrence {@code eId}. Each amendment appears once.
   *
   * <ul>
   *   <li>The amendments at the occurrence itself.
   *   <li>The amendments at an occurrence that contains it. For example, an article replaced whole
   *       is an amendment of its paragraph.
   *   <li>If {@code withItsOccurrences} is true, the amendments at an occurrence in it. Use this
   *       for an occurrence inserted or deleted whole, with its occurrences.
   * </ul>
   */
  public List<Amendment> of(String eId, boolean withItsOccurrences) {
    var found = new LinkedHashSet<Amendment>();
    byOccurrence.forEach((occurrence, amendments) -> {
      if (occurrence.equals(eId)
          || in(eId, occurrence)
          || (withItsOccurrences && in(occurrence, eId))) {
        found.addAll(amendments);
      }
    });
    return List.copyOf(found);
  }

  /**
   * Returns whether the occurrence {@code eId} is in {@code occurrence}. This is true if it is
   * below the occurrence. For a numbered point, it is also true for a point numbered under it that
   * legal-text keeps beside it. For example, {@code anx/pnt_3.0.1} and its points are in {@code
   * anx/pnt_3}.
   */
  static boolean in(String eId, String occurrence) {
    if (eId.startsWith(occurrence + "/")) {
      return true;
    }
    return NUMBERED.matcher(occurrence.substring(occurrence.lastIndexOf('/') + 1)).matches()
        && eId.startsWith(occurrence + ".");
  }

  /** Returns how many amendments are at an occurrence. Each amendment counts once. */
  public int placed() {
    return (int) byOccurrence.values().stream().flatMap(List::stream).distinct().count();
  }
}
