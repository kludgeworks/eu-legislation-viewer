// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.CataloguedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparedLaw;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparedLaws;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Histories;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Laws;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Lookups;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline.TwoDays;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Today;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Gives what the Compare page shows. It gets what the rules need and that a request does not have:
 * the act, today, and the texts that are built. It decides nothing about the law.
 */
@Service
public class CompareService implements ComparedLaws {
  private final Lookups lookups;
  private final Texts texts;
  private final Laws laws;
  private final Histories histories;
  private final Today today;

  public CompareService(Lookups lookups, Texts texts, Laws laws, Histories histories, Today today) {
    this.lookups = lookups;
    this.texts = texts;
    this.laws = laws;
    this.histories = histories;
    this.today = today;
  }

  @Override
  public Optional<ComparedLaw> of(
      Eli eli,
      Language language,
      @Nullable LocalDate from,
      @Nullable LocalDate to,
      @Nullable String occurrence) {
    var act = CataloguedActs.act(lookups, eli);
    var overTime = histories.overTime(act, language);
    var inLanguage = act.timeline().in(language);
    if (inLanguage.isEmpty()) {
      return Optional.empty();
    }
    var timeline = inLanguage.get();
    var days = timeline.twoDays(from, to, today.today());
    if (!days.from().equals(from) || !days.to().equals(to)) {
      return Optional.of(new ComparedLaw.Elsewhere(days));
    }
    if (timeline.startsAfter(days.from())) {
      return Optional.of(
          new ComparedLaw.BeforeTheAct(overTime, days, timeline.endFromTheFirst(days.to())));
    }
    var preparation = texts.prepare(act, language);
    if (preparation.preparing()) {
      return Optional.of(new ComparedLaw.Preparing(overTime, days, preparation, today.now()));
    }
    var tags = texts.tags(preparation);
    var law = laws.of(act, language, tags);
    var earlier = law.on(days.from()).shown();
    var later = law.on(days.to()).shown();
    if (earlier == null || later == null) {
      return Optional.empty();
    }
    return Optional.of(
        new ComparedLaw.Prepared(
            overTime,
            days,
            preparation,
            earlier,
            later,
            histories.compared(overTime, earlier, later, occurrence, days.to(), tags),
            today.today()));
  }

  @Override
  public Optional<TwoDays> retry(Eli eli, Language language, LocalDate from, LocalDate to) {
    var act = CataloguedActs.act(lookups, eli);
    return act.timeline()
        .in(language)
        .map(timeline -> {
          texts.retry(act, language);
          return timeline.twoDays(from, to, today.today());
        });
  }
}
