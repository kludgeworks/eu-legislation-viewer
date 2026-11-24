// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * Runs the examples of a module's rules that are tagged {@code @known-bug}, and checks that each
 * still fails. Such an example states what the domain should do, and a filed bug, named by the tag
 * of its number (such as {@code @ISS-270}), makes it fail today. The module's rules run leaves them
 * out, so it stays green.
 *
 * <p>An example that passes means that its bug is fixed: it is a problem, which says to remove the
 * tag and close the issue. An example that fails without a failed expectation, such as a step that
 * no glue defines, is a problem too: it does not show the bug. The run is saved as Cucumber's
 * messages ({@link RulesMessages}), so the wiki shows these examples with their result.
 */
public final class KnownBugs {
  private static final String KNOWN_BUG = "known-bug";
  private static final String ISSUE = "ISS-";
  private static final String NOTION = "https://www.notion.so/";

  private KnownBugs() {}

  /**
   * What the run found.
   *
   * @param count how many examples are known bugs
   * @param problems the examples that pass, that name no issue, or that fail on no expectation
   * @param failing each example that fails as its bug makes it, with its issue and its failure
   */
  public record Result(int count, List<String> problems, List<String> failing) {
    public Result {
      problems = List.copyOf(problems);
      failing = List.copyOf(failing);
    }
  }

  /** The result of one example of the run. */
  private record Ran(String name, List<String> tags, TestExecutionResult result) {
    Optional<String> issue() {
      return tags.stream()
          .filter(tag -> tag.startsWith(ISSUE))
          .map(tag -> tag + " (" + NOTION + tag + ")")
          .findFirst();
    }
  }

  /**
   * Runs the known bugs of the features in the package {@code features} (for example {@code
   * rules.catalogue}) with the steps of the package {@code glue}.
   */
  public static Result run(String features, String glue) {
    var ran = new ArrayList<Ran>();
    var request =
        LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectPackage(features))
            .filters(EngineFilter.includeEngines("cucumber"))
            .configurationParameter("cucumber.glue", glue)
            .configurationParameter("cucumber.filter.tags", "@" + KNOWN_BUG)
            .configurationParameter("cucumber.plugin", RulesMessages.class.getName())
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

    var problems = new ArrayList<String>();
    var failing = new ArrayList<String>();
    for (var each : ran) {
      var issue = each.issue();
      if (issue.isEmpty()) {
        problems.add(each.name() + " has no tag @ISS-<n> that names its issue");
      }
      var close = issue.map(url -> "close " + url).orElse("close its issue");
      var status = each.result().getStatus();
      if (status == TestExecutionResult.Status.SUCCESSFUL) {
        problems.add(each.name() + " passes: the bug is fixed — remove @known-bug and " + close);
      } else if (status == TestExecutionResult.Status.FAILED
          && each.result().getThrowable().orElse(null) instanceof AssertionError failed) {
        failing.add(
            "%s fails, as its bug makes it, %s:%n%s"
                .formatted(
                    each.name(),
                    close.substring("close ".length()),
                    Objects.requireNonNullElse(failed.getMessage(), "").strip()));
      } else {
        problems.add(
            each.name()
                + " fails, but not on an expectation: "
                + each.result().getThrowable().map(Throwable::toString).orElseGet(status::name));
      }
    }
    return new Result(ran.size(), problems, failing);
  }
}
