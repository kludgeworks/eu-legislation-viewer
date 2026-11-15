// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.modifyHeaders;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.replacePattern;

import com.networknt.schema.Error;
import com.samskivert.mustache.Mustache;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.ManualRestDocumentation;
import org.springframework.restdocs.cli.CliDocumentation;
import org.springframework.restdocs.cli.CurlRequestSnippet;
import org.springframework.restdocs.http.HttpResponseSnippet;
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation;
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler;
import org.springframework.restdocs.operation.Operation;
import org.springframework.restdocs.snippet.Snippet;
import org.springframework.restdocs.snippet.TemplatedSnippet;
import org.springframework.restdocs.templates.StandardTemplateResourceResolver;
import org.springframework.restdocs.templates.TemplateFormat;
import org.springframework.restdocs.templates.mustache.MustacheTemplateEngine;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the examples of the API's documentation from a module's tests, with Spring REST Docs, and
 * checks them against the examples the page shows. A test documents an exchange with {@code
 * ApiSnippets.document("act/lookup").handle(result.getMvcResult())}, and REST Docs writes the
 * exchange as a fragment of HTML, {@code act/lookup/example.html}: the request's method and path,
 * the response's status, headers and body, and the request as a curl command. The format is the
 * viewer's own, {@code html}: its templates are in {@code src/test/resources/restdocs}.
 *
 * <p>The page's examples are golden files: the resources {@code api/snippets/<name>/example.html},
 * which {@code ApiDocsController} puts into its steps. After each test, every fragment it wrote
 * must be the same as the golden one. Under Bazel, REST Docs writes into the test's undeclared
 * outputs, and {@code mise run api-snippets} copies them over the golden ones after a deliberate
 * change.
 *
 * <p>The examples are at the viewer's public address, and their answers have no header that changes
 * from run to run.
 *
 * <p>An answer that names its JSON Schema ({@code describedby}) must also be valid against it, with
 * no member that the schema does not name ({@link ApiSchemas}).
 */
@NullMarked
public final class ApiSnippets implements BeforeEachCallback, AfterEachCallback {
  /** The address of the examples: the viewer's public one. */
  private static final String HOST = "eu-law.groknull.dev";

  /** Fragments of HTML, for the page to put together. */
  private static final TemplateFormat HTML =
      new TemplateFormat() {
        @Override
        public String getId() {
          return "html";
        }

        @Override
        public String getFileExtension() {
          return "html";
        }
      };

  private static final Path OUTPUT = outputDirectory();

  /**
   * When a preparation started and settled, in compact or indented JSON. REST Docs replaces the
   * group, the instant.
   */
  private static final Pattern INSTANT_STARTED = Pattern.compile("\"started\" ?: ?\"([^\"]+)\"");

  private static final Pattern INSTANT_SETTLED = Pattern.compile("\"settledAt\" ?: ?\"([^\"]+)\"");

  private static final ManualRestDocumentation DOCUMENTATION =
      new ManualRestDocumentation(OUTPUT.toString());

  /**
   * Returns the documentation of an exchange, {@code name}, for example {@code act/lookup}: its
   * request and its response, the JSON indented, without the headers that change from run to run. A
   * preparation's instants also change from run to run, so the examples show fixed ones: it started
   * at {@code 2026-11-14T09:30:00Z} and settled at {@code 2026-11-14T09:30:03Z}.
   */
  public static RestDocumentationResultHandler document(String name) {
    return MockMvcRestDocumentation.document(
        name,
        preprocessResponse(
            prettyPrint(),
            replacePattern(INSTANT_STARTED, "2026-11-14T09:30:00Z"),
            replacePattern(INSTANT_SETTLED, "2026-11-14T09:30:03Z"),
            modifyHeaders()
                .remove(HttpHeaders.ETAG)
                .remove(HttpHeaders.VARY)
                .remove(HttpHeaders.CONTENT_LENGTH)));
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    DOCUMENTATION.beforeTest(
        context.getRequiredTestClass(), context.getRequiredTestMethod().getName());
  }

