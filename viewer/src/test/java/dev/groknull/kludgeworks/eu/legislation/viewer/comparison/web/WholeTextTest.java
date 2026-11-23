// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Differ;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Outlines;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Where the Whole text puts a deleted provision: where it stood in the earlier text, after the
 * nearest provision before it that the later text still has. On the texts of Directive 2013/36 of
 * 17 January 2025 and of 11 January 2026, which the converter's samples have.
 */
class WholeTextTest {
  private static List<String> earlier = List.of();
  private static Set<String> later = Set.of();
  private static List<Difference> deleted = List.of();

  @BeforeAll
  static void compare() {
    var from = element("06.02.1/02013L0036-20250117.xml");
    var to = element("06.02.4/02013L0036-20260111.xml");
    earlier = Outlines.of(from).eIds();
    var occurrences = new HashSet<String>();
    collect(to, occurrences);
    later = occurrences;
    deleted =
        Differ.differences(from, to).differences().stream()
            .filter(each -> each.kind() == Kind.DELETED)
            .toList();
  }

  @Test
  void putsADeletedProvisionAfterTheOccurrenceBeforeItThatTheLaterTextHas() {
    // Point (24) of Article 2(5) was deleted. Point (23) is still there.
    assertThat(placed("art_2/par_5/pnt_24"))
        .isEqualTo(new WholeText.Placed("art_2/par_5/pnt_23", true));
  }

  @Test
  void putsItAfterTheSiblingThatHoldsTheOccurrenceBeforeIt() {
    // Article 161(3) was deleted. The occurrence just before it is inside Article 161(2), so it
    // stands after Article 161(2).
    assertThat(placed("art_161/par_3")).isEqualTo(new WholeText.Placed("art_161/par_2", true));
  }

  @Test
  void putsAProvisionThatWasFirstInItsParentBeforeTheOccurrenceAfterIt() {
    // Points (a) to (c) of Article 23(1) were deleted. Point (a) was first in Article 23(1), so it
    // stands before the next occurrence that the later text has, Article 23(2).
    assertThat(placed("art_23/par_1/pnt_a")).isEqualTo(new WholeText.Placed("art_23/par_2", false));
  }

  private static WholeText.Placed placed(String eId) {
    var difference =
        deleted.stream().filter(each -> each.eId().equals(eId)).findFirst().orElseThrow();
    var placed = WholeText.place(eId, difference.parent(), earlier, later);
    assertThat(placed).as(eId).isNotNull();
    return placed;
  }

  private static Element element(String path) {
    return Decoder.element(Encoder.encode(LegalTexts.sample(path)));
  }

  private static void collect(Element element, Set<String> out) {
    var eId = element.name().equals("occurrence") ? element.attribute("eId") : null;
    if (eId != null) {
      out.add(eId);
    }
    element.children().forEach(child -> collect(child, out));
  }
}
