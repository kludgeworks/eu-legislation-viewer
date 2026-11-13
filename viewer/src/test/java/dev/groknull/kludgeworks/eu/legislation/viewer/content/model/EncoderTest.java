// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests that {@link Encoder} writes legal texts as JSON, and that {@link Decoder} reads them back
 * as the converter wrote them.
 */
class EncoderTest {
  private static final String NS = "xmlns=\"" + LegalText.NAMESPACE + "\"";

  @ParameterizedTest
  @MethodSource("dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts#samples")
  void decodesEachSampleAsTheConverterWroteIt(String sample) {
    var text = LegalTexts.sample(sample);

    var decoded = Decoder.element(Encoder.encode(text));

    assertThat(decoded).isEqualTo(LegalTexts.element(text));
    assertThat(LegalTexts.lines(LegalTexts.canonical(decoded)))
        .containsExactlyElementsOf(LegalTexts.lines(LegalTexts.canonical(text)));
  }

  @Test
  void writesTheSameJsonWhateverTheOrderOfTheAttributes() {
    var before = Encoder.encode(LegalTexts.parse(text("eId=\"art_1\" kind=\"article\"")));
    var after = Encoder.encode(LegalTexts.parse(text("kind=\"article\" eId=\"art_1\"")));

    assertThat(after).isEqualTo(before);
  }

  @Test
  void keepsTheEIdsAndNoteReferencesAsTheConverterWroteThem() {
    var xml =
        """
        <legalText %s><body>
          <p eId="p_1"><t v="See"/><noteRef href="#n_1" marker="1"/></p>
          <note eId="n_1" marker="1"><p><t v="A note."/></p></note>
        </body></legalText>
        """
            .formatted(NS);

    var json = new String(Encoder.encode(LegalTexts.parse(xml)), StandardCharsets.UTF_8);

    assertThat(json)
        .isEqualTo(
            "[\"legalText\",{},[\"body\",{},"
                + "[\"p\",{\"eId\":\"p_1\"},[\"t\",{\"v\":\"See\"}],"
                + "[\"noteRef\",{\"href\":\"#n_1\",\"marker\":\"1\"}]],"
                + "[\"note\",{\"eId\":\"n_1\",\"marker\":\"1\"},[\"p\",{},[\"t\",{\"v\":\"A"
                + " note.\"}]]]]]");
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

  @Test
  void refusesJsonThatIsNotALegalText() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Decoder.element("[\"p\",[]]".getBytes(StandardCharsets.UTF_8)))
        .withMessageContaining("Not a legal text's JSON");
  }

  private static String text(String attributes) {
    return """
    <legalText %s><body><unit %s><heading><t v="Scope"/></heading></unit></body></legalText>
    """
        .formatted(NS, attributes);
  }
}
