// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.description.JavadocDescription;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmNode;

/**
 * Java records' Javadoc from an XML Schema's documentation, where the format is described once: a
 * record says what its schema type's documentation says; each component (@param) what the element
 * or attribute it maps from says; each enum constant what its enumeration value says, by xjc's
 * naming (ALPHA_UPPER for alpha-upper). Records and schema types share their names, but for those
 * the tables rename; types the schema doesn't have keep their own Javadoc. {@link
 * UpdateSchemaJavadoc} writes it into the sources, SchemaJavadocTests checks they say it; the macro
 * schema_javadoc sets both up.
 */
public final class SchemaJavadoc {

  private static final String XS = "http://www.w3.org/2001/XMLSchema";

  static {
    StaticJavaParser.getParserConfiguration()
        .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
  }

  private final XPathCompiler xpath;
  private final XdmNode schema;
  private final Map<String, String> types;
  private final Map<String, String> components;

  /**
   * @param schema the XML Schema
   * @param types record types whose schema type has another name: Inline=InlineNode; nested records
   *     by their own name, nested enums by their outer type, Unit.Kind=UnitKind
   * @param components components mapped from an element or attribute of another name: Row.cells=td
   */
  public SchemaJavadoc(
      Processor saxon, Path schema, Map<String, String> types, Map<String, String> components) {
    xpath = saxon.newXPathCompiler();
    xpath.declareNamespace("xs", XS);
    xpath.declareVariable(new QName("type"));
    xpath.declareVariable(new QName("name"));
    try {
      this.schema = saxon.newDocumentBuilder().build(schema.toFile());
    } catch (SaxonApiException e) {
      throw new IllegalStateException("can't read " + schema, e);
    }
    this.types = Map.copyOf(types);
    this.components = Map.copyOf(components);
  }

  /** The schema and the tables the macro passes, as system properties (schema-javadoc.*). */
  public static SchemaJavadoc fromProperties() {
    return new SchemaJavadoc(
        new Processor(false),
        Path.of(System.getProperty("schema-javadoc.schema")),
        table(System.getProperty("schema-javadoc.types", "")),
        table(System.getProperty("schema-javadoc.components", "")));
  }

  /** The sources the macro passes, by their paths in the workspace (their runfiles paths too). */
  public static List<Path> sources() {
    return Stream.of(System.getProperty("schema-javadoc.srcs").split(" "))
        .filter(source -> !source.isBlank())
        .map(Path::of)
        .toList();
  }

  /** A table as the macro passes it: "Inline=InlineNode,Unit.Kind=UnitKind". */
  public static Map<String, String> table(String entries) {
    var table = new LinkedHashMap<String, String>();
    for (var entry : entries.split(",", -1)) {
      if (!entry.isBlank()) {
        var pair = entry.split("=", 2);
        table.put(pair[0].trim(), pair[1].trim());
      }
    }
    return table;
  }

  /** Writes the schema's documentation into a record file's Javadoc. */
  public void apply(CompilationUnit file) {
    types(file).forEach(this::apply);
  }

  /**
   * A file's types by the names the tables use: a record or an interface by its own, at the top or
   * nested in another (Inline.Emphasis is Emphasis), an enum by its outer type's (Unit.Kind).
   */
  private static Map<String, TypeDeclaration<?>> types(CompilationUnit file) {
    var types = new LinkedHashMap<String, TypeDeclaration<?>>();
    file.getTypes().forEach(type -> collect(type, types));
    return types;
  }

  private static void collect(TypeDeclaration<?> type, Map<String, TypeDeclaration<?>> types) {
    types.put(type.getNameAsString(), type);
    for (var member : type.getMembers()) {
      if (member instanceof EnumDeclaration nested) {
        types.put(type.getNameAsString() + "." + nested.getNameAsString(), nested);
      } else if (member instanceof TypeDeclaration<?> nested) {
        collect(nested, types);
      }
    }
  }

