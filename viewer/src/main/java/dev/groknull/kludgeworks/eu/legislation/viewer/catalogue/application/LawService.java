// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.DeferredApplication;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Divergence;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.LawOnADay;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.LawOverTime;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Laws;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextTags;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheLaw;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheLaw.Deferred;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheLaw.Diverged;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Gives the text and the law of an act over time, and keeps what it worked out. It decides nothing
 * about the law: {@link LawOverTime} applies the rules.
 */
@Service
public class LawService implements Laws {

  /**
   * The occurrences in all the texts that {@link #known} keeps, at most. Each is an eId of some
   * tens of characters, so the keys keep about 50 MB at most.
   */
  private static final long OCCURRENCES_KEPT = 500_000;

  private final @Nullable Texts texts;

  public LawService(ObjectProvider<Texts> texts) {
    this.texts = texts.getIfAvailable();
  }

  /**
   * What was worked out, by what it was worked out from. A page asks two to four times for one
   * request, and each request again: to find the divergences is the most work of a page. The key is
   * the act and the texts themselves, so a change to either works it out again.
   */
  private final Cache<Key, LawOverTime> known =
      Caffeine.newBuilder()
          .maximumWeight(OCCURRENCES_KEPT)
          .weigher((Key key, LawOverTime found) -> key.occurrences())
          .expireAfterAccess(Duration.ofHours(1))
          .build();

  @Override
  public LawOverTime of(Act act, Language language, Map<Version, TextTags> texts) {
    return known.get(
        new Key(act, language, Map.copyOf(texts)),
        key -> LawOverTime.of(key.act(), key.language(), key.texts()));
  }

  @Override
  public Optional<Version> versionOn(Act act, LocalDate day, Language language) {
    return act.timeline().applicableOn(day, language);
  }

  @Override
  public Optional<TheLaw> on(Act act, LocalDate day) {
    var timeline = act.timeline();
    var applying = timeline.allApplicableOn(day);
    if (applying.isEmpty()) {
      return Optional.empty();
    }
    var until = timeline.nextStartAfter(day).orElse(null);
    var deferred = deferredApplication(act, applying, day);
    var divergences = divergences(act, applying, day);
    return Optional.of(new TheLaw(act, day, applying, until, deferred, divergences));
  }

  private @Nullable Deferred deferredApplication(Act act, List<Version> applying, LocalDate date) {
    var languages = new LinkedHashMap<DeferredApplication, List<Language>>();
    applying.stream()
        .flatMap(version -> version.languages().stream())
        .distinct()
        .sorted()
        .forEach(language ->
            Optional.ofNullable(of(act, language, built(act, language)).on(date))
                .map(LawOnADay::deferredApplication)
                .ifPresent(each ->
                    languages.computeIfAbsent(each, key -> new ArrayList<>()).add(language)));
    return languages.entrySet().stream()
        .findFirst()
        .map(each -> new Deferred(each.getKey(), each.getValue()))
        .orElse(null);
  }

  private List<Diverged> divergences(Act act, List<Version> applying, LocalDate date) {
    var languages = new LinkedHashMap<Divergence, List<Language>>();
    applying.stream()
        .flatMap(version -> version.languages().stream())
        .distinct()
        .sorted()
        .forEach(language ->
            of(act, language, built(act, language))
                .on(date)
                .divergences()
                .forEach(each ->
                    languages.computeIfAbsent(each, key -> new ArrayList<>()).add(language)));
    return languages.entrySet().stream()
        .map(each -> new TheLaw.Diverged(each.getKey(), each.getValue()))
        .toList();
  }

  private Map<Version, TextTags> built(Act act, Language language) {
    return texts == null ? Map.of() : texts.tags(texts.preparation(act, language));
  }

  /**
   * What the answer is worked out from.
   *
   * @param occurrences the number of occurrences in the texts, which tells how much memory the key
   *     keeps
   */
  private record Key(Act act, Language language, Map<Version, TextTags> texts, int occurrences) {

    Key(Act act, Language language, Map<Version, TextTags> texts) {
      this(
          act, language, texts, texts.values().stream().mapToInt(text -> text.eIds().size()).sum());
    }
  }
}
