// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The amending acts and corrigenda that each version of an act includes.
 *
 * <p>A version includes the acts that Cellar lists for it ({@link Version#integrates}), and the
 * acts that its own text tags first. Cellar's list can lack an act that the text already tags. For
 * example, the version of 767/2008 of 29 December 2017 tags 16 occurrences with 2017/2226, but
 * Cellar lists 2017/2226 only from the version of 11 June 2019.
 *
 * <p>An act that the text of a version tags is first tagged there if no earlier version includes
 * it: Cellar lists it for no earlier version, and no earlier text that is built tags it. Where the
 * text of a version is not built, only Cellar's list counts for it.
 *
 * @param timeline the versions, in one language
 * @param tagged the acts that the text of each version tags first, by version. Cellar lists none of
 *     them for that version.
 */
@ValueObject
public record Inclusions(Timeline timeline, Map<Version, List<ActIdentifier>> tagged) {

  public Inclusions {
    tagged = Map.copyOf(tagged);
  }

  /**
   * Returns what the versions of {@code timeline} include, with {@code texts}, the tags of the
   * texts that are built, by version ({@link WhatAComparisonCovers#theActsAVersionIncludes}).
   */
  public static Inclusions of(Timeline timeline, Map<Version, TextTags> texts) {
    return new Inclusions(
        timeline, WhatAComparisonCovers.theActsAVersionIncludes(timeline.versions(), texts));
  }

  /** Returns what the versions of {@code timeline} include by Cellar's lists only. */
  public static Inclusions listed(Timeline timeline) {
    return new Inclusions(timeline, Map.of());
  }

  /**
   * Returns the acts that {@code version} includes: Cellar's list, in its order, then the acts that
   * its text tags first.
   */
  public List<ActIdentifier> of(Version version) {
    var first = tagged.get(version);
    if (first == null) {
      return version.integrates();
    }
    var acts = new ArrayList<>(version.integrates());
    acts.addAll(first);
    return List.copyOf(acts);
  }

  /** Returns whether {@code version} includes {@code act}. */
  public boolean includes(Version version, ActIdentifier act) {
    return version.integrates().contains(act)
        || tagged.getOrDefault(version, List.of()).contains(act);
  }

  /**
   * Returns the acts that the versions after {@code from}, up to and including {@code to}, include,
   * in the order of the versions ({@link WhatAComparisonCovers#theActsBetweenTwoVersions}). The
   * acts include their corrigenda.
   */
  public SequencedSet<ActIdentifier> between(Version from, Version to) {
    return WhatAComparisonCovers.theActsBetweenTwoVersions(timeline.versions(), this, from, to);
  }

  /**
   * Returns the act {@code celex} if it is between {@code from} and {@code to}, or empty if it is
   * not ({@link WhatAComparisonCovers#oneActBetweenTheVersions}).
   */
  public Optional<ActIdentifier> oneBetween(Version from, Version to, String celex) {
    return WhatAComparisonCovers.oneActBetweenTheVersions(between(from, to), celex);
  }
}
