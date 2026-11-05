// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.myers.MyersDiff;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.text.DiffRowGenerator;
import com.github.difflib.text.deltamerge.InlineDeltaMergeInfo;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Block;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Run;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Units.Unit;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.List;

/**
 * How one text differs from another (Design: Data the pages need, matching): its units matched by
 * eId; a unit in one only, inserted or deleted, with its units; a unit in both whose own wording
 * differs, changed. A changed unit's blocks are aligned by what they say, and a block that changed
 * is compared word by word: both alignments are Myers's diff (java-diff-utils).
 */
public final class Differ {
  private Differ() {}

  /**
   * The differences of {@code to} from {@code from}, in {@code to}'s order: its units' eIds aligned
   * with {@code from}'s, units deleted before those inserted in their place.
   */
  public static List<Difference> differences(Element from, Element to) {
    var before = Units.of(from);
    var after = Units.of(to);
    var differences = new ArrayList<Difference>();
    var units =
        DiffUtils.diff(
            List.copyOf(before.byEId().values()),
            List.copyOf(after.byEId().values()),
            new MyersDiff<Unit>((one, other) -> one.eId().equals(other.eId())),
            null,
            true);
    for (var delta : units.getDeltas()) {
      for (var unit : delta.getSource().getLines()) {
        if (after.get(unit.eId()) == null && in(unit, after)) {
          differences.add(new Difference(unit.eId(), Kind.DELETED, List.of()));
        }
      }
      for (var unit : delta.getTarget().getLines()) {
        var earlier = before.get(unit.eId());
        if (earlier == null) {
          if (in(unit, before)) {
            differences.add(new Difference(unit.eId(), Kind.INSERTED, List.of()));
          }
        } else {
          // the same unit, where it was or moved
          var blocks = blocks(earlier.wording(), unit.wording());
          if (!blocks.isEmpty()) {
            differences.add(new Difference(unit.eId(), Kind.CHANGED, blocks));
          }
        }
      }
    }
    return List.copyOf(differences);
  }

  /** Whether {@code unit} is outermost or the unit it is in is one of {@code units}. */
  private static boolean in(Unit unit, Units units) {
    return unit.parent() == null || units.get(unit.parent()) != null;
  }

  /** How the blocks said {@code from} became those said {@code to}; none if they are the same. */
  static List<Block> blocks(List<String> from, List<String> to) {
    var blocks = new ArrayList<Block>();
    for (var delta : DiffUtils.diff(from, to).getDeltas()) {
      var source = delta.getSource();
      var target = delta.getTarget();
      var paired = Math.min(source.size(), target.size());
      for (var i = 0; i < Math.max(source.size(), target.size()); i++) {
        if (i < paired) {
          blocks.add(
              new Block(
                  source.getPosition() + i,
                  target.getPosition() + i,
                  runs(source.getLines().get(i), target.getLines().get(i))));
        } else if (i < source.size()) {
          blocks.add(
              new Block(
                  source.getPosition() + i,
                  null,
                  List.of(new Run(Run.Mark.DELETED, source.getLines().get(i)))));
        } else {
          blocks.add(
              new Block(
                  null,
                  target.getPosition() + i,
                  List.of(new Run(Run.Mark.INSERTED, target.getLines().get(i)))));
        }
      }
    }
    return blocks;
  }

  /**
   * How the wording {@code from} became {@code to}, in runs: the same, deleted, inserted. Where
   * words were replaced by others, the spaces between them, which both have, would split the change
   * word by word (relevant / Annex, provisions / to…): a run of spaces between changes joins them,
   * so a phrase replaced reads as one deletion and one insertion (java-diff-utils' whitespace
   * merger).
   */
  static List<Run> runs(String from, String to) {
    var before = Wording.words(from);
    var after = Wording.words(to);
    var runs = new ArrayList<Run>();
    var next = 0;
    var deltas =
        DiffRowGenerator.WHITESPACE_EQUALITIES_MERGER.apply(
            new InlineDeltaMergeInfo(DiffUtils.diff(before, after).getDeltas(), before, after));
    for (AbstractDelta<String> delta : deltas) {
      var source = delta.getSource();
      add(runs, Run.Mark.SAME, before.subList(next, source.getPosition()));
      add(runs, Run.Mark.DELETED, source.getLines());
      add(runs, Run.Mark.INSERTED, delta.getTarget().getLines());
      next = source.getPosition() + source.size();
    }
    add(runs, Run.Mark.SAME, before.subList(next, before.size()));
    return runs;
  }

  /**
   * Adds {@code words} to {@code runs} as a run marked {@code mark}, joining the last if it's one.
   */
  private static void add(List<Run> runs, Run.Mark mark, List<String> words) {
    add(runs, mark, String.join("", words));
  }

  private static void add(List<Run> runs, Run.Mark mark, String text) {
    if (text.isEmpty()) {
      return;
    }
    if (!runs.isEmpty() && runs.getLast().mark() == mark) {
      text = runs.removeLast().text() + text;
    }
    runs.add(new Run(mark, text));
  }
}
