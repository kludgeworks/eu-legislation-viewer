// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView.Choice;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Tests the head of the page of 2015/1998, made by {@link MastheadView}. The data is Cellar's
 * answer, recorded. The versions are in 23 languages, without Irish. The titles are in the same 23
 * languages.
 */
class MastheadViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));
  static final Version VERSION =
      AVSEC.timeline().startingOn(LocalDate.of(2021, 12, 31)).orElseThrow();
  static final LocalDate ASKED = LocalDate.of(2022, 1, 10);

  @Test
  void titlesTheActInThePagesLanguageAndLinksItsIdsToEurLex() {
    var head = MastheadView.reading(AVSEC, VERSION, ASKED, Language.FRA);
    assertThat(head.title()).startsWith("Règlement d'exécution (UE) 2015/1998 de la Commission");
    assertThat(head.titleLanguage()).isEqualTo("fr");
    assertThat(head.eli()).isEqualTo("eli/reg_impl/2015/1998/oj");
    assertThat(head.eliHref()).isEqualTo("https://eur-lex.europa.eu/eli/reg_impl/2015/1998/oj");
    assertThat(head.celexHref())
        .isEqualTo("https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:32015R1998");
  }

  @Test
  void splitsAnEnglishTitleAfterItsDate() {
    var head = MastheadView.reading(AVSEC, VERSION, ASKED, Language.ENG);
    assertThat(head.title())
        .isEqualTo("Commission Implementing Regulation (EU) 2015/1998 of 5 November 2015");
    assertThat(head.subject()).startsWith("laying down detailed measures");
    assertThat(MastheadView.reading(AVSEC, VERSION, ASKED, Language.FRA).subject()).isNull();
  }

  @Test
  void titlesItInEnglishWhereCellarHasNoTitleInTheLanguage() {
    var head = MastheadView.reading(AVSEC, VERSION, ASKED, Language.GLE);
    assertThat(head.title()).startsWith("Commission Implementing Regulation (EU) 2015/1998");
    assertThat(head.titleLanguage()).isEqualTo("en");
  }

  @Test
  void listsAllTheLanguagesInTheEusOrderGreyingThoseTheVersionIsntIn() {
    var head = MastheadView.reading(AVSEC, VERSION, ASKED, Language.FRA);
    assertThat(head.language()).isEqualTo("français");
    assertThat(head.languages())
        .hasSize(24)
        .extracting(Choice::iso)
        .startsWith("bg", "es", "cs")
        .endsWith("fi", "sv");
    assertThat(head.languages())
        .filteredOn(Choice::current)
        .singleElement()
        .extracting(Choice::name, Choice::href)
        .containsExactly("français", "/eli/reg_impl/2015/1998/2022-01-10/fra");
    assertThat(head.languages())
        .filteredOn(choice -> choice.iso().equals("de"))
        .singleElement()
        .extracting(Choice::name, Choice::href)
        .containsExactly("Deutsch", "/eli/reg_impl/2015/1998/2022-01-10/deu");
    assertThat(head.languages())
        .filteredOn(choice -> choice.missing() != null)
        .singleElement()
        .extracting(Choice::name, Choice::href, Choice::missing)
        .containsExactly("Gaeilge", null, "Not for this version");
  }

  @Test
  void greysALanguageEitherVersionComparedIsntInSayingWhich() {
    var from = AVSEC.timeline().versions().getFirst();
    var head = MastheadView.comparing(AVSEC, from, VERSION, Language.ENG);
    assertThat(head.languages())
        .filteredOn(choice -> choice.missing() != null)
        .extracting(Choice::name, Choice::missing)
        .contains(org.assertj.core.groups.Tuple.tuple("Gaeilge", "Neither version is in Irish"));
  }
}
