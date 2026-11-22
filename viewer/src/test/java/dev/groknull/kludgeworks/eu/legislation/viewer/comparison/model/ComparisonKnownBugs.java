// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * Runs the comparison's examples tagged {@code @known-bug}, and checks that each still fails. Such
 * an example states what the domain should do, and a filed bug, named by the tag of its number
 * (such as {@code @ISS-270}), makes it fail today. The rules run leaves them out (ComparisonRules),
 * so it stays green.
 *
 * <p>An example that passes means that its bug is fixed: this test then fails, and says to remove
 * the tag and close the issue. An example that fails without a failed expectation, such as a step
 * that no glue defines, fails this test too: it does not show the bug. The run is saved as
 * Cucumber's messages, so the wiki shows these examples with their result.
 */
class ComparisonKnownBugs {
  private static final String KNOWN_BUG = "known-bug";
  private static final String ISSUE = "ISS-";
  private static final String NOTION = "https://www.notion.so/";

  /** The result of one example of the run. */
  private record Ran(String name, List<String> tags, TestExecutionResult result) {
    Optional<String> issue() {
      return tags.stream()
          .filter(tag -> tag.startsWith(ISSUE))
          .map(tag -> tag + " (" + NOTION + tag + ")")
          .findFirst();
    }
  }

  @Test
  void eachKnownBugStillFails() {
    var ran = new ArrayList<Ran>();
    var request =
        LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectPackage("rules.comparison"))
            .filters(EngineFilter.includeEngines("cucumber"))
            .configurationParameter(
                GLUE_PROPERTY_NAME,
                "dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model")
            .configurationParameter(FILTER_TAGS_PROPERTY_NAME, "@" + KNOWN_BUG)
            .configurationParameter(
                PLUGIN_PROPERTY_NAME, "dev.groknull.kludgeworks.build.rules.RulesMessages")
            .build();
    LauncherFactory.create()
        .execute(
            request,
            new TestExecutionListener() {
              @Override
              public void executionFinished(TestIdentifier test, TestExecutionResult result) {
                if (test.isTest()) {
                  ran.add(
                      new Ran(
                          test.getDisplayName(),
                          test.getTags().stream().map(TestTag::getName).toList(),
                          result));
                }
              }
            });

    var errors = new ArrayList<String>();
    for (var each : ran) {
      var issue = each.issue();
      if (issue.isEmpty()) {
        errors.add(each.name() + " has no tag @ISS-<n> that names its issue");
      }
      var close = issue.map(url -> "close " + url).orElse("close its issue");
      var status = each.result().getStatus();
      if (status == TestExecutionResult.Status.SUCCESSFUL) {
        errors.add(each.name() + " passes: the bug is fixed — remove @known-bug and " + close);
      } else if (status == TestExecutionResult.Status.FAILED
          && each.result().getThrowable().orElse(null) instanceof AssertionError failed) {
        System.out.printf(
            "%s fails, as its bug makes it, %s:%n%s%n",
            each.name(),
            close.substring("close ".length()),
            java.util.Objects.requireNonNullElse(failed.getMessage(), "").strip());
      } else {
        errors.add(
            each.name()
                + " fails, but not on an expectation: "
                + each.result().getThrowable().map(Throwable::toString).orElseGet(status::name));
      }
    }
    assertThat(errors).as("the known bugs").isEmpty();
    System.out.printf("%d known bugs, each still failing%n", ran.size());
  }
}
