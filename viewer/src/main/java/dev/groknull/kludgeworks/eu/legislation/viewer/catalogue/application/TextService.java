// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation.Step;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.PreparationChanged;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.PreparationCompleted;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.PreparationFailed;
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
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Ingest;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prepares an act in a language, and stores the state of the texts of its versions. It stores each
 * state as the events of ingest tell it, each event in its own transaction. A text can be queued,
 * being built, queued again after a failed attempt, built, or failed.
 *
 * <ul>
 *   <li>Each change to a text is a step of the preparation of its act. It publishes a {@link
 *       PreparationChanged} event.
 *   <li>The last text to settle settles the preparation, once. This publishes {@link
 *       PreparationCompleted}, or {@link PreparationFailed} if no text was built.
 *   <li>To prepare an act starts its preparation, queues its texts and schedules the texts that are
 *       not settled. What ingest says of each text corrects a text whose event is not stored yet,
 *       or was lost.
 *   <li>To try an act again queues its failed texts again, where that may help. It also starts the
 *       preparation again.
 *   <li>When a text is built, it asks for its comparison with the version before and with the
 *       version after, if their texts in the language are built too. It publishes a {@link
 *       ComparisonWanted} event for each, so the comparison is ready before a reader asks.
 * </ul>
 */
@Service
class TextService implements Texts {
  private static final Logger LOG = LoggerFactory.getLogger(TextService.class);

  private final ExpressionStore expressions;
  private final PreparationStore preparations;
  private final ActStore acts;
  private final Ingest ingest;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  TextService(
      ExpressionStore expressions,
      PreparationStore preparations,
      ActStore acts,
      Ingest ingest,
      ApplicationEventPublisher events,
      Clock clock) {
    this.expressions = expressions;
    this.preparations = preparations;
    this.acts = acts;
    this.ingest = ingest;
    this.events = events;
    this.clock = clock;
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
    // Start the preparation after the texts, because a change of a text locks the text first and
    // then the preparation.
    preparations.start(act.id(), language);
    changed |=
        preparations
            .lock(act.id(), language)
            .map(kept -> settle(act, language, kept))
            .orElse(false);
    if (changed) {
      events.publishEvent(new PreparationChanged(act.id(), language));
    }
    return preparation(act, language);
  }

  @Override
  @Transactional
  public void retry(Act act, Language language) {
    var retried = 0;
    for (var text : expressions.texts(celexes(act, language), language).entrySet()) {
      if (text.getValue() instanceof Text.Failed failed
          && failed.failure().retryable()
          && ingest.retry(text.getKey(), language)) {
        expressions.requeue(text.getKey(), language);
        retried++;
      }
    }
    if (retried == 0) {
      LOG.debug("Nothing of {} in {} to try again", act.id(), language.code());
      return;
    }
    // Restart the preparation after the texts, because a change of a text locks the text first
    // and then the preparation.
    preparations.restart(act.id(), language);
    LOG.info("Trying {} in {} again: {} versions", act.id(), language.code(), retried);
    events.publishEvent(new PreparationChanged(act.id(), language));
  }

  @Override
  public Preparation preparation(Act act, Language language) {
    var versions = versions(act, language);
    var celexes = versions.stream().map(Version::celex).toList();
    var kept = expressions.texts(celexes, language);
    var texts = new LinkedHashMap<Version, Text>();
    for (var version : versions) {
      texts.put(version, kept.getOrDefault(version.celex(), new Text.Queued()));
    }
    var preparation = preparations.find(act.id(), language);
    var waiting =
        texts.values().stream().noneMatch(Text.Building.class::isInstance)
            && texts.values().stream().anyMatch(Text.Queued.class::isInstance);
    return new Preparation(
        act.id(),
        language,
        preparation.map(PreparationStore.Kept::started).orElseGet(clock::instant),
        preparation.map(PreparationStore.Kept::settledAt).orElse(null),
        texts,
        preparations.steps(act.id(), language),
        waiting ? ingest.ahead(celexes, language) : 0);
  }

