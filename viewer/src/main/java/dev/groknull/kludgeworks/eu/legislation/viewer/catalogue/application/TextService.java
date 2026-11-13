// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.LanguageAdded;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NeighboursBuilt;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation.Step;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.PreparationChanged;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.PreparationCompleted;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.PreparationFailed;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.VersionAdded;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.VersionRepublished;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.VersionWithdrawn;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionBuilt;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionDeferred;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionFailed;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionStarted;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Ingest;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *   <li>When a text is built, and the text of the version before or after it in the language is
 *       built too, it publishes a {@link NeighboursBuilt} event for each such pair. A reader will
 *       probably compare them, so another module can get ready before the reader asks.
 * </ul>
 *
 * <p>The texts themselves are in the content module. The service asks it for the texts of the
 * versions whenever it gives the state of a text, so that a text that is ready names the text that
 * the content module has now.
 */
@Service
class TextService implements Texts {
  private static final Logger LOG = LoggerFactory.getLogger(TextService.class);

  private final ExpressionStore expressions;
  private final Content content;
  private final PreparationStore preparations;
  private final ActStore acts;
  private final Ingest ingest;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  TextService(
      ExpressionStore expressions,
      Content content,
      PreparationStore preparations,
      ActStore acts,
      Ingest ingest,
      ApplicationEventPublisher events,
      Clock clock) {
    this.expressions = expressions;
    this.content = content;
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
    for (var text : texts(celexes, language).entrySet()) {
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
    for (var text : texts(celexes(act, language), language).entrySet()) {
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
    var kept = texts(celexes, language);
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
        yield expressions.keep(celex, language);
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
  void on(VersionAdded added) {
    for (var language : added.languages()) {
      prepareText(added.act(), added.celex(), language, false);
    }
  }

  @ApplicationModuleListener
  void on(LanguageAdded added) {
    prepareText(added.act(), added.celex(), added.language(), false);
  }

  @ApplicationModuleListener
  void on(VersionRepublished republished) {
    prepareText(republished.act(), republished.celex(), republished.language(), true);
  }

  /**
   * Prepares the text of a version in a language, which Cellar added or, {@code again},
   * republished. It is queued and scheduled, whether or not the act was ever prepared in the
   * language. A text built already stays as it is, and is read, until the new text replaces it in
   * the content module. A text that failed is queued again. Each step is idempotent: an event
   * handed out twice schedules the text once, or builds it again to the same text.
   */
  private void prepareText(ActIdentifier act, String celex, Language language, boolean again) {
    expressions.queue(List.of(celex), language);
    var changed = again && expressions.requeue(celex, language);
    if (again) {
      ingest.rebuild(celex, language);
    }
    changed |= schedule(celex, language);
    LOG.info(
        "Preparing {} of {} in {}{}",
        celex,
        act,
        language.code(),
        again ? " again: Cellar republished it" : "");
    if (changed) {
      events.publishEvent(new PreparationChanged(act, language));
    }
  }

  @ApplicationModuleListener
  void on(ExpressionBuilt built) {
    var act = acts.findByVersion(built.celex());
    if (act.isEmpty()) {
      // A version that the catalogue does not have, or no longer has: it was withdrawn while its
      // text was built. The content module deletes the text again.
      LOG.info("{} in {} is built, but the version was withdrawn", built.celex(), built.language());
      events.publishEvent(new VersionWithdrawn(built.celex()));
      return;
    }
    if (expressions.keep(built.celex(), built.language())) {
      LOG.info("{} in {} is built", built.celex(), built.language().code());
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
    var before = timeline.get().predecessor(version);
    var after = timeline.get().successor(version);
    var celexes = new ArrayList<String>(List.of(built.celex()));
    before.ifPresent(each -> celexes.add(each.celex()));
    after.ifPresent(each -> celexes.add(each.celex()));
    var stored = content.texts(celexes, built.language());
    var text = stored.get(built.celex());
    if (text == null) {
      // The content module no longer has it: nothing to compare.
      return;
    }
    before.map(each -> stored.get(each.celex())).ifPresent(earlier -> neighbours(earlier, text));
    after.map(each -> stored.get(each.celex())).ifPresent(later -> neighbours(text, later));
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

  /**
   * Returns the texts of these versions in the language as they stand, by CELEX, with the texts
   * that the content module has for them.
   */
  private Map<String, Text> texts(Collection<String> celexes, Language language) {
    return expressions.texts(celexes, language, content.texts(celexes, language));
  }

  /** Tells that {@code earlier} and {@code later}, texts of neighbouring versions, are built. */
  private void neighbours(TextId earlier, TextId later) {
    LOG.debug("Neighbouring texts {} and {} are built", earlier, later);
    events.publishEvent(new NeighboursBuilt(earlier, later));
  }

  private static List<Version> versions(Act act, Language language) {
    return act.timeline().in(language).map(Timeline::versions).orElseGet(List::of);
  }

  private static List<String> celexes(Act act, Language language) {
    return versions(act, language).stream().map(Version::celex).toList();
  }
}
