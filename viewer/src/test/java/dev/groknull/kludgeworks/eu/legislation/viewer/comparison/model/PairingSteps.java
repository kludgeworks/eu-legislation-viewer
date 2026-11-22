// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Modification;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The steps of the rules about how the occurrences of two texts pair, and how a difference shows
 * the rows of a table (R-DIFF-09 to R-DIFF-14). The texts are the converter's sample texts of the
 * comparison ({@link Comparing}), or of the Council Resolution on a Model Agreement for setting up
 * a Joint Investigation Team, which has no number. The steps reach the comparison's domain only:
 * {@link Pairing}, {@link Differ} and {@link Occurrences}.
 */
public class PairingSteps {
  /** The CELEX of the Council Resolution on a Model Agreement for a Joint Investigation Team. */
  private static final String RESOLUTION = "32017G0119";

  private final Comparing comparing;
  private @Nullable Difference difference;

  public PairingSteps(Comparing comparing) {
    this.comparing = comparing;
  }

  @Given(
      "the texts of the Council Resolution on a Model Agreement for a Joint Investigation Team"
          + " of {day} and of {day}")
  public void resolution(LocalDate from, LocalDate to) {
    comparing.texts(
        Comparing.consolidated(new ActIdentifier(RESOLUTION), from),
        Comparing.consolidated(new ActIdentifier(RESOLUTION), to));
  }

  @Then("{place} of the later text pairs with {place} of the earlier text")
  public void pairs(Place is, Place was) {
    var before = Occurrences.of(comparing.earlier());
    var after = Occurrences.of(comparing.later());
    var paired =
        Pairing.of(before, after).was(Objects.requireNonNull(after.get(Comparing.eId(is, after))));
    assertThat(paired).as(is.words()).isNotNull();
    assertThat(Objects.requireNonNull(paired).eId())
        .as(is.words())
        .isEqualTo(Comparing.eId(was, before));
  }

  @Then("{place} of the later text has no pair in the earlier text")
  public void pairsWithNone(Place is) {
    var before = Occurrences.of(comparing.earlier());
    var after = Occurrences.of(comparing.later());
    assertThat(
            Pairing.of(before, after)
                .was(Objects.requireNonNull(after.get(Comparing.eId(is, after)))))
        .as(is.words())
        .isNull();
  }

  @When("the reader looks at the difference at the {word} {place}")
  public void difference(String kind, Place place) {
    var expected = Kind.valueOf(kind.toUpperCase(Locale.ROOT));
    var text = Occurrences.of(expected == Kind.DELETED ? comparing.earlier() : comparing.later());
    var eId = Comparing.eId(place, text);
    difference =
        Differ.differences(comparing.earlier(), comparing.later()).differences().stream()
            .filter(each -> each.eId().equals(eId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No difference at " + place.words()));
    assertThat(Objects.requireNonNull(difference).kind()).as(place.words()).isEqualTo(expected);
  }

  @Then("the point has the same place in both texts")
  public void sameEId() {
    var shown = Objects.requireNonNull(difference, "no difference given");
    assertThat(Occurrences.of(comparing.earlier()).get(shown.eId())).as(shown.eId()).isNotNull();
  }

  @Then("its {word} show, in order: {string}")
  public void show(String what, String expected) {
    var shown = Objects.requireNonNull(difference, "no difference given").shown();
    var pieces = new ArrayList<Element>();
    if (what.equals("rows")) {
      collect(shown, "tr", pieces);
    } else {
      for (var block : shown.children()) {
        if (!Set.of("occurrence", "num", "heading", "subheading").contains(block.name())) {
          pieces.add(block);
        }
      }
    }
    assertThat(pieces.stream().map(PairingSteps::shows).toList())
        .as(what)
        .containsExactly(expected.split(", "));
  }

  @Then("its tags name {act}, which {modification} it")
  public void tagged(ActIdentifier act, Modification modification) {
    assertThat(Objects.requireNonNull(difference, "no difference given").tags())
        .extracting(Difference.Tag::act, Difference.Tag::modification)
        .contains(org.assertj.core.groups.Tuple.tuple(act.celex(), modification));
  }

  /** Returns what a block or a row shows: inserted, deleted, changed or the same. */
  private static String shows(Element piece) {
    var words = new ArrayList<String>();
    marks(piece, "plain", words);
    var plain = words.contains("plain");
    var inserted = words.contains(Edits.INSERTED);
    var deleted = words.contains(Edits.DELETED);
    if (!inserted && !deleted) {
      return "the same";
    }
    if (!plain && !deleted) {
      return "inserted";
    }
    if (!plain && !inserted) {
      return "deleted";
    }
    return "changed";
  }

  /** Adds to {@code found} how each word of {@code element} shows: plain, inserted or deleted. */
  private static void marks(Element element, String mark, List<String> found) {
    var at =
        element.name().equals(Edits.INSERTED) || element.name().equals(Edits.DELETED)
            ? element.name()
            : mark;
    var words = element.attribute("v");
    if (words != null && !words.isBlank()) {
      found.add(at);
    }
    element.children().forEach(child -> marks(child, at, found));
  }

  private static void collect(Element element, String name, List<Element> found) {
    if (element.name().equals(name)) {
      found.add(element);
      return;
    }
    element.children().forEach(child -> collect(child, name, found));
  }
}