  /**
   * Schedules the text, and corrects its state as ingest says it stands. Returns whether that
   * changed the text.
   */
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
      // ingest keeps no trace id. A text that is failed already keeps the one it has.
      case FAILED ->
          expressions.fail(celex, language, Objects.requireNonNull(status.failure()), null);
    };
  }

  /**
   * Settles the act's preparation if every text is settled. {@code kept} is the preparation as
   * locked. It settles once, and the last change of its texts sees the others. Returns whether it
   * settled now.
   */
  private boolean settle(Act act, Language language, PreparationStore.Kept kept) {
    if (kept.settledAt() != null) {
      return false;
    }
    var preparation = preparation(act, language);
    if (!preparation.settled()) {
      return false;
    }
    var prepared = preparation.ready() > 0;
    if (!preparations.settle(act.id(), language, prepared)) {
      return false;
    }
    LOG.info(
        "Prepared {} in {}: {} of {} versions built, {} failed, in {} s",
        act.id(),
        language.code(),
        preparation.ready(),
        preparation.size(),
        preparation.failed(),
        Duration.between(kept.started(), clock.instant()).toSeconds());
    events.publishEvent(
        prepared
            ? new PreparationCompleted(
                act.id(), language, preparation.ready(), preparation.failed())
            : new PreparationFailed(act.id(), language));
    return true;
  }

  @ApplicationModuleListener
  void on(ExpressionStarted started) {
    var act = acts.findByVersion(started.celex());
    if (act.isPresent()
        && expressions.start(started.celex(), started.language(), started.attempt())) {
      LOG.debug(
          "{} in {} is being built, attempt {}",
          started.celex(),
          started.language().code(),
          started.attempt());
      changed(
          act.get(),
          started.celex(),
          started.language(),
          Step.Kind.STARTED,
          started.attempt(),
          null);
    }
  }

  @ApplicationModuleListener
  void on(ExpressionDeferred deferred) {
    var act = acts.findByVersion(deferred.celex());
    if (act.isPresent()
        && expressions.defer(deferred.celex(), deferred.language(), deferred.attempt())) {
      LOG.debug(
          "{} in {} is queued again after attempt {}",
          deferred.celex(),
          deferred.language().code(),
          deferred.attempt());
      changed(
          act.get(),
          deferred.celex(),
          deferred.language(),
          Step.Kind.DEFERRED,
          deferred.attempt(),
          null);
    }
  }

  @ApplicationModuleListener
  void on(ExpressionFailed failed) {
    var act = acts.findByVersion(failed.celex());
    if (act.isPresent()
        && expressions.fail(failed.celex(), failed.language(), failed.failure(), failed.trace())) {
      LOG.debug(
          "{} in {} couldn't be built: {}",
          failed.celex(),
          failed.language().code(),
          failed.failure());
      changed(
          act.get(), failed.celex(), failed.language(), Step.Kind.FAILED, null, failed.failure());
    }
  }

  @ApplicationModuleListener
  void on(ExpressionBuilt built) {
    var act = acts.findByVersion(built.celex());
    if (act.isEmpty()) {
      // A version that the catalogue does not have. It is not one of its texts.
      return;
    }
    if (expressions.keep(built.celex(), built.language(), built.root(), built.converter())) {
      LOG.info("{} in {} is now {}", built.celex(), built.language().code(), built.root());
      changed(act.get(), built.celex(), built.language(), Step.Kind.BUILT, null, null);
    }
    var timeline = act.get().timeline().in(built.language());
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

  /**
   * Records that a text of the act changed. The change is a step of its preparation in the
   * language, and it may settle the preparation. The preparation is locked first, so the reference
   * of the step to it waits on no other change.
   */
  private void changed(
      Act act,
      String celex,
      Language language,
      Step.Kind kind,
      @Nullable Integer attempt,
      @Nullable Failure failure) {
    var kept = preparations.lock(act.id(), language);
    preparations.step(act.id(), language, celex, kind, attempt, failure);
    kept.ifPresent(each -> settle(act, language, each));
    events.publishEvent(new PreparationChanged(act.id(), language));
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
