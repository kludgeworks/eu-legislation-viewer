// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.LawOverTime;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Laws;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextTags;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheTextAndTheLawOnADay;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
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
    return TheTextAndTheLawOnADay.versionOfADay(act.timeline(), day, language);
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
