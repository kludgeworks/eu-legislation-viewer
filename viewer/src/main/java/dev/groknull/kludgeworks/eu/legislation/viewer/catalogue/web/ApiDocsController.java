// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ApiStepView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ApiTermView;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The documentation of the API for people, on one page, {@code /api/docs}: a walk from the root to
 * a comparison, how the API answers, and what each link relation and problem type means.
 *
 * <ul>
 *   <li>The {@code av} CURIE of a HAL document leads to a relation on the page: {@code
 *       /api/docs#act}. The {@code service-doc} link of the root leads to the page.
 *   <li>The {@code type} of a problem leads to a problem type on the page: {@code
 *       /api/docs#text-failed}.
 * </ul>
 *
 * <p>Each module documents its own part of the API, in resources, and the page puts them together:
 *
 * <ul>
 *   <li>{@code api/guide/<order>-<name>.html}: a step of the walk, an HTML fragment that starts
 *       with its title, {@code <h3>Find an act</h3>}. The steps are in the order of their names.
 *       For example, the catalogue's {@code api/guide/1-act.html} and the comparison's {@code
 *       api/guide/4-comparisons.html}. A step shows an example by its name: {@code ${act/lookup}}.
 *   <li>{@code api/snippets/<name>/example.html}: an example, an exchange, as the module's
 *       documenting tests write it with Spring REST Docs. For example, {@code
 *       api/snippets/act/lookup/example.html}.
 *   <li>{@code api/rels/<name>.html}: a link relation, an HTML fragment. For example, {@code
 *       api/rels/act.html}.
 *   <li>{@code api/problems/<name>.html}: a problem type, an HTML fragment. It shows its example as
 *       a step does: {@code ${act/text-failed}}.
 * </ul>
 *
 * Thus a module documents its part without the catalogue knowing it. The resources are small, so
 * the page reads them when it is asked for.
 */
@Controller
class ApiDocsController {
  /** The address of the page. */
  static final String DOCS = ApiRootController.ROOT + "/docs";

  private static final String SNIPPETS = "api/snippets/";

  /** The title that a step starts with. */
  private static final Pattern TITLE = Pattern.compile("\\A<h3>(.+)</h3>\\R");

  /** The examples in a step, {@code ${act/lookup}}: an unknown one is an error. */
  private static final PropertyPlaceholderHelper EXAMPLES =
      new PropertyPlaceholderHelper("${", "}", null, null, false);

  private final Templates templates;
  private final Resource[] guide;
  private final Resource[] examples;
  private final Resource[] relations;
  private final Resource[] problems;

  ApiDocsController(
      Templates templates,
      @Value("classpath*:api/guide/*.html") Resource[] guide,
      @Value("classpath*:api/snippets/*/*/example.html") Resource[] examples,
      @Value("classpath*:api/rels/*.html") Resource[] relations,
      @Value("classpath*:api/problems/*.html") Resource[] problems) {
    this.templates = templates;
    this.guide = guide.clone();
    this.examples = examples.clone();
    this.relations = relations.clone();
    this.problems = problems.clone();
  }

  /** Returns the page. */
  @GetMapping(path = DOCS, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> docs(HttpServletRequest request) {
    var shown = examples();
    return ResponseEntity.ok()
        .contentType(Requests.HTML)
        .body(
            templates
                .catalogueApi(
                    Frame.of(request),
                    terms(guide, shown).stream().map(ApiDocsController::step).toList(),
                    shown,
                    terms(relations, shown),
                    terms(problems, shown))
                .render());
  }

  /**
   * Returns a step, {@code 1-act}, with its examples: its anchor is {@code start-act}, and its
   * title is the one it starts with.
   */
  private static ApiStepView step(ApiTermView step) {
    var title = TITLE.matcher(step.description());
    if (!title.find()) {
      throw new IllegalStateException(
          "The step api/guide/" + step.name() + ".html does not start with <h3>its title</h3>");
    }
    return new ApiStepView(
        "start-" + step.name().substring(step.name().indexOf('-') + 1),
        title.group(1),
        step.description().substring(title.end()));
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
        .map(
            resource ->
                new ApiTermView(
                    StringUtils.stripFilenameExtension(
                        Objects.requireNonNull(resource.getFilename())),
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
