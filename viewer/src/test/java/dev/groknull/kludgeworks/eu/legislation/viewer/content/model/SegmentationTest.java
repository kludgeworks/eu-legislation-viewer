// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Segment;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * Tests the windows of a long text. A window is a run of the children of a container. The windows
 * cover the children in order.
 */
class SegmentationTest {
  private static final String LONG = "06.02.1/02015R1998-20260322.xml";

  @Test
  void cutsALongTextIntoRunsOfItsContainersChildren() {
    var tree = Encoder.encode(LegalTexts.sample(LONG));

    var segments = Segmentation.of(tree);

    assertThat(segments).hasSizeGreaterThan(1);
    var end = 0L;
    for (var segment : segments) {
      var parent = Objects.requireNonNull(tree.nodes().get(segment.parent()));
      var weight =
          parent.children().subList(segment.from(), segment.to()).stream()
              .mapToLong(e -> Objects.requireNonNull(tree.nodes().get(e.child())).weight())
              .sum();
      assertThat(segment.weight()).isEqualTo(weight);
      assertThat(segment.offset()).isGreaterThanOrEqualTo(end);
      end = segment.offset() + segment.weight();
    }
    assertThat(end)
        .isLessThanOrEqualTo(Objects.requireNonNull(tree.nodes().get(tree.root())).weight());
  }

  @Test
  void leavesATextNoHeavierThanTheTargetWhole() {
    var tree =
        Encoder.encode(
            LegalTexts.parse(
                "<legalText xmlns=\"%s\"><body><p><t>Short.</t></p></body></legalText>"
                    .formatted(LegalText.NAMESPACE)));

    assertThat(Segmentation.of(tree)).isEmpty();
  }

  @Test
  void givesARunTheSameIdentityInEveryTextThatHasIt() {
    var segments = Segmentation.of(Encoder.encode(LegalTexts.sample(LONG)));
    var next =
        Segmentation.of(Encoder.encode(LegalTexts.sample("06.02.1/02015R1998-20261001.xml")));

    assertThat(next.stream().map(Segment::hash))
        .containsAnyElementsOf(segments.stream().map(Segment::hash).toList());
  }
}
