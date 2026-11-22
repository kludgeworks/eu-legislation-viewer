// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Comparisons of stored texts. Each comparison is worked out once, the first time that it is asked
 * for, and then kept. A kept comparison that an earlier version of the rules worked out is worked
 * out again.
 */
public interface Comparisons {

  /**
   * Returns how the text {@code from} became the text {@code to}. The comparison is the kept one,
   * or it is worked out now. Returns none if one of the texts is not stored.
   */
  Optional<Comparison> between(TextId from, TextId to);

  /**
   * Returns the difference at {@code eId} in the comparison of {@code from} with {@code to}, with
   * its occurrence whole. An annex inserted whole is in {@link #between} with only its opening,
   * because the store keeps it apart. Returns none if there is no such difference.
   */
  Optional<Difference> difference(TextId from, TextId to, String eId);

  /**
   * Returns the version whose text first has each change of {@code eIds}, by the eId of its
   * occurrence, from the kept comparisons of the neighbouring versions ({@link Appearance},
   * R-WHO-07). The comparison of {@code from} with {@code to} has these changes. {@code texts} are
   * the texts of the versions, as their preparation has them: a pair whose texts are not both ready
   * is passed over.
   */
  Map<String, Version> appeared(
      Timeline timeline, Map<Version, Text> texts, Version from, Version to, Set<String> eIds);
}
