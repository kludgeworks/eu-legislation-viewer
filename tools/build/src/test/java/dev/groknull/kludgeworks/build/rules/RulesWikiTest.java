// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.GherkinDocument;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class RulesWikiTest {
  @Test
  void refusesARuleWithoutAnIdentifierOrAnExampleAndAnExampleOutsideARule() {
    var errors =
        RulesWiki.check(
            List.of(
                document(
                    """
                    Feature: Tags
                      Example: Outside
                        Given a text

                      Rule: The tags of an occurrence
                        Example: Mine
                          Given a text

                      Rule: R-WHO-03 Who deleted an occurrence
                    """)));

    assertThat(errors)
        .containsExactly(
            "tags.feature: the example \"Outside\" is in no rule",
            "tags.feature: the rule \"The tags of an occurrence\" has no identifier (R-XXX-nn)",
            "tags.feature: R-WHO-03 has no example");
  }

  @Test
  void refusesTwoRulesWithOneIdentifier() {
    var errors =
        RulesWiki.check(
            List.of(
                document(
                    """
                    Feature: Tags
                      Rule: R-WHO-02 The tags of an occurrence
                        Example: One
                          Given a text
                      Rule: R-WHO-02 The tags of an occurrence, again
                        Example: Two
                          Given a text
                    """)));

    assertThat(errors).containsExactly("tags.feature: R-WHO-02 is also in tags.feature");
  }

  @Test
  void findsNoChangeWhereOnlyTheLinesOfADescriptionBreakElsewhere() {
    var before =
        RulesWiki.rules(
            List.of(
                document(
                    """
                    Feature: Tags
                      Rule: R-WHO-02 The tags of an occurrence
                        An occurrence's tags are its own,
                        each once and in order.

                        Example: One
                          Given a text
                    """)));
    var after =
        RulesWiki.rules(
            List.of(
                document(
                    """
                    Feature: Tags
                      Rule: R-WHO-02 The tags of an occurrence
                        An occurrence's tags are its own, each once and in order.

                        Example: One
                          Given a text
                    """)));

    assertThat(after.get("R-WHO-02"))
        .extracting(RulesWiki.Text::lines)
        .isEqualTo(Objects.requireNonNull(before.get("R-WHO-02")).lines());
  }

  @Test
  void showsABackgroundADocStringAndAKnownBugWithALinkToItsIssue() {
    var document =
        document(
            """
            Feature: Tags
              Rule: R-WHO-02 The tags of an occurrence
                An occurrence's tags are its own.

                Background:
                  Given the comparison of the texts of 2015/1998 of 1 January 2026 and of 22 March 2026

                @known-bug @ISS-270
                Example: A repeal that reads as an amendment
                  When the reader reads the difference at the deleted point 6.5.5
                  Then it deletes
                    \"\"\"
                    The results of testing shall be recorded.
                    \"\"\"
                  But 2019/413 is not named
            """);

    var page =
        RulesWiki.page(document, new RulesWiki.Run(List.of(document), Map.of()), null, null, null);

    assertThat(page.markdown())
        .contains(
            "**Background**\n\n- **Given** the comparison of the texts of 2015/1998 of 1 January"
                + " 2026 and of 22 March 2026\n")
        .contains(
            "> ⚠ **Known bug.** This example shows a bug that is filed: [ISS-270]"
                + "(https://www.notion.so/ISS-270).")
        .contains("- **Then** it deletes\n\n  > The results of testing shall be recorded.\n")
        .contains("- **But** 2019/413 is not named\n")
        .doesNotContain("`@known-bug`")
        .doesNotContain("`@ISS-");
  }

  @Test
  void findsAChangeInADocStringOrATag() {
    var before =
        RulesWiki.rules(
            List.of(
                document(
                    """
                    Feature: Tags
                      Rule: R-WHO-02 The tags of an occurrence
                        Example: One
                          Then it deletes
                            \"\"\"
                            one
                            \"\"\"
                    """)));
    var after =
        RulesWiki.rules(
            List.of(
                document(
                    """
                    Feature: Tags
                      Rule: R-WHO-02 The tags of an occurrence
                        @known-bug
                        Example: One
                          Then it deletes
                            \"\"\"
                            two
                            \"\"\"
                    """)));

    assertThat(Objects.requireNonNull(after.get("R-WHO-02")).lines())
        .contains("@known-bug", "    two")
        .isNotEqualTo(Objects.requireNonNull(before.get("R-WHO-02")).lines());
  }

  private static GherkinDocument document(String feature) {
    var parser = GherkinParser.builder().includeSource(false).includePickles(false).build();
    return parser
        .parse("tags.feature", feature.getBytes(StandardCharsets.UTF_8))
        .flatMap(envelope -> envelope.getGherkinDocument().stream())
        .findFirst()
        .orElseThrow();
  }
}
