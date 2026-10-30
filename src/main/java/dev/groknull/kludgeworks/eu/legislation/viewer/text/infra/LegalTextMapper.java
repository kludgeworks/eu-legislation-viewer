// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.text.Annotation;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Block;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.BlockContainer;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Cell;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Changed;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Citations;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Conclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.DateRef;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.DeletedBlock;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.DeletedInline;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.DocDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.DocNumber;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.DocPurpose;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.DocType;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.EId;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Emphasis;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Eol;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.EventRef;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Figure;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Formula;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.ImageContent;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Inline;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.InlineImage;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Lifecycle;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Lines;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.ListBlock;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.MathBlock;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.MathInline;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.NewText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Note;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.NoteRef;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.OldText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Paragraph;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Preamble;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.PreambleContent;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Preface;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Provenance;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Publication;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Quote;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.QuotedStructure;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Recitals;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Ref;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Row;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.RowGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Signatory;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Signature;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Source;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Span;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Table;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.TableGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.TableRow;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Unit;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlAction;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlAnnexes;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlAnnotation;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlBlock;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlBlockContainer;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlCaption;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlCell;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlChanged;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlCitations;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlConclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlDateRef;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlDeletedBlock;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlDeletedInline;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlDocDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlDocNumber;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlDocPurpose;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlDocType;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlDocumentKind;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlEmphasis;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlEmphasisKind;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlEol;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlEventRef;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlEventType;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlFigure;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlFormula;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlFormulaName;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlImageContent;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlInlineContent;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlInlineImage;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlInlineNode;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlIntro;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlLegalText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlLifecycle;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlLines;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlListBlock;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlListKind;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlMathBlock;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlMathInline;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlNewText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlNote;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlNoteKind;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlNoteRef;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlNotes;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlOldText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlParagraph;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlPreamble;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlPreface;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlProvenance;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlPublication;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlQuote;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlQuotedStructure;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlRecitals;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlRef;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlRefKind;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlRow;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlRowGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlSignatory;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlSignature;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlSource;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlSpan;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlTable;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlTableGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlUnit;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlUnitKind;
import java.io.StringWriter;
import java.util.List;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.jspecify.annotations.Nullable;
import org.mapstruct.BeanMapping;
import org.mapstruct.ClassAccessibility;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;
import org.w3c.dom.Element;

/**
 * Legal-text XML, as unmarshalled into the classes xjc generates from legal-text.xsd, onto the
 * domain records. MapStruct writes the implementation at compile time, and a property either side
 * leaves unmapped fails the build: a schema change the records don't follow can't go unnoticed.
 * Where the two agree on names it maps by name; below are only the renames (a note reference's href
 * becomes the note's eId), the optional wrappers that become empty lists, the two lists of mixed
 * types, and formulas. The generated classes' getters carry the schema's optionality as JSpecify
 * annotations (-Xjspecify), so MapStruct checks each optional value has a default here, and the
 * methods that take an optional value say so (@Nullable), so that they keep their null check. The
 * booleans' defaults restate the schema's: MapStruct 1.7.0.Beta2 counts a primitive getter as
 * possibly null.
 */