  @Override
  public void afterEach(ExtensionContext context) throws IOException {
    DOCUMENTATION.afterTest();
    var differences = new ArrayList<String>();
    try (var written = Files.walk(OUTPUT)) {
      written
          .filter(Files::isRegularFile)
          .forEach(
              file -> {
                var name = OUTPUT.relativize(file).toString();
                if (!read(file).equals(golden(name))) {
                  differences.add(name);
                }
              });
    }
    assertThat(differences)
        .as(
            "The API's examples differ from the page's, api/snippets. After a deliberate change:"
                + " mise run api-snippets")
        .isEmpty();
  }

  private static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static @Nullable String golden(String name) {
    var resource = new ClassPathResource("api/snippets/" + name);
    if (!resource.exists()) {
      return null;
    }
    try {
      return resource.getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path outputDirectory() {
    var outputs = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR");
    return outputs == null
        ? Path.of("build", "generated-snippets")
        : Path.of(outputs, "api", "snippets");
  }

  /** Has MockMvc record each exchange for REST Docs, in the {@code html} format. */
  @TestConfiguration(proxyBeanMethods = false)
  public static class Configuration {
    @Bean
    MockMvcBuilderCustomizer restDocumentation() {
      return builder ->
          builder.apply(
              documentationConfiguration(DOCUMENTATION)
                  .templateEngine(
                      new MustacheTemplateEngine(
                          new StandardTemplateResourceResolver(HTML),
                          Mustache.compiler().escapeHTML(true).emptyStringIsFalse(true)))
                  .uris()
                  .withScheme("https")
                  .withHost(HOST)
                  .withPort(443)
                  .and()
                  .snippets()
                  .withDefaults(new Example(), new Described())
                  .withTemplateFormat(HTML));
    }
  }

  /**
   * An exchange, as the page shows it: the model of REST Docs' curl request and of its HTTP
   * response, and the request's method and path, with its query. The response's body starts on the
   * first line of its block, without the line that REST Docs puts before it.
   */
  private static final class Example extends TemplatedSnippet {
    private final Curl curl = new Curl();
    private final Response response = new Response();

    Example() {
      super("example", null);
    }

    @Override
    protected Map<String, Object> createModel(Operation operation) {
      var model = new HashMap<String, Object>(curl.createModel(operation));
      model.putAll(response.createModel(operation));
      model.computeIfPresent("responseBody", (name, body) -> body.toString().strip());
      var uri = operation.getRequest().getUri();
      model.put("method", operation.getRequest().getMethod().name());
      model.put(
          "path",
          uri.getRawQuery() == null
              ? uri.getRawPath()
              : uri.getRawPath() + "?" + uri.getRawQuery());
      return model;
    }
  }

  /**
   * Checks an answer against the JSON Schema that it names, {@code _links.describedby}, closed
   * ({@link ApiSchemas}). It writes nothing. An answer that names no schema is not checked.
   */
  private static final class Described implements Snippet {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    public void document(Operation operation) {
      var type = operation.getResponse().getHeaders().getContentType();
      if (type == null
          || !("json".equals(type.getSubtype()) || "json".equals(type.getSubtypeSuffix()))) {
        return;
      }
      var body = JSON.readTree(operation.getResponse().getContentAsString());
      var schema = body.at("/_links/describedby/href");
      if (schema.isMissingNode()) {
        return;
      }
      assertThat(ApiSchemas.schema(schema.asString()).validate(body))
          .as("The answer of %s against its schema, %s", operation.getName(), schema.asString())
          .extracting(Error::toString)
          .isEmpty();
    }
  }

  private static final class Curl extends CurlRequestSnippet {
    Curl() {
      super(CliDocumentation.multiLineFormat());
    }

    @Override
    protected Map<String, Object> createModel(Operation operation) {
      return super.createModel(operation);
    }
  }

  private static final class Response extends HttpResponseSnippet {
    @Override
    protected Map<String, Object> createModel(Operation operation) {
      return super.createModel(operation);
    }
  }
}
