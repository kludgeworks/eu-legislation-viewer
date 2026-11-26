// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.broadcast.Broadcast;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActAddress;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.PreparationChanged;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.generated.precompiled.Templates;
import java.io.IOException;
import java.time.Clock;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes the preparation of an act in a language to the pages that wait for it, as server-sent
 * events. For example, {@code /eli/reg_impl/2015/1998/eng/prepare/events}.
 *
 * <p>Each message has the current state of the preparation. It contains the fragments of the page
 * that change: the progress and the count that a screen reader hears. The page swaps in each
 * fragment by its id.
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
/*
 * A handler takes the act and the language of its address as an ActAddress, which
 * ActAddressResolver gives. The check knows only the arguments that Spring gives.
 */
@SuppressWarnings("RequestMappingAnnotation")
@Controller
class PreparationController {
  private final PreparationStreams streams = new PreparationStreams(this::send);
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
  SseEmitter events(ActAddress address) {
    var language = address.language();
    var act = acts.find(address.eli()).orElseThrow(Requests::notFound);
    if (act.timeline().in(language).isEmpty()) {
      throw Requests.notFound();
    }
    return streams.open(act.id(), language);
  }

  /** Sends the preparation that changed to each page that waits for it. */
  @EventListener
  void on(Broadcast<PreparationChanged> broadcast) {
    streams.changed(broadcast.event());
  }

  /**
   * Sends the fragments of the page that change, or {@code done} if the act is settled.
   *
   * @return whether the act is settled
   */
  private boolean send(SseEmitter emitter, ActIdentifier id, Language language) throws IOException {
    var act = acts.find(id).orElseThrow();
    var preparation = texts.preparation(act, language);
    if (preparation.settled()) {
      emitter.send(SseEmitter.event().name("done").data(""));
      return true;
    }
    emitter.send(
        SseEmitter.event()
            .data(
                templates
                    .cataloguePreparingStream(
                        PreparingView.Progress.of(act, preparation, clock.instant()))
                    .render()));
    return false;
  }
}
