// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.broadcast.Broadcast;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.PreparationChanged;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import gg.jte.generated.precompiled.Templates;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes the preparation of an act in a language to the pages that wait for it, as server-sent
 * events. For example, {@code /eli/reg_impl/2015/1998/eng/prepare/events}.
 *
 * <p>Each message has the current state of the preparation. It contains the parts of the page that
 * change: the timeline, the progress and the steps. The page swaps in each part by its id.
 *
 * <ul>
 *   <li>The stream sends a message when the page connects or connects again.
 *   <li>It sends a message again each time the preparation changes. Each instance gets a {@link
 *       PreparationChanged} broadcast after the change is committed.
 *   <li>When every version is settled, the stream sends the event {@code done} and ends.
 * </ul>
 *
 * <p>A stream also ends after a while, and then the page connects again.
 */
@Controller
class PreparationController {
  private static final Logger LOG = LoggerFactory.getLogger(PreparationController.class);

  /** How long a stream lasts before the page is asked to connect again. */
  private static final Duration LASTS = Duration.ofMinutes(10);

  private final Set<Stream> streams = new CopyOnWriteArraySet<>();
  private final Acts acts;
  private final Texts texts;
  private final Templates templates;
  private final Clock clock;

  PreparationController(Acts acts, Texts texts, Templates templates, Clock clock) {
    this.acts = acts;
    this.texts = texts;
    this.templates = templates;
    this.clock = clock;
  }

  /** Returns the stream of the preparation of the act in the language. */
  @GetMapping(
      path = "/eli/{type}/{year}/{number}/{language}/prepare/events",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter events(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code) {
    var language = Requests.language(code);
    var act = acts.find(new Eli(type, year, number)).orElseThrow(Requests::notFound);
    if (act.timeline().in(language).isEmpty()) {
      throw Requests.notFound();
    }
    var stream = new Stream(new SseEmitter(LASTS.toMillis()), act.id(), language);
    stream.emitter().onCompletion(() -> streams.remove(stream));
    stream.emitter().onTimeout(() -> streams.remove(stream));
    stream.emitter().onError(e -> streams.remove(stream));
    streams.add(stream);
    send(stream);
    return stream.emitter();
  }

  /**
   * Sends the current state of a preparation that changed. It sends to each stream of that act in
   * that language.
   */
  @EventListener
  void on(Broadcast<PreparationChanged> broadcast) {
    var changed = broadcast.event();
    for (var stream : streams) {
      if (stream.language() == changed.language() && stream.act().equals(changed.act())) {
        send(stream);
      }
    }
  }

  /**
   * Sends the current state of the act of {@code stream}, or {@code done} if the act is settled. It
   * reads the act again, because the act may have versions that it did not have when the stream
   * began.
   */
  private void send(Stream stream) {
    var act = acts.find(stream.act()).orElseThrow();
    var preparation = texts.preparation(act, stream.language());
    try {
      if (preparation.settled()) {
        stream.emitter().send(SseEmitter.event().name("done").data(""));
        stream.emitter().complete();
        streams.remove(stream);
      } else {
        stream
            .emitter()
            .send(
                SseEmitter.event()
                    .data(
                        templates
                            .cataloguePreparingStream(
                                TimelineView.preparing(act, preparation),
                                PreparingView.Progress.of(preparation, clock.instant()),
                                PreparingView.steps(preparation))
                            .render()));
      }
    } catch (IOException | IllegalStateException e) {
      // The page closed, so its stream is no longer necessary.
      LOG.debug("A preparation's stream closed: {}", e.toString());
      streams.remove(stream);
    }
  }

  /** A page's stream. */
  private record Stream(SseEmitter emitter, ActIdentifier act, Language language) {}
}
