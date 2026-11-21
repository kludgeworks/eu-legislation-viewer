// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The amending acts and corrigenda that each version of an act includes.
 *
 * <p>A version includes the acts that Cellar lists for it ({@link Version#integrates}), and the
 * acts that its own text marks first. Cellar's list can lack an act that the text already marks.
 * For example, the version of 767/2008 of 29 December 2017 marks 16 occurrences with 2017/2226, but
 * Cellar lists 2017/2226 only from the version of 11 June 2019.
 *
 * <p>An act that the text of a version marks is first marked there if no earlier version includes
 * it: Cellar lists it for no earlier version, and no earlier text that is built marks it. Where the
 * text of a version is not built, only Cellar's list counts for it.
 *
 * @param timeline the versions, in one language
 * @param marked the acts that the text of each version marks first, by version. Cellar lists none
 *     of them for that version.
 */
@ValueObject
public record Inclusions(Timeline timeline, Map<Version, List<ActIdentifier>> marked) {

  public Inclusions {
    marked = Map.copyOf(marked);
  }

  /**
   * Returns what the versions of {@code timeline} include, with {@code texts}, the marks of the
   * texts that are built, by version.
   */
  public static Inclusions of(Timeline timeline, Map<Version, TextMarks> texts) {
    var included = new HashSet<ActIdentifier>();
    var marked = new HashMap<Version, List<ActIdentifier>>();
    for (var version : timeline.versions()) {
      included.addAll(version.integrates());
      var text = texts.get(version);
      if (text == null) {
        continue;
      }
      var first =
          text.byAct().keySet().stream()
              .filter(each -> !included.contains(each))
              .sorted(Comparator.comparing(ActIdentifier::celex))
              .toList();
      if (!first.isEmpty()) {
        marked.put(version, first);
        included.addAll(first);
      }
    }
    return new Inclusions(timeline, marked);
  }

  /** Returns what the versions of {@code timeline} include by Cellar's lists only. */
  public static Inclusions listed(Timeline timeline) {
    return new Inclusions(timeline, Map.of());
  }

  /**
   * Returns the acts that {@code version} includes: Cellar's list, in its order, then the acts that
   * its text marks first.
   */
  public List<ActIdentifier> of(Version version) {
    var first = marked.get(version);
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
        || marked.getOrDefault(version, List.of()).contains(act);
  }

  /**
   * Returns the acts that the versions after {@code from}, up to and including {@code to}, include,
   * in the order of the versions. The acts include their corrigenda.
   */
  public SequencedSet<ActIdentifier> between(Version from, Version to) {
    var between = new LinkedHashSet<ActIdentifier>();
    for (var version : timeline.versions()) {
      if (version.date().isAfter(from.date()) && !version.date().isAfter(to.date())) {
        between.addAll(of(version));
      }
    }
    return between;
  }
}
