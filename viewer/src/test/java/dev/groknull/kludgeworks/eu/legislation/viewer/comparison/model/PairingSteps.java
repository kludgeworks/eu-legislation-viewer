// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Modification;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

  private @Nullable Element earlier;
  private @Nullable Element later;
  private @Nullable Difference difference;

  @Given(
      "the texts of the Council Resolution on a Model Agreement for a Joint Investigation Team"
          + " of {day} and of {day}")
  public void resolution(LocalDate from, LocalDate to) {
    earlier = sample(RESOLUTION, from);
    later = sample(RESOLUTION, to);
  }

  @Then("{place} of the later text pairs with {place} of the earlier text")
  public void pairs(Place is, Place was) {
    var before = Occurrences.of(earlier());
    var after = Occurrences.of(later());
    var paired = Pairing.of(before, after).was(Objects.requireNonNull(after.get(eId(is, after))));
    assertThat(paired).as(is.words()).isNotNull();
    assertThat(Objects.requireNonNull(paired).eId()).as(is.words()).isEqualTo(eId(was, before));
  }

  @Then("{place} of the later text has no pair in the earlier text")
  public void pairsWithNone(Place is) {
    var before = Occurrences.of(earlier());
    var after = Occurrences.of(later());
    assertThat(Pairing.of(before, after).was(Objects.requireNonNull(after.get(eId(is, after)))))
        .as(is.words())
        .isNull();
  }

  @When("the reader looks at the difference at the {word} {place}")
  public void difference(String kind, Place place) {
    var expected = Kind.valueOf(kind.toUpperCase(Locale.ROOT));
    var text = Occurrences.of(expected == Kind.DELETED ? earlier() : later());
    var eId = eId(place, text);
    difference =
        Differ.differences(earlier(), later()).differences().stream()
            .filter(each -> each.eId().equals(eId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No difference at " + place.words()));
    assertThat(Objects.requireNonNull(difference).kind()).as(place.words()).isEqualTo(expected);
  }

  @Then("the point has the same place in both texts")
  public void sameEId() {
    var shown = Objects.requireNonNull(difference, "no difference given");
    assertThat(Occurrences.of(earlier()).get(shown.eId())).as(shown.eId()).isNotNull();
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

  @Then("its tags name {act}, which {word} it")
  public void tagged(ActIdentifier act, String modification) {
    assertThat(Objects.requireNonNull(difference, "no difference given").tags())
        .extracting(Difference.Tag::act, Difference.Tag::modification)
        .contains(org.assertj.core.groups.Tuple.tuple(act.celex(), Modification.of(modification)));
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

  private Element earlier() {
    return earlier != null ? earlier : Comparing.text(Comparing.from());
  }

  private Element later() {
    return later != null ? later : Comparing.text(Comparing.to());
  }

  /** Returns the sample text of the act {@code celex} of {@code day}. */
  private static Element sample(String celex, LocalDate day) {
    var name =
        "0" + celex.substring(1) + "-" + day.format(DateTimeFormatter.BASIC_ISO_DATE) + ".xml";
    var path =
        LegalTexts.samples()
            .filter(each -> each.endsWith("/" + name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("The converter has no sample text " + name));
    return Decoder.element(Encoder.encode(LegalTexts.sample(path)));
  }

  /** Returns the eId of the one occurrence of {@code text} that is {@code place}. */
  private static String eId(Place place, Occurrences text) {
    var found =
        Location.of(place)
            .in(List.copyOf(text.byEId().keySet()))
            .orElseThrow(() -> new AssertionError("The text has no " + place.words()));
    assertThat(found.eIds()).as(place.words()).hasSize(1);
    assertThat(found.within()).as(place.words()).isFalse();
    return found.eIds().getFirst();
  }
}
