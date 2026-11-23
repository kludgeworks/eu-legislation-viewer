// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Where a change that no act is known to have made first appears. The sidenote of such a change
 * names the text where it first appears, and what that text consolidates, but no act.
 *
 * <p>The text is the later text of the first pair of neighbouring versions, after the earlier day
 * of the comparison and up to its later day, whose comparison has a difference at the occurrence. A
 * pair whose texts are not both built is passed over. If no such pair has the change, nothing is
 * known of where it appears.
 */
public final class Appearance {
  private Appearance() {}

  /**
   * Returns the version whose text first has each change of {@code eIds}, by the eId of its
   * occurrence. The comparison of {@code from} with {@code to} has these changes.
   *
   * @param differing for a version, the eIds of the differences between the text of the version
   *     before it and its own text, or empty if the two texts are not both built
   */
  @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name = "no-act-named")
  public static Map<String, Version> of(
      Timeline timeline,
      Version from,
      Version to,
      Set<String> eIds,
      Function<Version, Optional<Set<String>>> differing) {
    var found = new HashMap<String, Version>();
    if (eIds.isEmpty()) {
      return found;
    }
    // The comparison itself is the only pair: each of its changes appears in the later text.
    if (timeline.predecessor(to).filter(from::equals).isPresent()) {
      eIds.forEach(eId -> found.put(eId, to));
      return found;
    }
    for (var version : timeline.versions()) {
      if (!version.date().isAfter(from.date()) || version.date().isAfter(to.date())) {
        continue;
      }
      differing.apply(version).stream()
          .flatMap(Set::stream)
          .filter(eIds::contains)
          .forEach(eId -> found.putIfAbsent(eId, version));
      if (found.size() == eIds.size()) {
        break;
      }
    }
    return found;
  }
}
