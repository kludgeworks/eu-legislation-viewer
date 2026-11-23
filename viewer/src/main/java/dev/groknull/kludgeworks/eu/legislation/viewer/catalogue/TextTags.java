// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The occurrences of the text of a version in a language, and the acts that tag them. A
 * consolidated text tags the wording that an act gave, with the act. For example, the version of
 * 2015/1998 of 7 June 2017 tags point 4.1.3.3 of its annex with 2017/815.
 *
 * @param eIds the eIds of the occurrences of the text, in document order. A {@link Location} is
 *     found among them.
 * @param byAct the occurrences that each act tags, by their eIds
 */
@ValueObject
public record TextTags(List<String> eIds, Map<ActIdentifier, Set<String>> byAct) {

  public TextTags {
    eIds = List.copyOf(eIds);
    byAct =
        byAct.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, each -> Set.copyOf(each.getValue())));
  }

  /**
   * Returns the tags of the text whose outline is {@code outline}, by act. A tag that is not a
   * CELEX number is left out.
   */
  public static TextTags of(Outline outline) {
    var byAct = new HashMap<ActIdentifier, Set<String>>();
    outline
        .tags()
        .forEach(
            (occurrence, acts) ->
                acts.stream()
                    .filter(ActIdentifier::isCelex)
                    .forEach(
                        act ->
                            byAct
                                .computeIfAbsent(new ActIdentifier(act), each -> new HashSet<>())
                                .add(occurrence)));
    return new TextTags(outline.eIds(), byAct);
  }

  /** Returns the eIds of the occurrences that {@code act} tags. */
  public Set<String> tagged(ActIdentifier act) {
    return byAct.getOrDefault(act, Set.of());
  }

  /** Returns whether the text tags an occurrence with {@code act}. */
  public boolean tags(ActIdentifier act) {
    return byAct.containsKey(act);
  }
}
