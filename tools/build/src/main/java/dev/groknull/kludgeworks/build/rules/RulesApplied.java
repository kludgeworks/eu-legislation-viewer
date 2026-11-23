// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build.rules;

import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.Rule;
import io.cucumber.messages.types.Tag;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

/**
 * Ties the business rules to the methods that apply them. A method of the viewer that applies a
 * rule has {@code @BusinessRule(group = ..., name = ..., revision = ...)}. The group is the rule's
 * feature, the name starts the rule's title ({@code Rule: tags-else-cellar · ...}), and the
 * revision is the rule's tag {@code @revision-n} (1 without it).
 *
 * <p>This class finds those marks on the classpath, checks them against the rules of the features
 * in both directions, and saves them for the wiki ({@value #FILE}, next to the run's messages). It
 * reads the mark by its name, so the tools do not depend on the viewer.
 */
public final class RulesApplied {
  /** The mark on a method that applies a rule. */
  static final String MARK = "dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule";

  /** The holder of the marks of a method that applies more than one rule. */
  static final String MARKS = "dev.groknull.kludgeworks.eu.legislation.rules.BusinessRules";

  /** The file, next to a run's messages, that gives the methods that apply each rule. */
  public static final String FILE = "rules-applied.tsv";

  /** The packages whose classes are read: the viewer's. */
  public static final String PACKAGE = "dev.groknull.kludgeworks.eu.legislation.";

  /** The tag of a rule's revision: {@code @revision-2}. */
  private static final Pattern REVISION = Pattern.compile("^@revision-(\\d+)$");

  private RulesApplied() {}

  /**
   * A method, a constructor or a type that applies a rule.
   *
   * @param group the rule's group, as the enum names it: {@code WHO_MADE_A_CHANGE}
   * @param name the rule's name in its group: {@code tags-else-cellar}
   * @param revision the revision of the rule that it applies
   * @param type the binary name of the class, for example {@code ...comparison.Attribution}
   * @param member the method's name, or empty for the type itself
   */
  public record Applied(String group, String name, int revision, String type, String member) {

    /** Returns the rule that it applies: its group and its name. */
    public String rule() {
      return key(group, name);
    }

    /** Returns how the wiki names it: the class without its package, and the method. */
    public String method() {
      var simple = type.substring(type.lastIndexOf('.') + 1).replace('$', '.');
      return member.isEmpty() ? simple : simple + "." + member;
    }

    /** Returns the path of its source file, from the root of the sources. */
    public String source() {
      var outer = type.contains("$") ? type.substring(0, type.indexOf('$')) : type;
      return outer.replace('.', '/') + ".java";
    }
  }

  /**
   * What a scan found: the marks, and the classes that it could not read. A class that it could not
   * read can hide a mark, so a check that finds such a class fails.
   */
  public record Scan(List<Applied> applied, List<String> unread) {}

  /** Returns how a rule is known across the features: its group and its name. */
  public static String key(String group, String name) {
    return group + "/" + name;
  }

  /** Returns the group of the rules of a feature, from the name of its file. */
  public static String group(GherkinDocument document) {
    var uri = document.getUri().orElse("");
    var file = uri.substring(uri.lastIndexOf('/') + 1).replaceFirst("\\.feature$", "");
    return file.toUpperCase(Locale.ROOT).replace('-', '_');
  }

  /** Returns a rule's revision: the number of its tag {@code @revision-n}, or 1 without it. */
  public static int revision(Rule rule) {
    for (Tag tag : rule.getTags()) {
      var matcher = REVISION.matcher(tag.getName());
      if (matcher.matches()) {
        return Integer.parseInt(matcher.group(1));
      }
    }
    return 1;
  }

