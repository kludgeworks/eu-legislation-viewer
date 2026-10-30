// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.text.Annotation;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Block;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.BlockContainer;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Changed;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Citations;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.DateRef;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.DeletedBlock;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.DeletedInline;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.DocDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.DocNumber;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.DocPurpose;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.DocType;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Emphasis;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Eol;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Figure;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Formula;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Inline;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.InlineImage;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Lines;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.ListBlock;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.MathBlock;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.MathInline;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.NewText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.NoteRef;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.OldText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Paragraph;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.PreambleContent;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Quote;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.QuotedStructure;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Recitals;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Ref;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Row;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.RowGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Span;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Table;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.TableGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.TableRow;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Unit;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Every text value and every eId a legal text's records hold, in document order: what the XML holds
 * as t/@v and @eId, if the mapping lost nothing. The switches are exhaustive over the sealed types,
 * so a new record type doesn't compile here until it is walked.
 */
final class RecordContents {

  final List<String> texts = new ArrayList<>();
  final List<String> eIds = new ArrayList<>();

  static RecordContents of(LegalText legalText) {
    var contents = new RecordContents();
    contents.legalText(legalText);
    return contents;
  }

  private void legalText(LegalText legalText) {
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
    if (lines != null) {
      inlines(lines.content());
      blocks(lines.blocks());
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
        case NoteRef ref -> {}
        case Eol eol -> {}
        case MathInline math -> {}
        case InlineImage image -> {}
      }
    }
  }
}
