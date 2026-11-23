// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attributed;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attribution;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attributions;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Finds who made each difference of a comparison. The comparison comes from {@link Comparisons},
 * the outlines of its texts from the content module, and the acts and their dates from the
 * catalogue's {@link Act}. The rules are the domain's: {@link Attributed#of}.
 */
@Service
class AttributionService implements Attributions {
  private final Comparisons comparisons;
  private final Content content;
  private final Texts texts;

  AttributionService(Comparisons comparisons, Content content, Texts texts) {
    this.comparisons = comparisons;
    this.content = content;
    this.texts = texts;
  }

  @Override
  public Optional<Compared> of(
      Act act,
      Language language,
      Preparation preparation,
      Inclusions included,
      Timeline timeline,
      Version from,
      Version to,
      @Nullable LocalDate day) {
    if (!(preparation.texts().get(from) instanceof Text.Ready earlier)
        || !(preparation.texts().get(to) instanceof Text.Ready later)) {
      return Optional.empty();
    }
    var comparison = comparisons.between(earlier.text(), later.text());
    if (comparison.isEmpty()) {
      return Optional.empty();
    }
    var before = content.outline(earlier.text()).orElseGet(Outline::empty);
    var outline = content.outline(later.text()).orElseGet(Outline::empty);
    var amended = act.amended(from, to, before.eIds(), outline.eIds(), language);
    var found = new ArrayList<Attributed>();
    for (var difference : comparison.get().differences()) {
      found.add(
          Attributed.of(
              act, included, from, to, difference, Attribution.amendmentsOf(amended, difference)));
    }
    var unattributed =
        found.stream()
            .filter(each -> each.attribution().unattributed())
            .map(each -> each.difference().eId())
            .collect(Collectors.toSet());
    var appeared = comparisons.appeared(timeline, preparation.texts(), from, to, unattributed);
    var prospective =
        day == null ? null : act.prospective(to, day, language, texts.tags(preparation));
    var each = new ArrayList<Attributed>();
    for (var attributed : found) {
      var eId = attributed.difference().eId();
      var version = attributed.attribution().unattributed() ? appeared.get(eId) : null;
      var placed = version == null ? attributed : attributed.appearedIn(version, included);
      each.add(prospective == null ? placed : placed.notApplying(prospective));
    }
    return Optional.of(
        new Compared(comparison.get(), later.text(), before, outline, amended, each));
  }
}
