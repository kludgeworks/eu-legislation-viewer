// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextTags;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests giving the law of an act over time and on a day through {@link LawService}. */
class LawServiceTest {

  private final FakeTexts texts = new FakeTexts();
  private final LawService service = new LawService(texts);

  @Test
  void answersEmptyWhenNoVersionApplies() {
    var law = service.on(OneActCellar.ACT, LocalDate.of(2015, 11, 10));

    assertThat(law).isEmpty();
  }

  @Test
  void answersTheLawOnADayWithApplyingVersionsAndUntil() {
    var day = LocalDate.of(2022, 2, 14);

    var law = service.on(OneActCellar.ACT, day);

    assertThat(law).isPresent();
    assertThat(law.get().day()).isEqualTo(day);
    assertThat(law.get().until()).isEqualTo(LocalDate.of(2026, 3, 22));
    assertThat(law.get().applying()).isNotEmpty();
    assertThat(law.get().deferred()).isNull();
  }

  private static final class FakeTexts implements Texts {
    Map<Version, TextTags> heldTags = Map.of();

    @Override
    public Preparation prepare(Act act, Language language) {
      return preparation(act, language);
    }

    @Override
    public void retry(Act act, Language language) {}

    @Override
    public Preparation preparation(Act act, Language language) {
      return new Preparation(
          act.id(), language, Instant.EPOCH, Instant.EPOCH, new LinkedHashMap<>(), List.of(), 0);
    }

    @Override
    public Map<Version, TextTags> tags(Preparation preparation) {
      return heldTags;
    }
  }
}
