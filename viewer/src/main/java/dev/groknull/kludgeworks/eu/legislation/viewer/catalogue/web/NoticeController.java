// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.broadcast.Broadcast;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.VersionAdded;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.NoticeView;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Requests;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextReplaced;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import gg.jte.generated.precompiled.Templates;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Pushes notices to the open pages of an act, as server-sent events, when the act changes while a
 * reader reads it. For example, {@code /eli/reg_impl/2015/1998/eng/notices}. The page shows the
 * notice. It does not change by itself.
 *
 * <ul>
 *   <li>"A newer version exists": the catalogue added a version of the act in the page's language
 *       ({@link VersionAdded}).
 *   <li>"This text was republished by the Publications Office on …": the content module replaced
 *       the text of a version of the act in the page's language ({@link TextReplaced}).
 * </ul>
 *
 * <p>Each instance gets both events as a {@link Broadcast}, after they are committed, and sends to
 * the streams that it holds. A stream ends after a while, and then the page connects again. A
 * notice sent while a page was not connected is not sent again.
 */
@Controller
class NoticeController {
  private static final Logger LOG = LoggerFactory.getLogger(NoticeController.class);

  /** How long a stream lasts before the page is asked to connect again. */
  private static final Duration LASTS = Duration.ofMinutes(30);

  /** Where the Publications Office is, for the day it published. */
  private static final ZoneId LUXEMBOURG = ZoneId.of("Europe/Luxembourg");

  private final Set<Stream> streams = new CopyOnWriteArraySet<>();
  private final Acts acts;
  private final Templates templates;

  NoticeController(Acts acts, Templates templates) {
    this.acts = acts;
    this.templates = templates;
  }

  /** Returns the stream of notices of the act in the language. */
  @GetMapping(
      path = "/eli/{type}/{year}/{number}/{language}/notices",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter events(
      @PathVariable String type,
      @PathVariable int year,
      @PathVariable String number,
      @PathVariable("language") String code) {
    var language = Requests.language(code);
    var act = acts.catalogued(new Eli(type, year, number)).orElseThrow(Requests::notFound);
    var stream = new Stream(new SseEmitter(LASTS.toMillis()), act.id(), language);
    stream.emitter().onCompletion(() -> streams.remove(stream));
    stream.emitter().onTimeout(() -> streams.remove(stream));
    stream.emitter().onError(e -> streams.remove(stream));
    streams.add(stream);
    return stream.emitter();
  }

  /** Tells the pages of the act in a language of the new version that a newer version exists. */
  @EventListener
  void onVersion(Broadcast<VersionAdded> broadcast) {
    var added = broadcast.event();
    for (var stream : streams) {
      if (stream.act().equals(added.act()) && added.languages().contains(stream.language())) {
        send(stream, NoticeView.newerVersion(added.date()));
      }
    }
  }

  /**
   * Tells the pages of the act in the language of a replaced text that it was replaced, with the
   * day the Publications Office last published its Formex, as the catalogue has it.
   */
  @EventListener
  void onText(Broadcast<TextReplaced> broadcast) {
    var replaced = broadcast.event();
    for (var stream : streams) {
      if (stream.language() != replaced.language()) {
        continue;
      }
      var act = acts.catalogued(stream.act());
      var version =
          act.stream()
              .flatMap(each -> each.timeline().versions().stream())
              .filter(each -> each.celex().equals(replaced.celex()))
              .findFirst();
      if (version.isEmpty()) {
        continue;
      }
      var formex = version.map(Version::formex).map(each -> each.get(replaced.language()));
      send(
          stream,
          NoticeView.replaced(
              formex.map(each -> LocalDate.ofInstant(each.modified(), LUXEMBOURG)).orElse(null)));
    }
  }

  private void send(Stream stream, NoticeView notice) {
    try {
      stream.emitter().send(SseEmitter.event().data(templates.catalogueNotice(notice).render()));
      LOG.debug("Told a page of {} in {}: {}", stream.act(), stream.language(), notice.notice());
    } catch (IOException | IllegalStateException e) {
      // The page closed, so its stream is no longer necessary.
      LOG.debug("A notices stream closed: {}", e.toString());
      streams.remove(stream);
    }
  }

  /** A page's stream. */
  private record Stream(SseEmitter emitter, ActIdentifier act, Language language) {}
}
