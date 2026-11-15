// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ApiResourceView;
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
 * The documentation of the API for people, on one page, {@code /api/docs}: how the API answers,
 * each resource with its members and links, and each problem type.
 *
 * <ul>
 *   <li>The {@code av} CURIE of a HAL document leads to a relation on the page: {@code
 *       /api/docs#act}. A relation that leads to a resource has the anchor of the resource. The
 *       others, a search or an action, have theirs in the links of the resource that has them. The
 *       {@code service-doc} link of the root leads to the page.
 *   <li>The {@code type} of a problem leads to a problem type on the page: {@code
 *       /api/docs#text-failed}.
 * </ul>
 *
 * <p>Each module documents its own part of the API, in resources, and the page puts them together:
 *
 * <ul>
 *   <li>{@code api/resources/<order>-<name>.html}: a resource, an HTML fragment that starts with
 *       its title, {@code <h3>Act</h3>}. The resources are in the order of their names. For
 *       example, the catalogue's {@code api/resources/02-act.html} and the comparison's {@code
 *       api/resources/10-comparison.html}. A resource shows an example by its name: {@code
 *       ${act/lookup}}.
 *   <li>{@code api/snippets/<name>/example.html}: an example, an exchange, as the module's
 *       documenting tests write it with Spring REST Docs. For example, {@code
 *       api/snippets/act/lookup/example.html}.
 *   <li>{@code api/problems/<name>.html}: a problem type, an HTML fragment. It shows its example as
 *       a resource does: {@code ${act/text-failed}}.
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

  /** The title that a resource starts with. */
  private static final Pattern TITLE = Pattern.compile("\\A<h3>(.+)</h3>\\R");

  /** The examples in a resource, {@code ${act/lookup}}: an unknown one is an error. */
  private static final PropertyPlaceholderHelper EXAMPLES =
      new PropertyPlaceholderHelper("${", "}", null, null, false);

  private final Templates templates;
  private final Resource[] resources;
  private final Resource[] examples;
  private final Resource[] problems;

  ApiDocsController(
      Templates templates,
      @Value("classpath*:api/resources/*.html") Resource[] resources,
      @Value("classpath*:api/snippets/*/*/example.html") Resource[] examples,
      @Value("classpath*:api/problems/*.html") Resource[] problems) {
    this.templates = templates;
    this.resources = resources.clone();
    this.examples = examples.clone();
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
                    terms(resources, shown).stream().map(ApiDocsController::resource).toList(),
                    shown,
                    terms(problems, shown))
                .render());
  }

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
