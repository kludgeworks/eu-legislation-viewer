// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.broadcast.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.broadcast.Broadcast;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the entries of the streams that {@link StreamTransport} sends to. It publishes each entry
 * on this instance as a {@link Broadcast} of the event that the entry holds. Only the externalized
 * types of the app, in {@code types}, are read back. An entry of any other type is skipped. An
 * entry that is not an event of its type is skipped too.
 */
class StreamReader implements StreamListener<String, MapRecord<String, String, String>> {
  private static final Logger LOG = LoggerFactory.getLogger(StreamReader.class);

  private final Map<String, Class<?>> types;
  private final JsonMapper json;
  private final ApplicationEventPublisher events;

  /**
   * @param types the app's externalized types, by name
   */
  StreamReader(Map<String, Class<?>> types, JsonMapper json, ApplicationEventPublisher events) {
    this.types = Map.copyOf(types);
    this.json = json;
    this.events = events;
  }

  @Override
  public void onMessage(MapRecord<String, String, String> entry) {
    var name = entry.getValue().get(StreamTransport.TYPE);
    var type = name == null ? null : types.get(name);
    var payload = entry.getValue().get(StreamTransport.PAYLOAD);
    if (type == null || payload == null) {
      LOG.warn(
          "Skipped {} in stream {}: not an event of the app's", entry.getId(), entry.getStream());
      return;
    }
    Object event;
    try {
      event = json.readValue(payload, type);
    } catch (RuntimeException e) {
      LOG.warn("Skipped {} in stream {}: not a {}", entry.getId(), entry.getStream(), name, e);
      return;
    }
    LOG.debug("Read {} from stream {} as {}", event, entry.getStream(), entry.getId());
    events.publishEvent(new Broadcast<>(event));
  }
}
