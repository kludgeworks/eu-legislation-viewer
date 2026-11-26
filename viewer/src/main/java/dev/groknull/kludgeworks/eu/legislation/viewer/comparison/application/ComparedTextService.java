// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amended;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline.TwoVersions;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Alternatives;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attribution;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attributions;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.ComparedTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparisons;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Ends;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.LastChange;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.TheChangesOfOneAct;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Versions;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.NeighbourFinder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Gives what the fragments of a comparison and the API show. It gets what the rules need and that a
 * request does not have: the act, the preparation of its texts, the comparison and the texts. The
 * rules are the domain's: {@link Ends#of}, {@link Attribution}, {@link Alternatives} and {@link
 * LastChange}.
 */
@Service
public class ComparedTextService implements ComparedTexts {
  private final Acts acts;
  private final Texts texts;
  private final Comparisons comparisons;
  private final Attributions attributions;
  private final Content content;

  public ComparedTextService(
      Acts acts, Texts texts, Comparisons comparisons, Attributions attributions, Content content) {
    this.acts = acts;
    this.texts = texts;
    this.comparisons = comparisons;
    this.attributions = attributions;
    this.content = content;
  }

  @Override
  public Optional<Ends> ends(
      Eli eli, Language language, LocalDate from, LocalDate to, @Nullable String only) {
    return acts.find(eli).flatMap(act -> ends(act, language, from, to, only));
  }

  @Override
  public Optional<Ends> ends(
      ActIdentifier celex, Language language, LocalDate from, LocalDate to, @Nullable String only) {
    return acts.find(celex).flatMap(act -> ends(act, language, from, to, only));
  }

  private Optional<Ends> ends(
      Act act, Language language, LocalDate from, LocalDate to, @Nullable String only) {
    var preparation = texts.preparation(act, language);
    return Ends.of(act, language, from, to, only, preparation, texts.tags(preparation));
  }

  @Override
  public Optional<TwoVersions> applyingOn(
      ActIdentifier celex, Language language, LocalDate from, LocalDate to) {
    return acts.find(celex).flatMap(act -> act.timeline().versionsOn(from, to, language));
  }

  @Override
  public Ends prepared(Ends ends) {
    var versions = ends.versions();
    return new Ends(
        ends.act(),
        new Versions(
            versions.language(),
            versions.timeline(),
            texts.prepare(ends.act(), versions.language()),
            versions.included()),
        ends.from(),
        ends.to(),
        ends.only(),
        ends.inclusion());
  }

  @Override
  public Optional<Attributions.Compared> attributed(Ends ends, @Nullable LocalDate day) {
    return attributions.of(ends.act(), ends.versions(), ends.earlier(), ends.later(), day);
  }

  @Override
  public Optional<AtAPlace> at(Ends ends, List<String> eIds) {
    if (!(ends.texts() instanceof Ends.Built built)) {
      return Optional.empty();
    }
    var comparison = comparisons.between(built.before(), built.after());
    if (comparison.isEmpty()) {
      return Optional.empty();
    }
    var found = TheChangesOfOneAct.differencesAtAPlace(comparison.get().differences(), eIds);
    var only = ends.only();
    if (!found.isEmpty() || only == null) {
      return Optional.of(new AtAPlace(found, null));
    }
    // A chapter that the annex keeps flat has no difference of its own.
    return attributed(ends, null)
        .map(attributed ->
            TheChangesOfOneAct.chapterTheAnnexKeepsFlat(attributed.each(), eIds, only))
        .map(made -> new AtAPlace(made, made.isEmpty() ? null : only));
  }

  @Override
  public Optional<Alternatives> either(Ends ends) {
    var only = ends.only();
    return only == null
        ? Optional.empty()
        : attributed(ends, null)
            .map(attributed -> TheChangesOfOneAct.thisActOrAnother(only, attributed.each()));
  }

  @Override
  public Optional<Amended> amended(Ends ends) {
    if (!(ends.texts() instanceof Ends.Built built)) {
      return Optional.empty();
    }
    return Optional.of(
        ends.act()
            .amended(
                built.earlier(),
                built.later(),
                content.outline(built.before()).orElseGet(Outline::empty).eIds(),
                content.outline(built.after()).orElseGet(Outline::empty).eIds(),
                ends.language()));
  }

  @Override
  public Optional<List<Unchanged>> unchanged(Ends ends, Collection<String> eIds) {
    return later(ends).map(text -> unchanged(ends, text, List.copyOf(eIds)));
  }

  @Override
  public Optional<Fold> unchanged(Ends ends, String first, String last) {
    return later(ends)
        .map(text -> {
          var eIds = new ArrayList<String>();
          for (var occurrence : text.occurrences(first, last)) {
            eIds.add(Objects.requireNonNull(occurrence.attribute("eId")));
          }
          return new Fold(unchanged(ends, text, eIds), eIds.size());
        });
  }

  /** Returns the later text of the comparison, if it is built. */
  private Optional<Element> later(Ends ends) {
    return ends.preparation().texts().get(ends.to()) instanceof Text.Ready later
        ? content.read(later.text())
        : Optional.empty();
  }

  private static List<Unchanged> unchanged(Ends ends, Element text, List<String> eIds) {
    var found = NeighbourFinder.of(text, Set.copyOf(eIds));
    var unchanged = new ArrayList<Unchanged>();
    for (var eId : eIds) {
      var neighbour = found.get(eId);
      if (neighbour != null) {
        unchanged.add(
            new Unchanged(
                eId,
                neighbour.occurrence(),
                LastChange.of(ends.act(), ends.included(), ends.to(), neighbour.tags())));
      }
    }
    return unchanged;
  }
}
