// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Lookups;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ActLookupView;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import gg.jte.generated.precompiled.Templates;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes the end of the lookup of an act to the page that waits for it, as server-sent events. For
 * example, {@code /eli/reg/2005/396/lookup/events?page=/eli/reg/2005/396/eng}.
 *
 * <ul>
 *   <li>If the act is found, the stream sends the event {@code done}, and the page asks for its
 *       address again.
 *   <li>If no act has the ELI, or the Publications Office did not answer, the stream sends the
 *       fragment of the page that says so.
 * </ul>
 *
 * <p>The stream joins the lookup that runs, or starts one. It ends when the lookup ends, or after a
 * while, and then the page connects again.
 */
/*
 * A handler takes the act of its address as an Eli, which ActAddressResolver gives. The check
 * knows only the arguments that Spring gives.
 */
@SuppressWarnings("RequestMappingAnnotation")
@Controller
class ActLookupController {
  private static final Logger LOG = LoggerFactory.getLogger(ActLookupController.class);

  /** How long a stream lasts before the page is asked to connect again. */
  private static final Duration LASTS = Duration.ofMinutes(2);

  private final Lookups lookups;
  private final Templates templates;

  ActLookupController(Lookups lookups, Templates templates) {
    this.lookups = lookups;
    this.templates = templates;
  }

  /**
   * Returns the stream of the lookup of the act. {@code page} is the address of the page that
   * waits, an address of the act. Returns 404 for another address.
   */
  @GetMapping(
      path = "/eli/{type}/{year}/{number}/lookup/events",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter events(Eli eli, @Valid @ModelAttribute LookupRequest asked) {
    var page = asked.page();
    if (!page.startsWith("/eli/" + eli.path() + "/")) {
      throw Requests.notFound();
    }
    var lookup = ActLookupView.of(eli, page);
    var emitter = new SseEmitter(LASTS.toMillis());
    lookups
        .lookUp(eli)
        .whenComplete((act, failure) -> {
          try {
            if (failure == null && act.isPresent()) {
              emitter.send(SseEmitter.event().name("done").data(""));
            } else {
              emitter.send(
                  SseEmitter.event()
                      .data(templates.catalogueActlookupEnded(lookup, failure == null).render()));
            }
            emitter.complete();
          } catch (IOException | IllegalStateException e) {
            // The page went away, so its stream is no longer necessary.
            LOG.debug("A lookup's stream closed: {}", e.toString());
          }
        });
    return emitter;
  }

  /**
   * What the address of the stream of a lookup asks for, beside the act.
   *
   * @param page the address of the page that waits
   */
  record LookupRequest(@NotNull String page) {}
}
