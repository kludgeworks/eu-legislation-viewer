// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The occurrences of the text of a version in a language, and the acts that mark them. A
 * consolidated text marks the wording that an act gave, with the act. For example, the version of
 * 2015/1998 of 7 June 2017 marks point 4.1.3.3 of its annex with 2017/815.
 *
 * @param eIds the eIds of the occurrences of the text, in document order. A {@link Location} is
 *     found among them.
 * @param byAct the occurrences that each act marks, by their eIds
 */
@ValueObject
public record TextMarks(List<String> eIds, Map<ActIdentifier, Set<String>> byAct) {

  public TextMarks {
    eIds = List.copyOf(eIds);
    byAct =
        byAct.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, each -> Set.copyOf(each.getValue())));
  }

  /** Returns the eIds of the occurrences that {@code act} marks. */
  public Set<String> marked(ActIdentifier act) {
    return byAct.getOrDefault(act, Set.of());
  }

  /** Returns whether the text marks an occurrence with {@code act}. */
  public boolean marks(ActIdentifier act) {
    return byAct.containsKey(act);
  }
}
