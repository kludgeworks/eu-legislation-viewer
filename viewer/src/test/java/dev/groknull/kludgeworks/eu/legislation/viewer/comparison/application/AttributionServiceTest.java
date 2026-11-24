// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attribution;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Tag;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Modification;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Versions;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

/**
 * Tests how {@link AttributionService} finds who made each difference of a comparison of 2015/1998,
 * from 22 March 2026 to 1 October 2026. The comparison, the outlines and the tags are given. The
 * versions, dates and amendments are Cellar's, recorded.
 */
class AttributionServiceTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));
  static final Inclusions LISTED = Inclusions.listed(AVSEC.timeline());
  static final Version FROM = version("02015R1998-20260322");
  static final Version TO = version("02015R1998-20261001");
  static final TextId EARLIER = new TextId(1);
  static final TextId LATER = new TextId(2);

  /** Point 8.1.2.3(c), which 2026/449 replaced. */
  static final Difference NAMED =
      changed("anx/pnt_8.1.2.3/pnt_c", new Tag("32026R0449", Modification.REPLACED, null));

  /** Point 8.1.2.3(f), whose tag names an act before the two versions. */
  static final Difference UNNAMED =
      changed("anx/pnt_8.1.2.3/pnt_f", new Tag("32023R0566", Modification.REPLACED, null));

  private final Comparisons comparisons = mock();

  /** The service, with no outline of a text, and no tags. */
  private final AttributionService service = new AttributionService(comparisons, mock(), mock());

  @Test
  void namesTheActsOfEachDifferenceAndTheTextWhereAnUnnamedChangeFirstAppears() {
    when(comparisons.between(EARLIER, LATER))
        .thenReturn(
            Optional.of(new Comparison(EARLIER, LATER, List.of(NAMED, UNNAMED), List.of(), null)));
    when(comparisons.appeared(any(), any(), any(), any(), any()))
        .thenReturn(Map.of(UNNAMED.eId(), TO));

    var compared = service.of(AVSEC, versions(built()), FROM, TO, null).orElseThrow();

    var named = compared.of(NAMED);
    assertThat(named.attribution().acts()).containsExactly(new ActIdentifier("32026R0449"));
    assertThat(named.applies()).isNotEmpty();
    assertThat(named.appeared()).isNull();
    var unnamed = compared.of(UNNAMED);
    assertThat(unnamed.attribution()).isEqualTo(Attribution.NONE);
    var appeared = Objects.requireNonNull(unnamed.appeared());
    assertThat(appeared.version()).isEqualTo(TO);
    assertThat(appeared.consolidates()).isEqualTo(LISTED.of(TO));
    assertThat(compared.each()).extracting(each -> each.notApplying()).containsOnlyNulls();
  }

  @Test
  void findsNothingWhileATextIsNotBuilt() {
    var waiting = new LinkedHashMap<>(built().texts());
    waiting.put(TO, new Text.Waiting(0));

    assertThat(service.of(AVSEC, versions(preparation(waiting)), FROM, TO, null)).isEmpty();
  }

  /** The versions of 2015/1998 in English, with the texts of {@code preparation}. */
  private static Versions versions(Preparation preparation) {
    return new Versions(Language.ENG, AVSEC.timeline(), preparation, LISTED);
  }

  /** The preparation of 2015/1998 with its texts built: From as text 1, To as text 2. */
  private static Preparation built() {
    var built = new LinkedHashMap<Version, Text>();
    for (var version : AVSEC.timeline().versions()) {
      built.put(
          version,
          new Text.Ready(
              version.equals(FROM) ? EARLIER : version.equals(TO) ? LATER : new TextId(9)));
    }
    return preparation(built);
  }

  private static Preparation preparation(SequencedMap<Version, Text> texts) {
    return new Preparation(
        AVSEC.id(), Language.ENG, Instant.parse("2026-11-06T08:00:00Z"), null, texts, List.of(), 0);
  }

  private static Difference changed(String eId, Tag tag) {
    return new Difference(
        eId,
        Kind.CHANGED,
        new Sibling(eId, "point", ""),
        new Element("occurrence", Map.of("eId", eId), List.of(), ""),
        0,
        0,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(tag),
        List.of(),
        null,
        null);
  }

  private static Version version(String celex) {
    return AVSEC.timeline().versions().stream()
        .filter(v -> v.celex().equals(celex))
        .findFirst()
        .orElseThrow();
  }
}
