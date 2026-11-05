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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * An act's preparation in a language, pushed to the pages waiting on it (Design: URLs and htmx,
 * Preparing): /eli/reg_impl/2015/1998/eng/prepare/events, server-sent events. Each message is how
 * it stands, the parts of the page that change (its timeline, its progress, its steps), each
 * swapped in by its id: first as it stands on connecting (or reconnecting), then again whenever it
 * changes (PreparationChanged, broadcast from every instance once committed); once every version is
 * settled, the event done, and the stream ends. A prepared act's page listens too while versions
 * that appeared since are prepared (ArrivingView), for done alone. A stream also ends after a
 * while, and the page connects again.
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

  /**
   * The stream of the act's preparation in the language: for a prepared act's page waiting on the
   * versions that appeared since ({@code arriving}), done alone.
   */
  @GetMapping(
      path = "/eli/{type}/{year}/{number}/{language}/prepare/events",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter events(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code,
      @RequestParam(defaultValue = "false") boolean arriving) {
    var language = Requests.language(code);
    var act = acts.find(new Eli(type, year, number)).orElseThrow(Requests::notFound);
    if (act.timeline().in(language).isEmpty()) {
      throw Requests.notFound();
    }
    var stream = new Stream(new SseEmitter(LASTS.toMillis()), act.id(), language, arriving);
    stream.emitter().onCompletion(() -> streams.remove(stream));
    stream.emitter().onTimeout(() -> streams.remove(stream));
    stream.emitter().onError(e -> streams.remove(stream));
    streams.add(stream);
    send(stream);
    return stream.emitter();
  }

  /** An act's preparation changed: its streams in the language, sent how it stands now. */
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
   * How {@code stream}'s act stands, or done if it is settled. The act read anew: it may have
   * versions it didn't have when the stream began.
   */
  private void send(Stream stream) {
    var act = acts.find(stream.act()).orElseThrow();
    var preparation = texts.preparation(act, stream.language());
    try {
      if (preparation.settled()) {
        stream.emitter().send(SseEmitter.event().name("done").data(""));
        stream.emitter().complete();
        streams.remove(stream);
      } else if (!stream.arriving()) {
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
      // the page went: its stream is done with
      LOG.debug("A preparation's stream closed: {}", e.toString());
      streams.remove(stream);
    }
  }

  /** A page's stream. */
  private record Stream(
      SseEmitter emitter, ActIdentifier act, Language language, boolean arriving) {}
}
