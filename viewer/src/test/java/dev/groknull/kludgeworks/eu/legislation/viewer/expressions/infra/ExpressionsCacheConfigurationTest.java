// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import org.jmolecules.jackson3.JMoleculesModule;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** A document's languages as they are kept: as JSON, by Boot's Jackson with jMolecules' module. */
class ExpressionsCacheConfigurationTest {
  static final JsonMapper JSON = JsonMapper.builder().addModule(new JMoleculesModule()).build();

  @Test
  void languagesAreKeptAsTheirCodesAndReadBack() {
    var serializer = ExpressionsCacheConfiguration.serializer(JSON);
    var languages = EnumSet.of(Language.DEU, Language.ENG);

    var kept = serializer.serialize(languages);

    assertThat(new String(kept, StandardCharsets.UTF_8)).isEqualTo("[\"DEU\",\"ENG\"]");
    assertThat(serializer.deserialize(kept)).isEqualTo(languages);
  }
}
