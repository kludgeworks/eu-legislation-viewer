// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * How the own blocks and footnotes of an occurrence differ from those of the occurrence that it
 * was.
 *
 * @param blocks its own blocks as they show: a changed block with its edits word by word, an
 *     inserted block whole as an insertion, and each deleted block whole as a deletion, where it
 *     stood. It counts the edited words of the blocks and of the footnotes.
 * @param footnotes its footnotes, with their edits against the footnotes that it had, with the
 *     deleted footnotes where they stood. Null if the footnotes say the same.
 */
record OccurrenceEdits(Pieces.Shown blocks, @Nullable Element footnotes) {

  /**
   * Returns how {@code to} differs from {@code from}. Returns null if the two occurrences say the
   * same.
   */
  @BusinessRule(
      group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE,
      name = "footnotes-belong-to-their-occurrence")
  static @Nullable OccurrenceEdits of(Occurrence from, Occurrence to) {
    // What the two occurrences say is worked out from their own blocks and footnotes alone. Most
    // occurrences of
    // two versions have the same ones, which say the same without working it out.
    if ((from.blocks().equals(to.blocks()) && from.footnotes().equals(to.footnotes()))
        || numbered(from).equals(numbered(to))) {
      return null;
    }
    var blocks = new Pieces.Shown();
    for (var each : Pieces.differing(from.blocks(), to.blocks(), from.keys(), to.keys())) {
      switch (each) {
        case Pieces.Changed(int at, Element was, Element is) ->
            blocks.replace(at, Edits.between(was, is));
        case Pieces.Inserted(int at, Element is) ->
            blocks.replace(at, Edits.all(is, Edits.INSERTED));
        case Pieces.Deleted(int at, Element was) ->
            blocks.delete(at, Edits.all(was, Edits.DELETED));
      }
    }
    @Nullable Element footnotes = null;
    if (!keys(from.footnotes()).equals(keys(to.footnotes()))) {
      var edited =
          Edits.between(Differ.footnotes(from.footnotes()), Differ.footnotes(to.footnotes()));
      footnotes = edited.element();
      blocks.count(edited);
    }
    return new OccurrenceEdits(blocks, footnotes);
  }

  /**
   * Returns what the own blocks of {@code occurrence} say, with its num without the full stop after
   * it, and what the footnotes that they refer to say. An occurrence whose footnote an act changed
   * differs. If a num only gained or lost its full stop, the Publications Office typeset it, and no
   * act changed it. For example, "11.3" to "11.3.".
   */
  @BusinessRule(
      group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE,
      name = "full-stop-after-a-number-is-no-change")
  private static List<String> numbered(Occurrence occurrence) {
    var wording = new ArrayList<String>();
    for (var block : occurrence.blocks()) {
      var said = Wording.key(block);
      wording.add(block.name().equals("num") ? said.replaceFirst("\\.$", "") : said);
    }
    occurrence.footnotes().forEach(footnote -> wording.add("footnote:" + Wording.key(footnote)));
    return wording;
  }

  /** Returns what each of {@code footnotes} says, as a comparison matches it. */
  private static List<String> keys(List<Element> footnotes) {
    return footnotes.stream().map(Wording::key).toList();
  }
}
