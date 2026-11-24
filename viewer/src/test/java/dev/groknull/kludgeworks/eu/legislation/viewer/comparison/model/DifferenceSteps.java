// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Place;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison.Side;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The steps of the rules about what counts as a difference, and how its edits show. The texts are
 * the converter's sample texts of the comparison ({@link Comparing}), or of an act that Cellar's
 * recorded answers do not hold. The steps reach the comparison's domain only: {@link Differ} and
 * {@link Occurrences}.
 */
public class DifferenceSteps {
  private final Comparing comparing;
  private Differ.@Nullable Compared compared;
  private @Nullable Difference difference;

  /** The words that the last step "it deletes" gave. */
  private @Nullable String deleted;

  public DifferenceSteps(Comparing comparing) {
    this.comparing = comparing;
  }

  @Given("the texts of {act} of {day} and of {day}")
  public void texts(ActIdentifier act, LocalDate from, LocalDate to) {
    comparing.texts(Comparing.consolidated(act, from), Comparing.consolidated(act, to));
  }

  @Given("the texts of {act} as adopted and of {day}")
  public void adopted(ActIdentifier act, LocalDate to) {
    comparing.texts(Comparing.sample(act.celex()), Comparing.consolidated(act, to));
  }

  @Then("{place} does not differ")
  public void doesNotDiffer(Place place) {
    var eId = Comparing.eId(place, Occurrences.of(comparing.later()));
    assertThat(compared().differences()).extracting(Difference::eId).doesNotContain(eId);
  }

