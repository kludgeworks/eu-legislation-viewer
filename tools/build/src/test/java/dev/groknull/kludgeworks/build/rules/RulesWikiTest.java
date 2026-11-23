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
  void refusesARuleWithoutANameOrAnExampleAndAnExampleOutsideARule() {
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

                      Rule: who-deleted-an-occurrence · Who deleted an occurrence

                      Rule: R-OLD-01 A rule as it was before rules had names
                        Example: Old
                          Given a text
                    """)));

    assertThat(errors)
        .containsExactly(
            "tags.feature: the example \"Outside\" is in no rule",
            "tags.feature: the rule \"The tags of an occurrence\" has no name (name · title, the"
                + " name in kebab case)",
            "tags.feature: who-deleted-an-occurrence has no example",
            "tags.feature: the rule \"R-OLD-01 A rule as it was before rules had names\" has no"
                + " name (name · title, the name in kebab case)");
  }

  @Test
  void refusesTwoRulesWithOneIdentifier() {
    var errors =
        RulesWiki.check(
            List.of(
                document(
                    """
                    Feature: Tags
                      Rule: tags-of-an-occurrence · The tags of an occurrence
                        Example: One
                          Given a text
                      Rule: tags-of-an-occurrence · The tags of an occurrence, again
                        Example: Two
                          Given a text
                    """)));

    assertThat(errors)
        .containsExactly("tags.feature: tags-of-an-occurrence is also in tags.feature");
  }

  @Test
  void findsNoChangeWhereOnlyTheLinesOfADescriptionBreakElsewhere() {
    var before =
        RulesWiki.rules(
            List.of(
                document(
                    """
                    Feature: Tags
                      Rule: tags-of-an-occurrence · The tags of an occurrence
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
                      Rule: tags-of-an-occurrence · The tags of an occurrence
                        An occurrence's tags are its own, each once and in order.

                        Example: One
                          Given a text
                    """)));

    assertThat(after.get("TAGS/tags-of-an-occurrence"))
        .extracting(RulesWiki.Text::lines)
        .isEqualTo(Objects.requireNonNull(before.get("TAGS/tags-of-an-occurrence")).lines());
  }

  @Test
  void showsABackgroundADocStringAndAKnownBugWithALinkToItsIssue() {
    var document =
        document(
            """
            Feature: Tags
              Rule: tags-of-an-occurrence · The tags of an occurrence
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
        RulesWiki.page(
            document, new RulesWiki.Run(List.of(document), Map.of()), null, null, null, Map.of());

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
  void namesTheMethodsThatApplyARuleLinkedToTheirSource() {
    var document =
        RulesWikiTest.document(
            """
            Feature: Who made a change
              Rule: amendments-around-and-inside · Amendments of the occurrences around and inside
                Example: A point takes the amendment of the point around it
                  Then the steps are told

              Rule: act-changes-only-what-the-law-has · An act changes only what the law has
                @known-bug @ISS-270
                Example: A point that an act deleted
                  Then the steps are told
            """,
            "who-made-a-change.feature");
    var applied =
        new RulesApplied.Applied(
            "WHO_MADE_A_CHANGE",
            "amendments-around-and-inside",
            1,
            "dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attribution",
            "amendmentsOf");

    var page =
        RulesWiki.page(
            document,
            new RulesWiki.Run(List.of(document), Map.of()),
            null,
            null,
            "2c55d943",
            Map.of(applied.rule(), List.of(applied)));

    assertThat(page.markdown())
        .contains(
            "## amendments-around-and-inside · Amendments of the occurrences around and inside")
        .contains(
            "_Applied by_ [`Attribution.amendmentsOf`](https://github.com/kludgeworks/"
                + "eu-legislation-viewer/blob/2c55d943/viewer/src/main/java/dev/groknull/kludgeworks/"
                + "eu/legislation/viewer/comparison/Attribution.java)")
        .contains("_Applied by no method yet: its examples are known bugs._");
  }

  @Test
  void findsAChangeInADocStringOrATag() {
    var before =
        RulesWiki.rules(
            List.of(
                document(
                    """
                    Feature: Tags
                      Rule: tags-of-an-occurrence · The tags of an occurrence
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
                      Rule: tags-of-an-occurrence · The tags of an occurrence
                        @known-bug
                        Example: One
                          Then it deletes
                            \"\"\"
                            two
                            \"\"\"
                    """)));

    assertThat(Objects.requireNonNull(after.get("TAGS/tags-of-an-occurrence")).lines())
        .contains("@known-bug", "    two")
        .isNotEqualTo(Objects.requireNonNull(before.get("TAGS/tags-of-an-occurrence")).lines());
  }

  private static GherkinDocument document(String feature) {
    return document(feature, "tags.feature");
  }

  private static GherkinDocument document(String feature, String file) {
    var parser = GherkinParser.builder().includeSource(false).includePickles(false).build();
    return parser
        .parse(file, feature.getBytes(StandardCharsets.UTF_8))
        .flatMap(envelope -> envelope.getGherkinDocument().stream())
        .findFirst()
        .orElseThrow();
  }
}