  /** Finds the marks on the classes of the viewer that {@code loader} has. */
  public static Scan scan(ClassLoader loader) {
    var applied = new ArrayList<Applied>();
    var unread = new ArrayList<String>();
    for (var name : classes()) {
      try {
        var type = Class.forName(name, false, loader);
        marks(type, name, "", applied);
        for (var method : type.getDeclaredMethods()) {
          marks(method, name, method.getName(), applied);
        }
        for (var constructor : type.getDeclaredConstructors()) {
          marks(constructor, name, "<init>", applied);
        }
      } catch (ClassNotFoundException | LinkageError e) {
        unread.add(name + ": " + e);
      }
    }
    applied.sort(Comparator.comparing(Applied::rule).thenComparing(Applied::method));
    return new Scan(List.copyOf(applied), List.copyOf(unread));
  }

  /**
   * Checks the marks against the rules of {@code documents}, in both directions. Each rule has a
   * method that applies it, unless all its examples are known bugs: the domain does not apply such
   * a rule yet. Each mark names a rule that a feature has. And a mark has the rule's revision: if
   * not, the rule changed its meaning after the method was checked against it.
   */
  public static List<String> check(List<GherkinDocument> documents, Scan scan) {
    var errors = new ArrayList<String>();
    scan.unread().forEach(each -> errors.add("could not read " + each));
    var rules = RulesWiki.rules(documents);
    var marked = new LinkedHashMap<String, List<Applied>>();
    scan.applied()
        .forEach(each -> marked.computeIfAbsent(each.rule(), rule -> new ArrayList<>()).add(each));
    for (var each : rules.entrySet()) {
      var text = each.getValue();
      var marks = marked.get(each.getKey());
      if (marks == null) {
        if (!onlyKnownBugs(text.rule())) {
          errors.add(
              text.id()
                  + " ("
                  + text.feature()
                  + ") has no method that applies it: put @BusinessRule(group = RuleGroup."
                  + text.group()
                  + ", name = \""
                  + text.id()
                  + "\") on the method that does");
        }
        continue;
      }
      var revision = revision(text.rule());
      for (var mark : marks) {
        if (mark.revision() != revision) {
          errors.add(
              text.id()
                  + " is revision "
                  + revision
                  + ", but "
                  + mark.method()
                  + " applies revision "
                  + mark.revision()
                  + ": the rule changed meaning: check "
                  + mark.method()
                  + " against it and set revision = "
                  + revision);
        }
      }
    }
    for (var each : marked.entrySet()) {
      if (!rules.containsKey(each.getKey())) {
        errors.add(
            "@BusinessRule names "
                + each.getKey()
                + " on "
                + String.join(", ", each.getValue().stream().map(Applied::method).toList())
                + ", but no feature has that rule");
      }
    }
    return errors;
  }

  /** Returns whether each example of {@code rule} is a known bug. */
  static boolean onlyKnownBugs(Rule rule) {
    if (tagged(rule.getTags())) {
      return true;
    }
    var examples =
        rule.getChildren().stream().flatMap(child -> child.getScenario().stream()).toList();
    return !examples.isEmpty() && examples.stream().allMatch(each -> tagged(each.getTags()));
  }

  private static boolean tagged(List<Tag> tags) {
    return tags.stream().anyMatch(tag -> tag.getName().equals(RulesWiki.KNOWN_BUG));
  }

  /** Saves the marks for the wiki, one line each: the group, name, revision, class and method. */
  public static void write(Path file, List<Applied> applied) throws IOException {
    var lines =
        applied.stream()
            .map(
                each ->
                    String.join(
                        "\t",
                        each.group(),
                        each.name(),
                        String.valueOf(each.revision()),
                        each.type(),
                        each.member()))
            .toList();
    Files.write(file, lines);
  }

  /** Reads the marks that {@link #write} saved. A file that is not there has none. */
  public static List<Applied> read(Path file) throws IOException {
    if (!Files.exists(file)) {
      return List.of();
    }
    try (var lines = Files.lines(file)) {
      return lines.filter(line -> !line.isBlank()).map(RulesApplied::applied).toList();
    }
  }

