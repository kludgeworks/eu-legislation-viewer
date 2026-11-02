// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Kind;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Legal texts encoded into the tree and rebuilt from it, as they were. */
class EncoderTest {
  private static final String NS = "xmlns=\"" + LegalText.NAMESPACE + "\"";

  @ParameterizedTest
  @MethodSource("dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts#samples")
  void rebuildsEachSampleAsItWas(String sample) {
    var text = LegalTexts.sample(sample);

    var encoded = Encoder.encode(text);

    assertThat(LegalTexts.lines(LegalTexts.canonical(LegalTexts.rebuild(encoded))))
        .containsExactlyElementsOf(LegalTexts.lines(LegalTexts.canonical(text)));
  }

  @ParameterizedTest
  @MethodSource("dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts#samples")
  void encodesTheRebuiltTextToTheSameRoot(String sample) {
    var encoded = Encoder.encode(LegalTexts.sample(sample));

    assertThat(Encoder.encode(LegalTexts.rebuild(encoded)).root()).isEqualTo(encoded.root());
  }

  @Test
  void keepsEIdsOutOfTheContent() {
    var before = Encoder.encode(LegalTexts.parse(text("art_1", "Scope")));
    var after = Encoder.encode(LegalTexts.parse(text("art_2", "Scope")));

    assertThat(after.root()).isNotEqualTo(before.root());
    assertThat(leaves(after)).isEqualTo(leaves(before));
  }

  @Test
  void citesANoteByItsHashAndRebuildsItsHrefAndMarker() {
    var xml =
        """
        <legalText %s><body>
          <p eId="p_1"><t>See</t><noteRef href="#n_1" marker="1"/></p>
          <note eId="n_1" marker="1"><p><t>A note.</t></p></note>
        </body></legalText>
        """
            .formatted(NS);
    var encoded = Encoder.encode(LegalTexts.parse(xml));

    var p = json(encoded, "[\"p\"");
    assertThat(p).contains("\"note\":\"").doesNotContain("href").doesNotContain("marker");
    assertThat(LegalTexts.canonical(LegalTexts.rebuild(encoded)))
        .isEqualTo(LegalTexts.canonical(LegalTexts.parse(xml)));
  }

  @Test
  void refusesTextBesideElements() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                Encoder.encode(
                    LegalTexts.parse("<legalText %s>loose<body/></legalText>".formatted(NS))))
        .withMessageContaining("Text beside elements");
  }

  private static String text(String eId, String heading) {
    return """
    <legalText %s><body><article eId="%s"><heading><t>%s</t></heading>
      <p><t>This Regulation applies.</t></p></article></body></legalText>
    """
        .formatted(NS, eId, heading);
  }

  private static Set<?> leaves(Tree encoded) {
    return encoded.nodes().values().stream()
        .filter(n -> n.kind() == Kind.LEAF)
        .map(Tree.Node::hash)
        .collect(Collectors.toSet());
  }

  private static String json(Tree encoded, String start) {
    return encoded.nodes().values().stream()
        .map(n -> new String(n.content(), StandardCharsets.UTF_8))
        .filter(s -> s.startsWith(start))
        .findFirst()
        .orElseThrow();
  }
}
