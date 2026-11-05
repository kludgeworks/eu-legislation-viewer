// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.PreparationChanged;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.ComparisonWanted;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionBuilt;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionDeferred;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionFailed;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionStarted;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Ingest;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The versions' texts as they stand, kept by the module as ingest's events say (each in its own
 * transaction): queued, being built, queued again after a failed attempt, built, failed. Each
 * change to one is its act's PreparationChanged. Preparing an act queues its texts and schedules
 * those not settled; what ingest says of each puts right any whose event isn't kept yet, or was
 * lost. A text built whose version's neighbours in the language are built too asks for their
 * comparisons ahead of a reader (ComparisonWanted): with the version before, and after.
 */
@Service
class TextService implements Texts {
  private static final Logger LOG = LoggerFactory.getLogger(TextService.class);

  private final ExpressionStore expressions;
  private final ActStore acts;
  private final Ingest ingest;
  private final ApplicationEventPublisher events;

  TextService(
      ExpressionStore expressions, ActStore acts, Ingest ingest, ApplicationEventPublisher events) {
    this.expressions = expressions;
    this.acts = acts;
    this.ingest = ingest;
    this.events = events;
  }

  @Override
  @Transactional
  public Preparation prepare(Act act, Language language) {
    var celexes = celexes(act, language);
    expressions.queue(celexes, language);
    var changed = false;
    for (var text : expressions.texts(celexes, language).entrySet()) {
      if (!text.getValue().settled()) {
        changed |= schedule(text.getKey(), language);
      }
    }
    if (changed) {
      events.publishEvent(new PreparationChanged(act.id(), language));
    }
    return preparation(act, language);
  }

  @Override
  public Preparation preparation(Act act, Language language) {
    var versions = versions(act, language);
    var kept = expressions.texts(versions.stream().map(Version::celex).toList(), language);
    var texts = new LinkedHashMap<Version, Text>();
    for (var version : versions) {
      texts.put(version, kept.getOrDefault(version.celex(), new Text.Queued()));
    }
    return new Preparation(act.id(), language, texts);
  }

  /** The text scheduled, and put right as ingest says it stands: whether that changed it. */
  private boolean schedule(String celex, Language language) {
    var status = ingest.schedule(celex, language);
    return switch (status.state()) {
      case QUEUED -> expressions.defer(celex, language, status.attempts());
      case RUNNING -> expressions.start(celex, language, status.attempts());
      case BUILT -> {
        LOG.debug("{} in {} is built, not yet kept", celex, language.code());
        yield expressions.keep(
            celex,
            language,
            Objects.requireNonNull(status.root()),
            Objects.requireNonNull(status.converter()));
      }
      case FAILED -> expressions.fail(celex, language, Objects.requireNonNull(status.failure()));
    };
  }

  @ApplicationModuleListener
  void on(ExpressionStarted started) {
    if (expressions.start(started.celex(), started.language(), started.attempt())) {
      LOG.debug(
          "{} in {} is being built, attempt {}",
          started.celex(),
          started.language().code(),
          started.attempt());
      changed(started.celex(), started.language());
    }
  }

  @ApplicationModuleListener
  void on(ExpressionDeferred deferred) {
    if (expressions.defer(deferred.celex(), deferred.language(), deferred.attempt())) {
      LOG.debug(
          "{} in {} is queued again after attempt {}",
          deferred.celex(),
          deferred.language().code(),
          deferred.attempt());
      changed(deferred.celex(), deferred.language());
    }
  }

  @ApplicationModuleListener
  void on(ExpressionFailed failed) {
    if (expressions.fail(failed.celex(), failed.language(), failed.failure())) {
      LOG.debug(
          "{} in {} couldn't be built: {}",
          failed.celex(),
          failed.language().code(),
          failed.failure());
      changed(failed.celex(), failed.language());
    }
  }

  @ApplicationModuleListener
  void on(ExpressionBuilt built) {
    var act = acts.findByVersion(built.celex());
    if (expressions.keep(built.celex(), built.language(), built.root(), built.converter())) {
      LOG.info("{} in {} is now {}", built.celex(), built.language().code(), built.root());
      act.ifPresent(
          each -> events.publishEvent(new PreparationChanged(each.id(), built.language())));
    }
    var timeline = act.flatMap(each -> each.timeline().in(built.language()));
    if (timeline.isEmpty()) {
      return;
    }
    var version =
        timeline.get().versions().stream()
            .filter(each -> each.celex().equals(built.celex()))
            .findFirst()
            .orElseThrow();
    timeline
        .get()
        .predecessor(version)
        .flatMap(before -> expressions.root(before.celex(), built.language()))
        .ifPresent(before -> want(before, built.root()));
    timeline
        .get()
        .successor(version)
        .flatMap(after -> expressions.root(after.celex(), built.language()))
        .ifPresent(after -> want(built.root(), after));
  }

  /** The text changed: its act's preparation in the language did. */
  private void changed(String celex, Language language) {
    acts.findByVersion(celex)
        .ifPresent(act -> events.publishEvent(new PreparationChanged(act.id(), language)));
  }

  private void want(ContentHash from, ContentHash to) {
    LOG.debug("Comparison of {} with {} wanted", from, to);
    events.publishEvent(new ComparisonWanted(from, to));
  }

  private static List<Version> versions(Act act, Language language) {
    return act.timeline().in(language).map(Timeline::versions).orElseGet(List::of);
  }

  private static List<String> celexes(Act act, Language language) {
    return versions(act, language).stream().map(Version::celex).toList();
  }
}
