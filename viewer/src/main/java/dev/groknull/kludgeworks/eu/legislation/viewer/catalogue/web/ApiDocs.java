// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ApiResourceView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ApiTermView;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.StringUtils;

/**
 * The sections of the API's documentation, as each module gives them. The documentation page shows
 * them ({@link ApiDocsController}), and the contents beside it and beside the About page list them.
 *
 * <ul>
 *   <li>{@code api/resources/<order>-<name>.html}: a resource, an HTML fragment that starts with
 *       its title, {@code <h3>Act</h3>}. The resources are in the order of their names. For
 *       example, the catalogue's {@code api/resources/02-act.html} and the comparison's {@code
 *       api/resources/11-comparison.html}. A resource shows an example by its name: {@code
 *       ${act/lookup}}.
 *   <li>{@code api/snippets/<name>/example.html}: an example, an exchange, as the module's
 *       documenting tests write it with Spring REST Docs. For example, {@code
 *       api/snippets/act/lookup/example.html}.
 *   <li>{@code api/problems/<name>.html}: a problem type, an HTML fragment. It shows its example as
 *       a resource does: {@code ${act/text-failed}}.
 * </ul>
 *
 * Thus a module documents its section without the catalogue knowing it. The sections are small, so
 * they are read each time a page asks for them.
 */
@Component
class ApiDocs {
  private static final String SNIPPETS = "api/snippets/";

  /** The title that a resource starts with. */
  private static final Pattern TITLE = Pattern.compile("\\A<h3>(.+)</h3>\\R");

  /** The examples in a resource, {@code ${act/lookup}}: an unknown one is an error. */
  private static final PropertyPlaceholderHelper EXAMPLES =
      new PropertyPlaceholderHelper("${", "}", null, null, false);

  private final Resource[] resources;
  private final Resource[] examples;
  private final Resource[] problems;

  ApiDocs(
      @Value("classpath*:api/resources/*.html") Resource[] resources,
      @Value("classpath*:api/snippets/*/*/example.html") Resource[] examples,
      @Value("classpath*:api/problems/*.html") Resource[] problems) {
    this.resources = resources.clone();
    this.examples = examples.clone();
    this.problems = problems.clone();
  }

  /** Reads the sections of the documentation. */
  Sections read() {
    var shown = examples();
    return new Sections(
        terms(resources, shown).stream().map(ApiDocs::resource).toList(),
        shown,
        terms(problems, shown));
  }

  /**
   * The sections of the documentation.
   *
   * @param resources the resources, in order
   * @param examples each example by its name, for example {@code act/lookup}
   * @param problems the problem types, by name
   */
  record Sections(
      List<ApiResourceView> resources, Map<String, String> examples, List<ApiTermView> problems) {}

  /**
   * Returns a resource, {@code 02-act}, with its examples: its anchor is {@code act}, and its title
   * is the one it starts with.
   */
  private static ApiResourceView resource(ApiTermView resource) {
    var title = TITLE.matcher(resource.description());
    if (!title.find()) {
      throw new IllegalStateException(
          "The resource api/resources/"
              + resource.name()
              + ".html does not start with <h3>its title</h3>");
    }
    return new ApiResourceView(
        resource.name().substring(resource.name().indexOf('-') + 1),
        title.group(1),
        resource.description().substring(title.end()));
  }

  /** Returns each example by its name, for example {@code act/lookup}. */
  private Map<String, String> examples() {
    var shown = new TreeMap<String, String>();
    for (var example : examples) {
      var path = path(example);
      shown.put(path.substring(0, path.lastIndexOf('/')), content(example));
    }
    return shown;
  }

  /**
   * Returns the path of an example under {@code api/snippets}. For example, {@code
   * act/lookup/example.html}.
   */
  private static String path(Resource snippet) {
    try {
      var url = snippet.getURL().getPath();
      return url.substring(url.lastIndexOf(SNIPPETS) + SNIPPETS.length());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Returns the resources {@code found} as terms, named by their file names, in their order, with
   * their examples.
   */
  private static List<ApiTermView> terms(Resource[] found, Map<String, String> examples) {
    return Arrays.stream(found)
        .map(resource ->
            new ApiTermView(
                StringUtils.stripFilenameExtension(Objects.requireNonNull(resource.getFilename())),
                EXAMPLES.replacePlaceholders(content(resource).strip(), examples::get)))
        .sorted(Comparator.comparing(ApiTermView::name))
        .toList();
  }

  private static String content(Resource resource) {
    try {
      return resource.getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
