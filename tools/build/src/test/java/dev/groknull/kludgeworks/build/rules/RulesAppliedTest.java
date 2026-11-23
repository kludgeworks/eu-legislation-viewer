// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.GherkinDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

class RulesAppliedTest {
  private static final String ATTRIBUTION =
      "dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attribution";

  private static final String WHO = "WHO_MADE_A_CHANGE";

  @Test
  void findsARuleWithoutAMethodAMarkWithoutARuleAndAnOldRevision() {
    var documents =
        documents(
            """
            Feature: Who made a change
              Rule: amendments-around-and-inside · Amendments of the occurrences around and inside
                Example: One
                  Then it is told

              Rule: a-corrigendum-corrects · A corrigendum corrects
                Example: Two
                  Then it is told

              @revision-2
              Rule: tags-else-cellar · The tags, else Cellar, name the acts
                Example: Three
                  Then it is told

              Rule: act-changes-only-what-the-law-has · An act changes only what the law has
                @known-bug @ISS-270
                Example: Four
                  Then it is told
            """);
    var scan =
        new RulesApplied.Scan(
            List.of(
                new RulesApplied.Applied(
                    WHO, "amendments-around-and-inside", 1, ATTRIBUTION, "amendmentsOf"),
                new RulesApplied.Applied(WHO, "tags-else-cellar", 1, ATTRIBUTION, "of"),
                new RulesApplied.Applied(WHO, "no-such-rule", 1, ATTRIBUTION + "$Step", "")),
            List.of());

    assertThat(RulesApplied.check(documents, scan))
        .containsExactly(
            "a-corrigendum-corrects (Who made a change) has no method that applies it: put"
                + " @BusinessRule(group = RuleGroup.WHO_MADE_A_CHANGE, name ="
                + " \"a-corrigendum-corrects\") on the method that does",
            "tags-else-cellar is revision 2, but Attribution.of applies revision 1: the rule"
                + " changed meaning: check Attribution.of against it and set revision = 2",
            "@BusinessRule names WHO_MADE_A_CHANGE/no-such-rule on Attribution.Step, but no"
                + " feature has that rule");
  }

  @Test
  void savesTheMarksAndReadsThemBack() throws IOException {
    var file = Files.createTempFile("rules", ".tsv");
    var applied =
        List.of(
            new RulesApplied.Applied(
                WHO, "amendments-around-and-inside", 1, ATTRIBUTION, "amendmentsOf"),
            new RulesApplied.Applied(
                "THE_CHANGES_OF_ONE_ACT", "one-act-is-one-step", 3, ATTRIBUTION + "$Step", ""));

    RulesApplied.write(file, applied);

    assertThat(RulesApplied.read(file)).isEqualTo(applied);
    assertThat(applied.get(1).method()).isEqualTo("Attribution.Step");
    assertThat(applied.get(1).rule()).isEqualTo("THE_CHANGES_OF_ONE_ACT/one-act-is-one-step");
    assertThat(applied.get(1).source())
        .isEqualTo("dev/groknull/kludgeworks/eu/legislation/viewer/comparison/Attribution.java");
  }

  private static List<GherkinDocument> documents(String feature) {
    var parser = GherkinParser.builder().includeSource(false).includePickles(false).build();
    try (var envelopes =
        parser.parse("who-made-a-change.feature", feature.getBytes(StandardCharsets.UTF_8))) {
      return envelopes.flatMap(each -> each.getGherkinDocument().stream()).toList();
    }
  }
}
