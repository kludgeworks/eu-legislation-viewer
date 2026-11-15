// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The JSON Schemas of the API's answers, closed for the tests.
 *
 * <p>The published schemas are open: an object can have members that its schema does not name. Thus
 * a minor version can add a member, and a client that validates an answer does not fail. The tests
 * are stricter. Here every object that names its members has no others ({@code
 * "additionalProperties": false}). So an answer cannot get a member that its schema does not give.
 *
 * <p>The schemas are read from the classpath, {@code static/api/schemas}, by their {@code $id}. No
 * schema is fetched from the network.
 */
@NullMarked
public final class ApiSchemas {
  /** The address of the schemas of version 1: the {@code $id} of each starts with it. */
  public static final String V1 = "https://eu-law.groknull.dev/api/schemas/v1/";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final SchemaRegistry REGISTRY =
      SchemaRegistry.withDefaultDialect(
          SpecificationVersion.DRAFT_2020_12, builder -> builder.schemas(ApiSchemas::closed));

  private ApiSchemas() {}

  /**
   * Returns the closed schema at {@code iri}. For example, {@code V1 + "unit.schema.json"}, or
   * {@code V1 + "text.schema.json#/$defs/legalText"} for a part of one.
   */
  public static Schema schema(String iri) {
    return REGISTRY.getSchema(SchemaLocation.of(iri));
  }

  /** Returns the schema that {@code iri} names, closed. Returns null if it is not the API's. */
  private static @Nullable String closed(String iri) {
    if (!iri.startsWith(V1)) {
      return null;
    }
    var resource = new ClassPathResource("static/api/schemas/v1/" + iri.substring(V1.length()));
    try {
      var schema = JSON.readTree(resource.getContentAsString(StandardCharsets.UTF_8));
      close(schema);
      return schema.toString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Gives every object schema below {@code node} that names its members no others. */
  private static void close(JsonNode node) {
    if (node.isObject() && node.has("properties") && !node.has("additionalProperties")) {
      ((ObjectNode) node).put("additionalProperties", false);
    }
    node.values().forEach(ApiSchemas::close);
  }
}