@Mapper(
    accessibility = ClassAccessibility.PACKAGE_PRIVATE,
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    unmappedSourcePolicy = ReportingPolicy.ERROR,
    subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
interface LegalTextMapper {

  /** What an optional wrapper of blocks or inline content becomes when the source leaves it out. */
  String EMPTY = "java(java.util.List.of())";

  // the document

  @Mapping(target = "body", source = "body.content")
  @Mapping(target = "annexes", defaultExpression = EMPTY)
  @Mapping(target = "notes", defaultExpression = EMPTY)
  LegalText legalText(XmlLegalText source);

  Source source(XmlSource source);

  @Nullable Publication publication(@Nullable XmlPublication source);

  @Mapping(target = "events", source = "eventRef")
  @Nullable Lifecycle lifecycle(@Nullable XmlLifecycle source);

  EventRef eventRef(XmlEventRef source);

  @Mapping(target = "longTitle", source = "longTitle.content")
  @Nullable Preface preface(@Nullable XmlPreface source);

  @Mapping(target = "eId", source = "EId")
  @Nullable Preamble preamble(@Nullable XmlPreamble source);

  Formula formula(XmlFormula source);

  @Mapping(target = "units", source = "unitOrDeleted")
  @Mapping(target = "intro", defaultExpression = EMPTY)
  Citations citations(XmlCitations source);

  @Mapping(target = "units", source = "unitOrDeleted")
  @Mapping(target = "intro", defaultExpression = EMPTY)
  Recitals recitals(XmlRecitals source);

  @Mapping(target = "signatures", source = "signature")
  @Nullable Conclusions conclusions(@Nullable XmlConclusions source);

  @Mapping(target = "signatories", source = "signatory")
  @Mapping(target = "placeAndDate", defaultExpression = EMPTY)
  Signature signature(XmlSignature source);

  Signatory signatory(XmlSignatory source);

  @Mapping(target = "eId", source = "EId")
  Note note(XmlNote source);

  @Mapping(target = "blocks", defaultExpression = EMPTY)
  @Nullable Lines lines(@Nullable XmlLines source);

  Provenance provenance(XmlProvenance source);

  // blocks

  @SubclassMapping(source = XmlParagraph.class, target = Paragraph.class)
  @SubclassMapping(source = XmlListBlock.class, target = ListBlock.class)
  @SubclassMapping(source = XmlTable.class, target = Table.class)
  @SubclassMapping(source = XmlTableGroup.class, target = TableGroup.class)
  @SubclassMapping(source = XmlUnit.class, target = Unit.class)
  @SubclassMapping(source = XmlQuotedStructure.class, target = QuotedStructure.class)
  @SubclassMapping(source = XmlAnnotation.class, target = Annotation.class)
  @SubclassMapping(source = XmlFigure.class, target = Figure.class)
  @SubclassMapping(source = XmlMathBlock.class, target = MathBlock.class)
  @SubclassMapping(source = XmlDeletedBlock.class, target = DeletedBlock.class)
  @SubclassMapping(source = XmlOldText.class, target = OldText.class)
  @SubclassMapping(source = XmlNewText.class, target = NewText.class)
  @SubclassMapping(source = XmlBlockContainer.class, target = BlockContainer.class)
  Block block(XmlBlock source);

  List<Block> blocks(List<XmlBlock> source);

  @Mapping(target = "num", defaultExpression = EMPTY)
  Paragraph paragraph(XmlParagraph source);

  ListBlock listBlock(XmlListBlock source);

  @Mapping(target = "caption", defaultExpression = EMPTY)
  Table table(XmlTable source);

  @Mapping(target = "cells", source = "td")
  @Mapping(target = "header", defaultValue = "false")
  Row row(XmlRow source);

  @Mapping(target = "num", defaultExpression = EMPTY)
  RowGroup rowGroup(XmlRowGroup source);

  @Mapping(target = "header", defaultValue = "false")
  Cell cell(XmlCell source);

  @Mapping(target = "caption", defaultExpression = EMPTY)
  TableGroup tableGroup(XmlTableGroup source);

  @Mapping(target = "eId", source = "EId")
  @Mapping(target = "num", defaultExpression = EMPTY)
  @Mapping(target = "box", defaultValue = "false")
  @Mapping(target = "ambiguous", defaultValue = "false")
  Unit unit(XmlUnit source);

  @Mapping(target = "eId", source = "EId")
  @Mapping(target = "ambiguous", defaultValue = "false")
  QuotedStructure quotedStructure(XmlQuotedStructure source);

  Annotation annotation(XmlAnnotation source);

  @Mapping(target = "caption", defaultExpression = EMPTY)
  @Mapping(target = "description", defaultExpression = EMPTY)
  Figure figure(XmlFigure source);

  DeletedBlock deletedBlock(XmlDeletedBlock source);

  OldText oldText(XmlOldText source);

  NewText newText(XmlNewText source);

  BlockContainer blockContainer(XmlBlockContainer source);

  // inline

  @SubclassMapping(source = XmlText.class, target = Text.class)
  @SubclassMapping(source = XmlEmphasis.class, target = Emphasis.class)
  @SubclassMapping(source = XmlDateRef.class, target = DateRef.class)
  @SubclassMapping(source = XmlQuote.class, target = Quote.class)
  @SubclassMapping(source = XmlNoteRef.class, target = NoteRef.class)
  @SubclassMapping(source = XmlRef.class, target = Ref.class)
  @SubclassMapping(source = XmlEol.class, target = Eol.class)
  @SubclassMapping(source = XmlMathInline.class, target = MathInline.class)
  @SubclassMapping(source = XmlSpan.class, target = Span.class)
  @SubclassMapping(source = XmlInlineImage.class, target = InlineImage.class)
  @SubclassMapping(source = XmlChanged.class, target = Changed.class)
  @SubclassMapping(source = XmlDeletedInline.class, target = DeletedInline.class)
  @SubclassMapping(source = XmlDocType.class, target = DocType.class)
  @SubclassMapping(source = XmlDocNumber.class, target = DocNumber.class)
  @SubclassMapping(source = XmlDocDate.class, target = DocDate.class)
  @SubclassMapping(source = XmlDocPurpose.class, target = DocPurpose.class)
  Inline inline(XmlInlineNode source);

  List<Inline> inlines(List<XmlInlineNode> source);

  @Mapping(target = "value", source = "v")
  Text text(XmlText source);

  Emphasis emphasis(XmlEmphasis source);

  DateRef dateRef(XmlDateRef source);

  Quote quote(XmlQuote source);

  @Mapping(target = "note", source = "href", qualifiedByName = "fragment")
  NoteRef noteRef(XmlNoteRef source);

  Ref ref(XmlRef source);

  default Eol eol(XmlEol source) {
    return new Eol();
  }

  Span span(XmlSpan source);

  InlineImage inlineImage(XmlInlineImage source);

  DocType docType(XmlDocType source);

  DocNumber docNumber(XmlDocNumber source);

  DocDate docDate(XmlDocDate source);

  DocPurpose docPurpose(XmlDocPurpose source);

  // amendment markers on running text: the same attributes as a unit's provenance

  @BeanMapping(ignoreUnmappedSourceProperties = "content")
  Provenance provenance(XmlChanged source);

  @BeanMapping(ignoreUnmappedSourceProperties = "content")
  Provenance provenance(XmlDeletedInline source);

  default Changed changed(XmlChanged source) {
    return new Changed(provenance(source), inlines(source.getContent()));
  }

  default DeletedInline deletedInline(XmlDeletedInline source) {
    return new DeletedInline(provenance(source), inlines(source.getContent()));
  }

  // kinds: the same constants on both sides

  LegalText.Kind kind(XmlDocumentKind source);

  EventRef.Type type(XmlEventType source);

  Formula.Name name(XmlFormulaName source);

  Note.Kind kind(XmlNoteKind source);

  Provenance.Action action(XmlAction source);

  ListBlock.Kind kind(XmlListKind source);

  Unit.Kind kind(XmlUnitKind source);

  Emphasis.@Nullable Kind kind(@Nullable XmlEmphasisKind source);

  Ref.Kind kind(XmlRefKind source);

  @Nullable ImageContent content(@Nullable XmlImageContent source);

  // identifiers, optional wrappers and the lists of mixed types

  default EId eId(String value) {
    return new EId(value);
  }

  /** A reference within the document, #note_2, as the eId it refers to. */
  @Named("fragment")
  default EId fragment(String href) {
    if (!href.startsWith("#")) {
      throw new IllegalArgumentException("not a reference within the document: " + href);
    }
    return new EId(href.substring(1));
  }

  default List<Block> blocks(@Nullable XmlCaption source) {
    return source == null ? List.of() : blocks(source.getContent());
  }

  default List<Block> blocks(@Nullable XmlIntro source) {
    return source == null ? List.of() : blocks(source.getContent());
  }

  default List<Block> blocks(@Nullable XmlAnnexes source) {
    return source == null ? List.of() : blocks(source.getUnitOrDeleted());
  }

  default List<Inline> inlines(@Nullable XmlInlineContent source) {
    return source == null ? List.of() : inlines(source.getContent());
  }

  default List<Note> notes(@Nullable XmlNotes source) {
    return source == null ? List.of() : source.getNote().stream().map(this::note).toList();
  }

  /** A preamble holds its formulas, citations and recitals among blocks, in their order. */
  default PreambleContent preambleContent(Object source) {
    return switch (source) {
      case XmlFormula formula -> formula(formula);
      case XmlCitations citations -> citations(citations);
      case XmlRecitals recitals -> recitals(recitals);
      case XmlBlock block -> block(block);
      default -> throw new IllegalArgumentException("not preamble content: " + source);
    };
  }

  /** A table holds rows and groups of rows, in their order. */
  default TableRow tableRow(Object source) {
    return switch (source) {
      case XmlRow row -> row(row);
      case XmlRowGroup group -> rowGroup(group);
      default -> throw new IllegalArgumentException("not a table row: " + source);
    };
  }

  // formulas: their MathML as written

  default MathBlock mathBlock(XmlMathBlock source) {
    return new MathBlock(serialize(source.getAny()));
  }

  default MathInline mathInline(XmlMathInline source) {
    return new MathInline(serialize(source.getAny()));
  }

  private static String serialize(Element mathml) {
    try {
      var transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      var out = new StringWriter();
      transformer.transform(new DOMSource(mathml), new StreamResult(out));
      return out.toString();
    } catch (TransformerException e) {
      throw new IllegalStateException("MathML that can't be serialised", e);
    }
  }
}