  /** Reads one line that {@link #write} saved, by its tabs. */
  private static Applied applied(String line) {
    var cells = List.of(line.split("\t", -1));
    return new Applied(
        cells.get(0),
        cells.get(1),
        Integer.parseInt(cells.get(2)),
        cells.get(3),
        cells.size() > 4 ? cells.get(4) : "");
  }

  /** Returns the methods that apply each rule, by {@link #key}, each once. */
  static Map<String, List<Applied>> byRule(Collection<Applied> applied) {
    var byRule = new LinkedHashMap<String, Set<Applied>>();
    applied.forEach(
        each -> byRule.computeIfAbsent(each.rule(), rule -> new LinkedHashSet<>()).add(each));
    var result = new LinkedHashMap<String, List<Applied>>();
    byRule.forEach((rule, each) -> result.put(rule, List.copyOf(each)));
    return result;
  }

  /** Adds the marks on {@code element}: one, or the several that their holder has. */
  private static void marks(
      AnnotatedElement element, String type, String member, List<Applied> applied) {
    for (Annotation annotation : element.getDeclaredAnnotations()) {
      var name = annotation.annotationType().getName();
      if (name.equals(MARK)) {
        applied.add(applied(annotation, type, member));
      } else if (name.equals(MARKS)) {
        for (var each : (Annotation[]) value(annotation, "value")) {
          applied.add(applied(each, type, member));
        }
      }
    }
  }

  private static Applied applied(Annotation mark, String type, String member) {
    return new Applied(
        ((Enum<?>) value(mark, "group")).name(),
        (String) value(mark, "name"),
        (Integer) value(mark, "revision"),
        type,
        member);
  }

  private static Object value(Annotation annotation, String element) {
    try {
      return annotation.annotationType().getMethod(element).invoke(annotation);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Cannot read " + element + " of " + annotation, e);
    }
  }

  /**
   * Returns the names of the viewer's classes on the classpath. Bazel can give a classpath as one
   * jar whose manifest names the others, so the manifest's Class-Path is read too.
   */
  private static Set<String> classes() {
    var names = new TreeSet<String>();
    var seen = new LinkedHashSet<Path>();
    var queue = new ArrayList<Path>();
    for (var entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
      if (!entry.isBlank()) {
        queue.add(Path.of(entry).toAbsolutePath().normalize());
      }
    }
    var prefix = PACKAGE.replace('.', '/');
    while (!queue.isEmpty()) {
      var path = queue.removeFirst();
      if (!seen.add(path) || !Files.exists(path)) {
        continue;
      }
      if (Files.isDirectory(path)) {
        try (Stream<Path> files = Files.walk(path)) {
          files
              .map(file -> path.relativize(file).toString().replace(File.separatorChar, '/'))
              .filter(file -> file.startsWith(prefix) && file.endsWith(".class"))
              .filter(file -> !file.endsWith("-info.class"))
              .forEach(file -> names.add(className(file)));
        } catch (IOException e) {
          throw new IllegalStateException("Cannot read " + path, e);
        }
        continue;
      }
      if (!path.toString().endsWith(".jar")) {
        continue;
      }
      try (var jar = new JarFile(path.toFile())) {
        var manifest = jar.getManifest();
        if (manifest != null) {
          var classPath = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
          if (classPath != null) {
            var base = path.getParent();
            for (var each : classPath.trim().split("\\s+")) {
              var next =
                  each.startsWith("file:")
                      ? Path.of(URI.create(each))
                      : base == null ? Path.of(each) : base.resolve(each);
              queue.add(next.toAbsolutePath().normalize());
            }
          }
        }
        jar.stream()
            .map(ZipEntry::getName)
            .filter(file -> file.startsWith(prefix) && file.endsWith(".class"))
            .filter(file -> !file.endsWith("-info.class"))
            .forEach(file -> names.add(className(file)));
      } catch (IOException e) {
        throw new IllegalStateException("Cannot read " + path, e);
      }
    }
    return names;
  }

  private static String className(String file) {
    return file.substring(0, file.length() - ".class".length()).replace('/', '.');
  }
}
