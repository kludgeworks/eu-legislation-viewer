// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.web;

import static java.util.Map.entry;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Block;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expression;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Lines;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.PreambleContent;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.TableRow;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Unit;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.EnumNamingStrategies;
import tools.jackson.databind.annotation.EnumNaming;
import tools.jackson.databind.jsontype.NamedType;
import tools.jackson.databind.module.SimpleModule;

/**
 * The legal text as JSON (URLs and API: no JSON Schema, the JSON reads as the XML does): the
 * records as they are, each block, inline and table row typed by its element's name in
 * legal-text.xsd ({@code "type": "p"}, {@code "unit"}, {@code "t"}), an enumeration by its value
 * there ({@code "article"}, {@code "alphaUpper"}). What a text hasn't (none, an empty list) is left
 * out, as the XML leaves out an element. Lines are told apart by their fields, inline content or
 * blocks, as the schema has no element for either. For writing only: nothing reads it back. A
 * Jackson module, which Spring Boot adds to its JsonMapper.
 */
@Component
public class LegalTextJsonModule extends SimpleModule {
  private static final long serialVersionUID = 1L;

  /** Each block, inline and table row by its element's name in legal-text.xsd. */
  static final Map<Class<?>, String> ELEMENTS =
      Map.ofEntries(
          entry(Block.Paragraph.class, "p"),
          entry(Block.ListBlock.class, "list"),
          entry(Block.Table.class, "table"),
          entry(Block.TableGroup.class, "tableGroup"),
          entry(Unit.class, "unit"),
          entry(Block.QuotedStructure.class, "quotedStructure"),
          entry(Block.Annotation.class, "annotation"),
          entry(Block.Figure.class, "figure"),
          entry(Block.MathBlock.class, "math"),
          entry(Block.DeletedBlock.class, "deleted"),
          entry(Block.OldText.class, "old"),
          entry(Block.NewText.class, "new"),
          entry(Block.BlockContainer.class, "blockContainer"),
          entry(PreambleContent.Formula.class, "formula"),
          entry(PreambleContent.Citations.class, "citations"),
          entry(PreambleContent.Recitals.class, "recitals"),
          entry(TableRow.Row.class, "tr"),
          entry(TableRow.RowGroup.class, "rowGroup"),
          entry(Inline.Text.class, "t"),
          entry(Inline.Emphasis.class, "emphasis"),
          entry(Inline.DateRef.class, "date"),
          entry(Inline.Quote.class, "quote"),
          entry(Inline.NoteRef.class, "noteRef"),
          entry(Inline.Ref.class, "ref"),
          entry(Inline.Eol.class, "eol"),
          entry(Inline.MathInline.class, "math"),
          entry(Inline.Span.class, "span"),
          entry(Inline.InlineImage.class, "img"),
          entry(Inline.Changed.class, "changed"),
          entry(Inline.DeletedInline.class, "deleted"),
          entry(Inline.DocType.class, "docType"),
          entry(Inline.DocNumber.class, "docNumber"),
          entry(Inline.DocDate.class, "docDate"),
          entry(Inline.DocPurpose.class, "docPurpose"));

  public LegalTextJsonModule() {
    super("legal-text");
    for (var choice : List.of(Block.class, Inline.class, PreambleContent.class, TableRow.class)) {
      setMixInAnnotation(choice, ByElement.class);
    }
    setMixInAnnotation(Lines.class, ByFields.class);
    ELEMENTS.forEach((type, element) -> registerSubtypes(new NamedType(type, element)));
    for (var type : model()) {
      if (type.isEnum()) {
        setMixInAnnotation(type, AsSchemaValue.class);
      }
    }
  }

  @Override
  public void setupModule(SetupContext context) {
    super.setupModule(context);
    var leftOut = JsonInclude.Value.construct(JsonInclude.Include.NON_EMPTY, null);
    for (var type : model()) {
      if (type.isRecord()) {
        context.configOverride(type).setInclude(leftOut);
      }
    }
  }

  /**
   * The text's types: those of the expressions module that an Expression holds, by the records'
   * components and the sealed interfaces' cases.
   */
  static Set<Class<?>> model() {
    var found = new LinkedHashSet<Class<?>>();
    var next = new ArrayDeque<Class<?>>(List.of(Expression.class));
    while (!next.isEmpty()) {
      var type = next.pop();
      if (!type.getPackageName().equals(Expression.class.getPackageName()) || !found.add(type)) {
        continue;
      }
      if (type.isSealed()) {
        next.addAll(List.of(type.getPermittedSubclasses()));
      }
      if (type.isRecord()) {
        for (var component : type.getRecordComponents()) {
          next.add(component.getType());
          if (component.getGenericType() instanceof ParameterizedType parameterized) {
            for (var argument : parameterized.getActualTypeArguments()) {
              if (argument instanceof Class<?> element) {
                next.add(element);
              }
            }
          }
        }
      }
    }
    return found;
  }

  /** A choice of the schema's, typed by its element's name. */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  private interface ByElement {}

  /** A choice the schema has no element for, told by its fields. */
  @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
  private interface ByFields {}

  /** An enumeration, by its value in the schema: its constants in lower camel case. */
  @EnumNaming(EnumNamingStrategies.LowerCamelCaseStrategy.class)
  private interface AsSchemaValue {}
}
