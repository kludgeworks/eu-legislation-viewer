// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build.rules;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.ndjson.Json;
import io.cucumber.messages.types.DataTable;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.Pickle;
import io.cucumber.messages.types.Rule;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.Step;
import io.cucumber.messages.types.TableRow;
import io.cucumber.messages.types.Tag;
import io.cucumber.messages.types.TestCase;
import io.cucumber.messages.types.TestCaseStarted;
import io.cucumber.messages.types.TestStepResultStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Writes the business rules as wiki pages, one page for each feature, from the runs of the rules
 * suites (Cucumber's messages, {@link RulesMessages}).
 *
 * <p>Each page gives the rules of its feature, and each example with its result in the run. If the
 * features of the last release are given, each rule says whether it is new or changed since then,
 * and a changed rule shows the difference. A page that has no change says that it is as released.
 *
 * <p>It writes no page if a rule has no example, if an example is not in a rule, if a rule has no
 * name (such as tags-of-an-occurrence), or if two rules of a feature have the same name.
 *
 * <p>A page shows a rule's background, its examples' doc strings, and the tags of the rule and of
 * each example. The tag {@code @known-bug} marks an example that shows a filed bug. With the tag of
 * the issue's number, such as {@code @ISS-270}, the page links to the issue: Notion opens a page by
 * its prefixed unique id at {@code https://www.notion.so/ISS-270}.
 *
 * <p>Under each rule, a page names the methods that apply it ({@code @BusinessRule}, saved beside
 * each run in {@value RulesApplied#FILE}), each linked to its source on GitHub at the commit, or on
 * main. A rule whose examples are all known bugs has no such method yet.
 *
 * <pre>{@code
 * RulesWiki --out DIR [--released DIR --version v2026.11.17] [--commit SHA] RUN.ndjson...
 * }</pre>
 */
public final class RulesWiki {
  /**
   * The title of a rule: its name, in kebab case, then its title, for example {@code
   * tags-else-cellar · The tags, else Cellar, name the acts}. A name is unique in its feature.
   */
  private static final Pattern NAMED = Pattern.compile("^([a-z0-9]+(?:-[a-z0-9]+)*) · (.+)$");

  /** The tag of an example that shows a filed bug. */
  static final String KNOWN_BUG = "@known-bug";

  /** The tag of an issue's number in the Issues database: {@code @ISS-270}. */
  private static final Pattern ISSUE = Pattern.compile("^@(ISS-\\d+)$");

  /** Where Notion opens a page by its prefixed unique id. */
  private static final String NOTION = "https://www.notion.so/";

  /** The repository's files on GitHub, followed by a commit or a branch. */
  static final String GITHUB = "https://github.com/kludgeworks/eu-legislation-viewer/blob/";

  /** The root of the viewer's sources in the repository. */
  static final String SOURCES = "viewer/src/main/java/";

  private RulesWiki() {}

  /**
   * A rule as the run or the release has it: its name, its title, its feature and group, and its
   * text.
   */
  record Text(
      String id, String title, String feature, String group, Rule rule, List<String> lines) {}

  /** Returns the name and the title of a rule, or empty if its title is not in that form. */
  private static Optional<java.util.regex.MatchResult> title(Rule rule) {
    var named = NAMED.matcher(rule.getName());
    return named.matches() ? Optional.of(named.toMatchResult()) : Optional.empty();
  }

  public static void main(String[] args) throws IOException {
    Path out = null;
    Path released = null;
    String version = null;
    String commit = null;
    var runs = new ArrayList<Path>();
    for (var i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--out" -> out = Path.of(args[++i]);
        case "--released" -> released = Path.of(args[++i]);
        case "--version" -> version = args[++i];
        case "--commit" -> commit = args[++i];
        default -> runs.add(Path.of(args[i]));
      }
    }
    if (out == null || runs.isEmpty() || (released != null && version == null)) {
      System.err.println(
          "Usage: RulesWiki --out DIR [--released DIR --version VERSION] [--commit SHA]"
              + " RUN.ndjson...");
      System.exit(2);
      return;
    }
    var run = Run.read(runs);
    var applied = new ArrayList<RulesApplied.Applied>();
    for (var each : runs) {
      var folder = each.toAbsolutePath().getParent();
      if (folder != null) {
        applied.addAll(RulesApplied.read(folder.resolve(RulesApplied.FILE)));
      }
    }
    var byRule = RulesApplied.byRule(applied);
    var errors = check(run.documents());
    if (!errors.isEmpty()) {
      errors.forEach(error -> System.err.println("error: " + error));
      System.exit(1);
      return;
    }
    var before = released == null ? null : rules(features(released));
    Files.createDirectories(out);
    var sidebar = new StringBuilder("**Business rules**\n\n");
    for (var document : run.documents()) {
      var feature = feature(document);
      var page = page(document, run, before, version, commit, byRule);
      var name = slug(feature.getName());
      Files.writeString(out.resolve(name + ".md"), page.markdown());
      sidebar
          .append("- [[")
          .append(feature.getName())
          .append('|')
          .append(name)
          .append("]]")
          .append(page.changes() > 0 ? " · draft" : "")
          .append('\n');
    }
    Files.writeString(out.resolve("_Sidebar.md"), sidebar.toString());
  }

  /**
   * The documents of the runs, and the result of each example: the worst result of its steps. An
   * example is known by its feature and its line ({@link #key}), not by Cucumber's id, because each
   * run gives a feature new ids: two runs of one feature, such as the rules and the known bugs,
   * then agree.
   */
  record Run(List<GherkinDocument> documents, Map<String, TestStepResultStatus> results) {
    static Run read(List<Path> files) throws IOException {
      var json = Json.instance().orElseThrow(() -> new IllegalStateException("No JSON provider"));
      var deserializer = json.deserializer(Envelope.class);
      var documents = new TreeMap<String, GherkinDocument>();
      var pickles = new HashMap<String, Pickle>();
      var cases = new HashMap<String, TestCase>();
      var started = new HashMap<String, TestCaseStarted>();
      var results = new HashMap<String, TestStepResultStatus>();
      for (var file : files) {
        var keys = new HashMap<String, String>();
        try (var lines = Files.lines(file)) {
          for (var line : (Iterable<String>) lines::iterator) {
            if (line.isBlank()) {
              continue;
            }
            var envelope = deserializer.readValue(line);
            envelope
                .getGherkinDocument()
                .ifPresent(
                    doc -> {
                      documents.put(doc.getUri().orElse(""), doc);
                      keys.putAll(keys(doc));
                    });
            envelope.getPickle().ifPresent(pickle -> pickles.put(pickle.getId(), pickle));
            envelope.getTestCase().ifPresent(test -> cases.put(test.getId(), test));
            envelope.getTestCaseStarted().ifPresent(start -> started.put(start.getId(), start));
            envelope
                .getTestStepFinished()
                .ifPresent(
                    step -> {
                      var start = started.get(step.getTestCaseStartedId());
                      var test = start == null ? null : cases.get(start.getTestCaseId());
                      var pickle = test == null ? null : pickles.get(test.getPickleId());
                      var key =
                          pickle == null || pickle.getAstNodeIds().isEmpty()
                              ? null
                              : keys.get(pickle.getAstNodeIds().getFirst());
                      if (key != null) {
                        results.merge(key, step.getTestStepResult().getStatus(), Run::worse);
                      }
                    });
          }
        }
      }
      return new Run(List.copyOf(documents.values()), results);
    }

    /** Returns the key of each example of {@code document}, by Cucumber's id of the example. */
    private static Map<String, String> keys(GherkinDocument document) {
      var keys = new HashMap<String, String>();
      var uri = document.getUri().orElse("");
      document
          .getFeature()
          .ifPresent(
              feature ->
                  feature.getChildren().stream()
                      .flatMap(child -> child.getRule().stream())
                      .flatMap(rule -> rule.getChildren().stream())
                      .flatMap(child -> child.getScenario().stream())
                      .forEach(scenario -> keys.put(scenario.getId(), key(uri, scenario))));
      return keys;
    }

    /** Returns how an example is known across runs: its feature and its line. */
    static String key(String uri, Scenario scenario) {
      return uri + ":" + scenario.getLocation().getLine();
    }

    private static TestStepResultStatus worse(TestStepResultStatus a, TestStepResultStatus b) {
      return a == TestStepResultStatus.PASSED ? b : a;
    }
  }

  /**
   * Each rule has a name and an example, each example is in a rule, and no two rules of a feature
   * have the same name.
   */
  public static List<String> check(List<GherkinDocument> documents) {
    var errors = new ArrayList<String>();
    var seen = new HashMap<String, String>();
    for (var document : documents) {
      var uri = document.getUri().orElse("?");
      var feature = document.getFeature().orElse(null);
      if (feature == null) {
        errors.add(uri + ": no feature");
        continue;
      }
      for (var child : feature.getChildren()) {
        child
            .getScenario()
            .ifPresent(
                scenario ->
                    errors.add(uri + ": the example \"" + scenario.getName() + "\" is in no rule"));
        child
            .getRule()
            .ifPresent(
                rule -> {
                  var title = title(rule);
                  if (title.isEmpty()) {
                    errors.add(
                        uri
                            + ": the rule \""
                            + rule.getName()
                            + "\" has no name (name · title, the name in kebab case)");
                    return;
                  }
                  var id = title.get().group(1);
                  var before =
                      seen.putIfAbsent(RulesApplied.key(RulesApplied.group(document), id), uri);
                  if (before != null) {
                    errors.add(uri + ": " + id + " is also in " + before);
                  }
                  if (rule.getChildren().stream()
                      .noneMatch(each -> each.getScenario().isPresent())) {
                    errors.add(uri + ": " + id + " has no example");
                  }
                });
      }
    }
    return errors;
  }

  /** The features in {@code folder} and the folders in it. */
  public static List<GherkinDocument> features(Path folder) throws IOException {
    var parser = GherkinParser.builder().includeSource(false).includePickles(false).build();
    var documents = new ArrayList<GherkinDocument>();
    try (var files = Files.walk(folder)) {
      for (var file :
          files.filter(each -> each.toString().endsWith(".feature")).sorted().toList()) {
        try (var envelopes = parser.parse(file)) {
          for (var envelope : (Iterable<Envelope>) envelopes::iterator) {
            envelope.getGherkinDocument().ifPresent(documents::add);
            envelope
                .getParseError()
                .ifPresent(
                    error -> {
                      throw new IllegalStateException(file + ": " + error.getMessage());
                    });
          }
        }
      }
    }
    return documents;
  }

  /**
   * The rules of the documents by their group and name ({@link RulesApplied#key}), in the order in
   * which they stand.
   */
  static Map<String, Text> rules(List<GherkinDocument> documents) {
    var rules = new LinkedHashMap<String, Text>();
    for (var document : documents) {
      var feature = document.getFeature().orElse(null);
      if (feature == null) {
        continue;
      }
      for (var child : feature.getChildren()) {
        child
            .getRule()
            .ifPresent(
                rule ->
                    title(rule)
                        .ifPresent(
                            title ->
                                rules.put(
                                    RulesApplied.key(RulesApplied.group(document), title.group(1)),
                                    new Text(
                                        title.group(1),
                                        title.group(2),
                                        feature.getName(),
                                        RulesApplied.group(document),
                                        rule,
                                        lines(rule)))));
      }
    }
    return rules;
  }

  /** The text of a rule, line by line, to compare two versions of it. */
  static List<String> lines(Rule rule) {
    var lines = new ArrayList<String>();
    tagLine(rule.getTags()).ifPresent(lines::add);
    lines.add("Rule: " + rule.getName());
    // The description as one paragraph: a change of where its lines break is not a change
    var description = paragraph(rule.getDescription());
    if (!description.isEmpty()) {
      lines.add(description);
    }
    for (var child : rule.getChildren()) {
      child
          .getBackground()
          .ifPresent(
              background -> {
                lines.add("");
                lines.add(background.getKeyword() + ":");
                steps(background.getSteps(), lines);
              });
      child
          .getScenario()
          .ifPresent(
              scenario -> {
                lines.add("");
                tagLine(scenario.getTags()).ifPresent(lines::add);
                lines.add(scenario.getKeyword() + ": " + scenario.getName());
                steps(scenario.getSteps(), lines);
              });
    }
    return lines;
  }

  private static void steps(List<Step> steps, List<String> lines) {
    for (var step : steps) {
      lines.add("  " + step.getKeyword().strip() + " " + step.getText());
      step.getDataTable().ifPresent(table -> rows(table).forEach(lines::add));
      step.getDocString()
          .ifPresent(
              doc -> {
                lines.add("    \"\"\"");
                doc.getContent().lines().map(line -> "    " + line).forEach(lines::add);
                lines.add("    \"\"\"");
              });
    }
  }

  private static Optional<String> tagLine(List<Tag> tags) {
    return tags.isEmpty()
        ? Optional.empty()
        : Optional.of(tags.stream().map(Tag::getName).collect(Collectors.joining(" ")));
  }

  private static Stream<String> rows(DataTable table) {
    return table.getRows().stream()
        .map(
            row ->
                "    | "
                    + row.getCells().stream()
                        .map(cell -> cell.getValue())
                        .collect(Collectors.joining(" | "))
                    + " |");
  }

  /** A page and the number of its rules that changed since the release. */
  record Page(String markdown, int changes) {}

  static Page page(
      GherkinDocument document,
      Run run,
      @Nullable Map<String, Text> before,
      @Nullable String version,
      @Nullable String commit,
      Map<String, List<RulesApplied.Applied>> applied) {
    var feature = feature(document);
    var now = rules(List.of(document));
    var md = new StringBuilder(4096);
    md.append("# ").append(feature.getName()).append("\n\n");
    var body = new StringBuilder(4096);
    var added = 0;
    var changed = 0;
    for (var text : now.values()) {
      var old = before == null ? null : before.get(RulesApplied.key(text.group(), text.id()));
      String state;
      if (before == null) {
        state = "";
      } else if (old == null) {
        state = " `New`";
        added++;
      } else if (!old.lines().equals(text.lines())) {
        state = " `Changed`";
        changed++;
      } else {
        state = "";
      }
      body.append("## ").append(text.id()).append(" · ").append(text.title()).append(state);
      body.append("\n\n");
      tags(body, text.rule().getTags());
      body.append(paragraph(text.rule().getDescription())).append("\n\n");
      appliedBy(
          body,
          applied.getOrDefault(RulesApplied.key(text.group(), text.id()), List.of()),
          text.rule(),
          commit);
      for (var child : text.rule().getChildren()) {
        child
            .getBackground()
            .ifPresent(
                background -> {
                  body.append("**").append(background.getKeyword()).append("**\n\n");
                  steps(body, background.getSteps());
                  body.append('\n');
                });
        child
            .getScenario()
            .ifPresent(scenario -> example(body, scenario, run, document.getUri().orElse("")));
      }
      if (old != null && !old.lines().equals(text.lines())) {
        body.append("**What changed since ").append(version).append("**\n\n```diff\n");
        var patch = DiffUtils.diff(old.lines(), text.lines());
        UnifiedDiffUtils.generateUnifiedDiff(
                Objects.requireNonNull(version), "main", old.lines(), patch, 3)
            .forEach(line -> body.append(line).append('\n'));
        body.append("```\n\n");
      }
    }
    var removed =
        before == null
            ? List.<Text>of()
            : before.values().stream()
                .filter(old -> old.feature().equals(feature.getName()))
                .filter(old -> !now.containsKey(RulesApplied.key(old.group(), old.id())))
                .sorted(Comparator.comparing(Text::id))
                .toList();
    if (!removed.isEmpty()) {
      body.append("## Removed since ").append(version).append("\n\n");
      removed.forEach(
          old -> body.append("- ").append(old.id()).append(" · ").append(old.title()).append('\n'));
      body.append('\n');
    }
    var changes = added + changed + removed.size();
    if (before == null) {
      md.append("> **Draft.** No release has these rules yet.\n\n");
    } else if (changes == 0) {
      md.append("> As released in ").append(version).append(".\n\n");
    } else {
      md.append("> **Draft.** These rules changed on main since ")
          .append(version)
          .append(": ")
          .append(count(added, "new"))
          .append(", ")
          .append(count(changed, "changed"))
          .append(", ")
          .append(count(removed.size(), "removed"))
          .append(".\n\n");
    }
    md.append(paragraph(feature.getDescription())).append("\n\n").append(body);
    md.append("---\n\n_Generated from `")
        .append(document.getUri().orElse("?").replaceFirst("^classpath:", ""))
        .append('`')
        .append(commit == null ? "" : " at " + commit)
        .append(
            ", with the result of each example in that run. Do not edit this page: change the"
                + " feature._\n");
    return new Page(md.toString(), changes);
  }

  /**
   * Writes the methods that apply a rule, each linked to its source. A rule whose examples are all
   * known bugs says that no method applies it yet.
   */
  private static void appliedBy(
      StringBuilder md, List<RulesApplied.Applied> applied, Rule rule, @Nullable String commit) {
    if (applied.isEmpty()) {
      if (RulesApplied.onlyKnownBugs(rule)) {
        md.append("_Applied by no method yet: its examples are known bugs._\n\n");
      }
      return;
    }
    var ref = commit == null ? "main" : commit;
    md.append("_Applied by_ ")
        .append(
            String.join(
                ", ",
                applied.stream()
                    .map(
                        each ->
                            "[`"
                                + each.method()
                                + "`]("
                                + GITHUB
                                + ref
                                + "/"
                                + SOURCES
                                + each.source()
                                + ")")
                    .toList()))
        .append("\n\n");
  }

  private static void example(StringBuilder md, Scenario scenario, Run run, String uri) {
    var result = run.results().get(Run.key(uri, scenario));
    md.append("**")
        .append(scenario.getKeyword())
        .append(": ")
        .append(scenario.getName())
        .append("** ")
        .append(
            result == null
                ? "· not run"
                : result == TestStepResultStatus.PASSED
                    ? "✓"
                    : "✗ " + result.name().toLowerCase(java.util.Locale.ROOT))
        .append("\n\n");
    tags(md, scenario.getTags());
    steps(md, scenario.getSteps());
    md.append('\n');
  }

  private static void steps(StringBuilder md, List<Step> steps) {
    for (var step : steps) {
      md.append("- **").append(step.getKeyword().strip()).append("** ").append(step.getText());
      md.append('\n');
      step.getDataTable().ifPresent(table -> table(md, table.getRows()));
      step.getDocString()
          .ifPresent(
              doc -> {
                md.append('\n');
                doc.getContent()
                    .lines()
                    .forEach(line -> md.append("  > ").append(line).append('\n'));
                md.append('\n');
              });
    }
  }

  /**
   * Writes the tags, if there are any: a known bug as a warning, with a link to its issue, and any
   * other tag as it is.
   */
  private static void tags(StringBuilder md, List<Tag> tags) {
    if (tags.isEmpty()) {
      return;
    }
    var names = tags.stream().map(Tag::getName).toList();
    var issue =
        names.stream()
            .map(ISSUE::matcher)
            .filter(java.util.regex.Matcher::matches)
            .map(matched -> matched.group(1))
            .map(number -> "[" + number + "](" + NOTION + number + ")")
            .findFirst();
    if (names.contains(KNOWN_BUG)) {
      md.append("> ⚠ **Known bug.** This example shows a bug that is filed")
          .append(issue.map(link -> ": " + link).orElse(""))
          .append(".\n\n");
    }
    var others =
        names.stream()
            .filter(name -> !name.equals(KNOWN_BUG) && !ISSUE.matcher(name).matches())
            .map(name -> "`" + name + "`")
            .toList();
    if (!others.isEmpty()) {
      md.append("Tags: ").append(String.join(" ", others)).append("\n\n");
    }
  }

  private static void table(StringBuilder md, List<TableRow> rows) {
    md.append('\n');
    for (var i = 0; i < rows.size(); i++) {
      md.append("  |");
      for (var cell : rows.get(i).getCells()) {
        md.append(' ').append(cell.getValue().replace("|", "\\|")).append(" |");
      }
      md.append('\n');
      if (i == 0) {
        md.append("  |").append(" --- |".repeat(rows.get(i).getCells().size())).append('\n');
      }
    }
    md.append('\n');
  }

  private static Feature feature(GherkinDocument document) {
    return document.getFeature().orElseThrow();
  }

  private static String paragraph(String description) {
    return description.lines().map(String::strip).collect(Collectors.joining(" ")).strip();
  }

  private static String count(int n, String what) {
    return n + " " + what;
  }

  /** The name of a feature's page: its words joined by hyphens, as the wiki names pages. */
  static String slug(String name) {
    return name.replaceAll("[^\\p{L}\\p{N} ]", "").strip().replaceAll("\\s+", "-");
  }
}
