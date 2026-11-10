// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link Chunker} cuts a text into chunks of bounded size, which versions share, and
 * which {@link Decoder} reads back. The samples' round trips through the chunks are in {@code
 * EncoderTest}.
 */
class ChunkerTest {
  private static final String LONG = "06.02.1/02015R1998-20260322.xml";
  private static final String NEXT = "06.02.1/02015R1998-20261001.xml";

  @Test
  void cutsALongTextIntoChunksOfAboutTheLimit() {
    var chunks = Chunker.of(Encoder.encode(LegalTexts.sample(LONG)));

    assertThat(chunks.bodies()).hasSizeGreaterThan(1);
    assertThat(chunks.bodies().values())
        .allSatisfy(
            body ->
                assertThat(body.length <= 2 * Chunker.LIMIT || nodes(body) == 1)
                    .as("%d bytes in %d nodes", body.length, nodes(body))
                    .isTrue());
  }

  @Test
  void keepsAShortTextInOneChunk() {
    var chunks =
        Chunker.of(
            Encoder.encode(
                LegalTexts.parse(
                    "<legalText xmlns=\"%s\"><body><p><t>Short.</t></p></body></legalText>"
                        .formatted(LegalText.NAMESPACE))));

    assertThat(chunks.manifest()).containsExactly(chunks.top());
  }

  @Test
  void sharesMostChunksBetweenTwoVersions() {
    var chunks = Chunker.of(Encoder.encode(LegalTexts.sample(LONG)));
    var next = Chunker.of(Encoder.encode(LegalTexts.sample(NEXT)));

    var shared = next.manifest().stream().filter(chunks.bodies()::containsKey).count();
    assertThat(shared).isGreaterThan(next.manifest().size() / 2);
  }

  @Test
  void cutsTheSameTreeIntoTheSameChunks() {
    var tree = Encoder.encode(LegalTexts.sample(LONG));

    assertThat(Chunker.of(tree).manifest()).isEqualTo(Chunker.of(tree).manifest());
  }

  private static int nodes(byte[] body) {
    return ((List<?>) Identity.parse(body)).size();
  }
}
