// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Block;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Block.Annotation;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Block.BlockContainer;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Block.DeletedBlock;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Block.Figure;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Block.ListBlock;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Block.MathBlock;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Block.NewText;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Block.OldText;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Block.Paragraph;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Block.QuotedStructure;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Block.Table;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Block.TableGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expression;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline.Changed;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline.DateRef;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline.DeletedInline;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline.DocDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline.DocNumber;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline.DocPurpose;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline.DocType;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline.Emphasis;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline.Eol;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline.InlineImage;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline.MathInline;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline.NoteRef;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline.Quote;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline.Ref;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline.Span;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Lines;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Lines.BlockLines;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Lines.InlineLines;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.PreambleContent;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.PreambleContent.Citations;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.PreambleContent.Formula;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.PreambleContent.Recitals;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.TableRow;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.TableRow.Row;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.TableRow.RowGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Unit;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Every text value, eId and note reference a legal text's records hold, in document order: what the
 * XML holds as t/@v and @eId, if the mapping lost nothing. The switches are exhaustive over the
 * sealed types, so a new record type doesn't compile here until it is walked.
 */
final class RecordContents {

  final List<String> texts = new ArrayList<>();
  final List<String> eIds = new ArrayList<>();
  final List<String> noteRefs = new ArrayList<>();

  static RecordContents of(Expression legalText) {
    var contents = new RecordContents();
    contents.legalText(legalText);
    return contents;
  }

  private void legalText(Expression legalText) {
    if (legalText.preface() != null) {
      blocks(legalText.preface().longTitle());
    }
    if (legalText.preamble() != null) {
      eIds.add(legalText.preamble().eId().value());
      legalText.preamble().content().forEach(this::preambleContent);
    }
    blocks(legalText.body());
    if (legalText.conclusions() != null) {
      blocks(legalText.conclusions().content());
      for (var signature : legalText.conclusions().signatures()) {
        inlines(signature.placeAndDate());
        for (var signatory : signature.signatories()) {
          lines(signatory.organization());
          lines(signatory.role());
          lines(signatory.person());
        }
      }
    }
    blocks(legalText.annexes());
    for (var note : legalText.notes()) {
      eIds.add(note.eId().value());
      blocks(note.content());
    }
  }

  private void preambleContent(PreambleContent content) {
    switch (content) {
      case Formula formula -> blocks(formula.content());
      case Citations citations -> {
        blocks(citations.intro());
        blocks(citations.units());
      }
      case Recitals recitals -> {
        blocks(recitals.intro());
        blocks(recitals.units());
      }
      case Block block -> block(block);
    }
  }

  private void blocks(List<Block> blocks) {
    blocks.forEach(this::block);
  }

  private void block(Block block) {
    switch (block) {
      case Paragraph paragraph -> {
        inlines(paragraph.num());
        inlines(paragraph.content());
      }
      case ListBlock list -> blocks(list.items());
      case Table table -> {
        blocks(table.caption());
        rows(table.rows());
      }
      case TableGroup group -> {
        blocks(group.caption());
        blocks(group.content());
      }
      case Unit unit -> {
        eIds.add(unit.eId().value());
        inlines(unit.num());
        lines(unit.heading());
        lines(unit.subheading());
        blocks(unit.content());
      }
      case QuotedStructure quoted -> {
        eIds.add(quoted.eId().value());
        blocks(quoted.content());
      }
      case Annotation annotation -> {
        lines(annotation.heading());
        blocks(annotation.content());
      }
      case Figure figure -> {
        blocks(figure.caption());
        blocks(figure.description());
      }
      case MathBlock math -> {}
      case DeletedBlock deleted -> blocks(deleted.content());
      case OldText old -> blocks(old.content());
      case NewText corrected -> blocks(corrected.content());
      case BlockContainer container -> blocks(container.content());
    }
  }

  private void rows(List<TableRow> rows) {
    for (var row : rows) {
      switch (row) {
        case Row cells -> cells.cells().forEach(cell -> blocks(cell.content()));
        case RowGroup group -> {
          inlines(group.num());
          lines(group.heading());
          lines(group.subheading());
          rows(group.rows());
        }
      }
    }
  }

  private void lines(@Nullable Lines lines) {
    switch (lines) {
      case null -> {}
      case InlineLines inline -> inlines(inline.content());
      case BlockLines blocks -> blocks(blocks.blocks());
    }
  }

  private void inlines(List<Inline> inlines) {
    for (var inline : inlines) {
      switch (inline) {
        case Text text -> texts.add(text.value());
        case Emphasis emphasis -> inlines(emphasis.content());
        case Quote quote -> inlines(quote.content());
        case Ref ref -> inlines(ref.content());
        case Span span -> inlines(span.content());
        case Changed changed -> inlines(changed.content());
        case DeletedInline deleted -> inlines(deleted.content());
        case DocType type -> inlines(type.content());
        case DocNumber number -> inlines(number.content());
        case DocPurpose purpose -> inlines(purpose.content());
        case DateRef date -> {}
        case DocDate date -> {}
        case NoteRef ref -> noteRefs.add(ref.note().value());
        case Eol eol -> {}
        case MathInline math -> {}
        case InlineImage image -> {}
      }
    }
  }
}
