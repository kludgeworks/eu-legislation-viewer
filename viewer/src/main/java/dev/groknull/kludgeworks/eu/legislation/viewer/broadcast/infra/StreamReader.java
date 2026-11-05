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
 * The entries of the streams StreamTransport sends to, published on this instance as Broadcast of
 * the event each holds. Only the app's externalized types are read back (types): an entry of any
 * other type is skipped, as is one that isn't an event of its type.
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