  private void apply(String name, TypeDeclaration<?> type) {
    expected(name, type).ifPresent(type::setJavadocComment);
    if (type instanceof EnumDeclaration enumeration) {
      var schemaType = types.getOrDefault(name, name);
      for (var constant : enumeration.getEntries()) {
        constant(schemaType, constant.getNameAsString()).ifPresent(constant::setJavadocComment);
      }
    }
  }

  /** Where a record file's Javadoc says other than the schema, as "Unit.Kind.ARTICLE: …" lines. */
  public List<String> differences(CompilationUnit file) {
    var differences = new ArrayList<String>();
    types(file).forEach((name, type) -> compare(name, type, differences));
    return differences;
  }

  public static CompilationUnit parse(String source) {
    return StaticJavaParser.parse(source);
  }

  private void compare(String name, TypeDeclaration<?> type, List<String> differences) {
    expected(name, type)
        .ifPresent(
            expected -> {
              var actual = text(type);
              if (!actual.equals(normalize(expected.toText()))) {
                differences.add(name + ": the schema says «" + normalize(expected.toText()) + "»");
              }
            });
    if (type instanceof EnumDeclaration enumeration) {
      var schemaType = types.getOrDefault(name, name);
      for (var constant : enumeration.getEntries()) {
        constant(schemaType, constant.getNameAsString())
            .ifPresent(
                expected -> {
                  if (!text(constant).equals(normalize(expected))) {
                    differences.add(
                        name
                            + "."
                            + constant.getNameAsString()
                            + ": the schema says «"
                            + expected
                            + "»");
                  }
                });
      }
    }
  }

  /** The Javadoc a type should carry: empty when the schema has no such type. */
  private Optional<Javadoc> expected(String name, TypeDeclaration<?> type) {
    var schemaType = types.getOrDefault(name, name);
    var description = documentation("/xs:schema/xs:*[@name = $type]/xs:annotation", schemaType, "");
    if (description.isEmpty()) {
      return Optional.empty();
    }
    var javadoc = new Javadoc(JavadocDescription.parseText(description.get()));
    if (type instanceof RecordDeclaration record) {
      for (var component : record.getParameters()) {
        var componentName = component.getNameAsString();
        member(schemaType, components.getOrDefault(name + "." + componentName, componentName))
            .ifPresent(doc -> javadoc.addBlockTag("param", componentName, doc));
      }
    }
    return Optional.of(javadoc);
  }

  /** An element's or attribute's documentation, in the type or an attribute group it uses. */
  private Optional<String> member(String type, String name) {
    return documentation(
        "let $t := /xs:schema/xs:*[@name = $type], $groups := /xs:schema/xs:attributeGroup[@name ="
            + " $t//xs:attributeGroup/@ref] return ($t, $groups)//(xs:element |"
            + " xs:attribute)[(@name, substring-after(@ref, ':'))[1] = $name]/xs:annotation",
        type,
        name);
  }

  /**
   * An enumeration value's documentation, by the constant that stands for it: ALPHA_UPPER for
   * alpha-upper, ACTING_ENTITY for actingEntity, as xjc names them.
   */
  private Optional<String> constant(String type, String constant) {
    return documentation(
        "/xs:schema/xs:simpleType[@name = $type]//xs:enumeration[upper-case(replace(replace(@value,"
            + " '([a-z0-9])([A-Z])', '$1_$2'), '-', '_')) = $name]/xs:annotation",
        type,
        constant);
  }

  private Optional<String> documentation(String annotation, String type, String name) {
    try {
      var selector = xpath.compile("(" + annotation + ")[1]/xs:documentation").load();
      selector.setVariable(new QName("type"), new XdmAtomicValue(type));
      selector.setVariable(new QName("name"), new XdmAtomicValue(name));
      selector.setContextItem(schema);
      var found = selector.evaluate();
      return found.isEmpty()
          ? Optional.empty()
          : Optional.of(normalize(found.itemAt(0).getStringValue()));
    } catch (SaxonApiException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String text(NodeWithJavadoc<?> node) {
    return node.getJavadoc().map(javadoc -> normalize(javadoc.toText())).orElse("");
  }

  private static String normalize(String text) {
    return text.trim().replaceAll("\\s+", " ");
  }
}
