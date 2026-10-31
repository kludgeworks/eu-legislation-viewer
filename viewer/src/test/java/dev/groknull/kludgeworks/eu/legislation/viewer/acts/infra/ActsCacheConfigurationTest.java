// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.infra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import org.jmolecules.jackson3.JMoleculesModule;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Acts as the cache keeps them: JSON, read back as the same act. */
class ActsCacheConfigurationTest {
  static final ActIdentifier TFEU = new ActIdentifier("12016E/TXT");

  final JsonMapper json = JsonMapper.builder().addModule(new JMoleculesModule()).build();

  @Test
  void anActIsReadBackAsItWasKept() {
    var act =
        new Act(
            TFEU,
            Eli.treaty("tfeu_2016"),
            new ResourceType("TREATY"),
            LocalDate.of(2016, 6, 7),
            true,
            null,
            new Timeline(
                List.of(
                    Version.original(
                        LocalDate.of(2016, 6, 7),
                        TFEU,
                        EnumSet.allOf(Language.class),
                        Instant.parse("2026-07-10T01:02:19.329Z")),
                    new Version(
                        Version.Kind.CONSOLIDATED,
                        LocalDate.of(2025, 3, 15),
                        "02016E/TXT-20250315",
                        true,
                        List.of(new ActIdentifier("32025D0001")),
                        EnumSet.allOf(Language.class),
                        Instant.parse("2026-07-10T01:02:19.329Z")))));
    var serializer = ActsCacheConfiguration.serializer(json);

    var kept = serializer.serialize(act);

    assertThat(serializer.deserialize(kept)).isEqualTo(act);
  }
}