  @When("the reader reads the difference at the {word} {place}")
  public void reads(String kind, Place place) {
    var expected = Kind.valueOf(kind.toUpperCase(Locale.ROOT));
    var eId =
        Comparing.eId(
            place,
            Occurrences.of(expected == Kind.DELETED ? comparing.earlier() : comparing.later()));
    difference =
        compared().differences().stream()
            .filter(each -> each.eId().equals(eId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No difference at " + place.words()));
    assertThat(difference().kind()).as(place.words()).isEqualTo(expected);
  }

  @Then("it does not edit {string}")
  public void doesNotEdit(String words) {
    var runs = runs(difference().shown());
    assertThat(runs.stream().filter(run -> run.mark().isEmpty()).map(Run::text))
        .as("the words without edits")
        .anyMatch(text -> said(text).contains(said(words)));
    assertThat(runs.stream().filter(run -> !run.mark().isEmpty()).map(Run::text))
        .as("the edited words")
        .noneMatch(text -> said(text).contains(said(words)));
  }

  @Then("it inserts {string}")
  public void inserts(String words) {
    assertThat(edited(INSERTED)).as("the inserted words").contains(said(words));
  }

  @Then("it deletes {string}")
  public void deletes(String words) {
    assertThat(edited(DELETED)).as("the deleted words").contains(said(words));
  }

  @Then("it deletes")
  public void deletesPassage(String words) {
    deleted = words;
    deletes(words);
  }

  @Then("it deletes {string}, and inserts {string} just after it")
  public void deletesThenInserts(String deleted, String inserted) {
    this.deleted = deleted;
    insertsJustAfter(inserted);
  }

  @Then("it inserts {string} just after it")
  public void insertsJustAfter(String inserted) {
    var before = Objects.requireNonNull(deleted, "no deleted words given");
    var runs = runs(difference().shown());
    var found = false;
    for (var i = 0; i + 1 < runs.size(); i++) {
      var run = runs.get(i);
      var next = runs.get(i + 1);
      if (run.mark().equals(DELETED)
          && said(run.text()).equals(said(before))
          && next.mark().equals(INSERTED)
          && said(next.text()).startsWith(said(inserted))) {
        found = true;
        break;
      }
    }
    assertThat(found)
        .as("\"%s\" deleted, then \"%s\" inserted, in %s", before, inserted, runs)
        .isTrue();
  }

  @Then("it {word} all its words")
  public void allWords(String how) {
    var mark = how.equals("inserts") ? INSERTED : DELETED;
    var runs = runs(difference().shown());
    assertThat(
            runs.stream()
                .filter(run -> run.text().codePoints().anyMatch(Character::isLetterOrDigit)))
        .as("the runs with words")
        .isNotEmpty()
        .allMatch(run -> run.mark().equals(mark));
  }

  @Then("it inserts {int} words")
  public void insertsWords(int words) {
    assertThat(difference().inserted()).as("the inserted words").isEqualTo(words);
  }

  @Then("it shows the footnotes that it refers to, under it, with their edits")
  public void footnotes() {
    var footnotes =
        difference().shown().children().stream()
            .filter(child -> child.name().equals("footnotes"))
            .toList();
    assertThat(footnotes).as("the footnotes under it").hasSize(1);
    assertThat(runs(footnotes.getFirst()))
        .as("the edits of its footnotes")
        .anyMatch(run -> !run.mark().isEmpty());
  }

  @Then("its footnote is footnote {int} in the earlier text and footnote {int} in the later text")
  public void footnoteNumbers(int before, int after) {
    var place = Objects.requireNonNull(shownAt, "no place given");
    assertThat(markers(Occurrences.of(comparing.earlier()), place))
        .as("in the earlier text")
        .containsExactly(String.valueOf(before));
    assertThat(markers(Occurrences.of(comparing.later()), place))
        .as("in the later text")
        .containsExactly(String.valueOf(after));
  }

  private @Nullable Place shownAt;

  @Then("{place} does not differ, though its footnote has another number")
  public void doesNotDifferThoughRenumbered(Place place) {
    shownAt = place;
    doesNotDiffer(place);
  }

  @Then("it shows the occurrences in it whole, and none of them is a difference of its own")
  public void showsWhole() {
    var eId = difference().eId();
    assertThat(Occurrences.holdsOccurrences(difference().shown()))
        .as("it shows its occurrences")
        .isTrue();
    assertThat(compared().differences())
        .extracting(Difference::eId)
        .noneMatch(each -> each.startsWith(eId + "/"));
  }

  @Then(
      "it shows only its own content, and the occurrences in it that differ are differences of"
          + " their own")
  public void showsOwnContent() {
    var eId = difference().eId();
    assertThat(Occurrences.holdsOccurrences(difference().shown()))
        .as("it shows its occurrences")
        .isFalse();
    assertThat(compared().differences())
        .extracting(Difference::eId)
        .anyMatch(each -> each.startsWith(eId + "/"));
  }

  @Then(
      "the Differences view places each occurrence of the later text once, in the order of the"
          + " text")
  public void placedOnce() {
    var occurrences = Occurrences.of(comparing.later());
    var placed = new ArrayList<String>();
    for (var each : compared().differences()) {
      placed(each.context(), occurrences, placed);
      if (each.kind() != Kind.DELETED) {
        placed.add(each.eId());
        if (Occurrences.holdsOccurrences(each.shown())) {
          placed.addAll(inside(each.eId(), occurrences));
        }
      }
    }
    placed(compared().closing(), occurrences, placed);
    assertThat(placed).containsExactlyElementsOf(occurrences.byEId().keySet());
  }

  /**
   * Adds the occurrences of {@code context}: an opening alone, or the occurrences that are the same
   * in both texts with the occurrences in them.
   */
  private static void placed(
      List<Difference.Context> context, Occurrences occurrences, List<String> placed) {
    for (var each : context) {
      for (var occurrence : each.occurrences()) {
        placed.add(occurrence.eId());
        if (each.opening() == null) {
          placed.addAll(inside(occurrence.eId(), occurrences));
        }
      }
    }
  }

  /**
   * Returns the eIds of the occurrences in the occurrence {@code eId}, in the order of the text.
   */
  private static List<String> inside(String eId, Occurrences occurrences) {
    var inside = new ArrayList<String>();
    for (var occurrence : occurrences.in(eId)) {
      inside.add(occurrence);
      inside.addAll(inside(occurrence, occurrences));
    }
    return inside;
  }

  @Then("no indent of a footnote is a difference of its own")
  public void noFootnoteIndentDiffers() {
    // A footnote compares with the occurrence that refers to it, so nothing in a
    // footnote is a difference of its own. A footnote's eId is its place, note_N.
    assertThat(compared().differences())
        .extracting(Difference::eId)
        .noneMatch(eId -> eId.startsWith("note_"));
  }

  @Then("the later text has no preamble")
  public void noPreamble() {
    assertThat(compared().withoutPreamble()).isEqualTo(Side.LATER);
  }

  @Then("no citation and no recital differs")
  public void preambleNotCompared() {
    assertThat(compared().differences())
        .extracting(Difference::eId)
        .noneMatch(eId -> eId.contains("/cit_") || eId.contains("/rct_"));
  }

  @Then("it inserts a formula whole")
  public void formula() {
    assertThat(wholeFormulas(difference().shown())).as("the formulas inserted whole").isPositive();
  }

  @Then("it stands first in {place}")
  public void standsFirstIn(Place place) {
    var stands = Objects.requireNonNull(difference().stands(), "it stands at the end of the text");
    var later = Occurrences.of(comparing.later());
    assertThat(stands.after()).as("before the first occurrence in it").isFalse();
    assertThat(Objects.requireNonNull(later.byEId().get(stands.eId())).parent())
        .as(stands.eId())
        .isEqualTo(Comparing.eId(place, later));
  }

  @Then("it stands after {place}")
  public void standsAfter(Place place) {
    var stands = Objects.requireNonNull(difference().stands(), "it stands at the end of the text");
    assertThat(stands)
        .isEqualTo(
            new Difference.Standing(Comparing.eId(place, Occurrences.of(comparing.later())), true));
  }

  @Then("the next difference is an inserted occurrence in {place}")
  public void next(Place place) {
    var differences = compared().differences();
    var at = differences.indexOf(difference());
    assertThat(at + 1).as("a difference after it").isLessThan(differences.size());
    var after = differences.get(at + 1);
    assertThat(after.kind()).as(after.eId()).isEqualTo(Kind.INSERTED);
    assertThat(after.parent())
        .as(after.eId())
        .isEqualTo(Comparing.eId(place, Occurrences.of(comparing.later())));
  }

  /**
   * Returns {@code text} as the steps compare it: each run of spacing as one space, no spaces at
   * its ends.
   */
  private static String said(String text) {
    return text.replaceAll("[\\s\\u00A0]+", " ").strip();
  }

  private static final String INSERTED = "+";
  private static final String DELETED = "-";

  /** A run of the words that a difference shows: inserted (+), deleted (-), or without edits. */
  private record Run(String mark, String text) {}

  /** Returns the texts of the runs of {@code mark}, each without the spaces at its ends. */
  private List<String> edited(String mark) {
    return runs(difference().shown()).stream()
        .filter(run -> run.mark().equals(mark))
        .map(run -> said(run.text()))
        .toList();
  }

  /**
   * Returns the words of {@code element} in order, as runs. Runs of one kind next to each other
   * join.
   */
  private static List<Run> runs(Element element) {
    var runs = new ArrayList<Run>();
    walk(element, "", runs);
    return runs;
  }

  private static void walk(Element element, String mark, List<Run> runs) {
    var name = element.name();
    var at = name.equals(Edits.INSERTED) ? INSERTED : name.equals(Edits.DELETED) ? DELETED : mark;
    if (name.equals("footnotes") && mark.isEmpty() && !runs.isEmpty()) {
      // The footnotes stand under the occurrence: their words are not next to its own words.
      runs.add(new Run("", "\n"));
    }
    if (name.equals("quote")) {
      add(runs, at, element.attributes().getOrDefault("startQuote", ""));
      element.children().forEach(child -> walk(child, at, runs));
      add(runs, at, element.attributes().getOrDefault("endQuote", ""));
      return;
    }
    if (name.equals("math")) {
      add(runs, at, "[formula]");
      return;
    }
    if (name.equals("t") || name.equals("number")) {
      add(runs, at, element.attributes().getOrDefault("v", ""));
    }
    element.children().forEach(child -> walk(child, at, runs));
  }

  private static void add(List<Run> runs, String mark, String text) {
    if (text.isEmpty()) {
      return;
    }
    var last = runs.isEmpty() ? null : runs.getLast();
    if (last != null && last.mark().equals(mark)) {
      runs.set(runs.size() - 1, new Run(mark, last.text() + text));
    } else {
      runs.add(new Run(mark, text));
    }
  }

  /**
   * Returns how many formulas in {@code element} are inserted whole: the formula alone in an
   * insertion.
   */
  private static int wholeFormulas(Element element) {
    var found = 0;
    if (element.name().equals(Edits.INSERTED)
        && element.children().size() == 1
        && element.children().getFirst().name().equals("math")) {
      found++;
    }
    for (var child : element.children()) {
      found += wholeFormulas(child);
    }
    return found;
  }

  /**
   * Returns the markers of the footnotes that the occurrence {@code place} of {@code text} refers
   * to.
   */
  private static List<String> markers(Occurrences text, Place place) {
    var occurrence = Objects.requireNonNull(text.get(Comparing.eId(place, text)), place.words());
    return occurrence.footnotes().stream()
        .map(footnote -> Objects.requireNonNullElse(footnote.attribute("marker"), ""))
        .toList();
  }

  private Differ.Compared compared() {
    if (compared == null) {
      compared = Differ.differences(comparing.earlier(), comparing.later());
    }
    return Objects.requireNonNull(compared);
  }

  private Difference difference() {
    return Objects.requireNonNull(difference, "no difference read");
  }
}
