// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextTags;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests the words of the head of the Compare page of 2015/1998. The versions, the dates and the
 * changes are Cellar's, recorded. 2017/815 and 2019/103 each replaced point 4.1.3.1 of the Annex.
 */
class CompareViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));
  static final LocalDate TODAY = LocalDate.of(2026, 11, 17);
  static final String POINT = "anx/pnt_4.1.3.1";
  static final List<String> OCCURRENCES =
      List.of("anx", "anx/pnt_4", "anx/pnt_4.1", "anx/pnt_4.1.3", POINT);
  static final Version FROM = AVSEC.timeline().startingOn(LocalDate.of(2016, 2, 29)).orElseThrow();
  static final Version TO = AVSEC.timeline().startingOn(LocalDate.of(2017, 6, 7)).orElseThrow();
  static final Version LATEST =
      AVSEC.timeline().startingOn(LocalDate.of(2026, 10, 1)).orElseThrow();
  static final LocalDate FROM_DAY = LocalDate.of(2016, 3, 15);
  static final LocalDate TO_DAY = LocalDate.of(2017, 6, 3);

  @Test
  void showsTheTwoDaysAsTheReaderGaveThemAndTheAmendingActsBetweenThem() {
    var view =
        CompareView.of(AVSEC, FROM, FROM_DAY, TO, TO_DAY, null, Language.ENG, TODAY, Map.of());

    assertThat(view.heading()).isEqualTo("2015/1998 on two days");
    assertThat(view.days()).isEqualTo("From 15 March 2016 to 3 June 2017.");
    assertThat(view.picker().from()).isEqualTo("2016-03-15");
    assertThat(view.picker().on()).isEqualTo("2017-06-03");
    assertThat(view.picker().single()).isEqualTo("/eli/reg_impl/2015/1998/eng/history");
    assertThat(view.picker().range()).isEqualTo("/eli/reg_impl/2015/1998/eng/compare");
    assertThat(view.said()).isEqualTo("1 amending act changed 2015/1998 between these days:");
    assertThat(view.acts())
        .containsExactly(
            new CompareView.Link(
                "2017/815", "/eli/reg_impl/2015/1998/eng/history/32017R0815?on=2017-06-03", "."));
    assertThat(view.whole()).isNull();
    assertThat(view.steps()).isEmpty();
  }

  @Test
  void saysThatNoAmendingActChangedTheActWhereTheTwoDaysHaveOneText() {
    var view =
        CompareView.of(AVSEC, FROM, FROM_DAY, FROM, TO_DAY, null, Language.ENG, TODAY, Map.of());

    assertThat(view.said()).isEqualTo("No amending act changed 2015/1998 between these days.");
    assertThat(view.acts()).isEmpty();
  }

  @Test
  void listsTheStepsOfOneProvisionOldestFirstAndLinksToTheWholeAct() {
    var view =
        CompareView.of(
            AVSEC,
            FROM,
            FROM_DAY,
            LATEST,
            TODAY,
            POINT,
            Language.ENG,
            TODAY,
            Map.of(LATEST, new TextTags(OCCURRENCES, Map.of())));

    assertThat(view.heading()).endsWith(" of 2015/1998 on two days").doesNotContain("A provision");
    assertThat(view.picker().occurrence()).isEqualTo(POINT);
    assertThat(view.said()).contains(" amending acts changed ").endsWith(" between these days:");
    assertThat(view.acts()).extracting(CompareView.Link::act).contains("2017/815", "2019/103");
    assertThat(view.acts().getLast().after()).isEqualTo(".");
    var own = view.steps().stream().filter(line -> line.words().equals("replaced it.")).toList();
    assertThat(own).extracting(ProvisionView.Line::act).containsExactly("2017/815", "2019/103");
    assertThat(view.whole())
        .isEqualTo("/eli/reg_impl/2015/1998/eng/compare?from=2016-03-15&to=2026-11-17");
  }

  @Test
  void aDayBeforeTheFirstTextHasTheDaysAndNoActs() {
    var view = CompareView.before(AVSEC, LocalDate.of(2000, 1, 1), TO_DAY, Language.ENG);

    assertThat(view.days()).isEqualTo("From 1 January 2000 to 3 June 2017.");
    assertThat(view.said()).isNull();
    assertThat(view.acts()).isEmpty();
  }
}
