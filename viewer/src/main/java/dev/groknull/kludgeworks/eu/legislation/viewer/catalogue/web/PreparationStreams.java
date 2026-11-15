// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.PreparationChanged;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The open streams of the preparations of acts, as server-sent events. A page that waits for a text
 * has one ({@link PreparationController}), and so has a client of the API ({@link
 * ApiPreparationController}). Each writes its events in its own way, its {@link Sender}.
 *
 * <ul>
 *   <li>A stream sends the preparation when it opens.
 *   <li>It sends it again each time the preparation changes ({@link #changed}).
 *   <li>It ends when the preparation is settled, or after a while, and then the client connects
 *       again.
 * </ul>
 */
final class PreparationStreams {
  private static final Logger LOG = LoggerFactory.getLogger(PreparationStreams.class);

  /** How long a stream lasts before the client is asked to connect again. */
  private static final Duration LASTS = Duration.ofMinutes(10);

  private final Set<Stream> streams = new CopyOnWriteArraySet<>();
  private final Sender sender;

  PreparationStreams(Sender sender) {
    this.sender = sender;
  }

  /** Opens a stream of the preparation of the act in the language, and sends its state. */
  SseEmitter open(ActIdentifier act, Language language) {
    var stream = new Stream(new SseEmitter(LASTS.toMillis()), act, language);
    stream.emitter().onCompletion(() -> streams.remove(stream));
    stream.emitter().onTimeout(() -> streams.remove(stream));
    stream.emitter().onError(e -> streams.remove(stream));
    streams.add(stream);
    send(stream);
    return stream.emitter();
  }

  /** Sends the preparation that changed to each stream of it. */
  void changed(PreparationChanged changed) {
    for (var stream : streams) {
      if (stream.language() == changed.language() && stream.act().equals(changed.act())) {
        send(stream);
      }
    }
  }

  private void send(Stream stream) {
    try {
      if (sender.send(stream.emitter(), stream.act(), stream.language())) {
        stream.emitter().complete();
        streams.remove(stream);
      }
    } catch (IOException | IllegalStateException e) {
      // The client went away, so its stream is no longer necessary.
      LOG.debug("A preparation's stream closed: {}", e.toString());
      streams.remove(stream);
    }
  }

  /** Writes the current state of a preparation to a stream. */
  @FunctionalInterface
  interface Sender {
    /**
     * Sends the current state of the preparation of the act in the language. It reads the act
     * again, because the act may have versions that it did not have when the stream began.
     *
     * @return whether the preparation is settled, so the stream ends
     */
    boolean send(SseEmitter emitter, ActIdentifier act, Language language) throws IOException;
  }

  private record Stream(SseEmitter emitter, ActIdentifier act, Language language) {}
}
