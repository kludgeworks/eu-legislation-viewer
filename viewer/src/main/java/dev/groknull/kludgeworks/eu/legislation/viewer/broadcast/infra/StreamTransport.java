// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.broadcast.infra;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.modulith.events.support.BrokerRouting;
import org.springframework.modulith.events.support.EventExternalizationTransport;
import tools.jackson.databind.json.JsonMapper;

/**
 * An externalized event sent to Valkey: added to the stream its @Externalized names (XADD), its
 * type and its JSON, for every instance's StreamReader. A stream keeps only its latest entries
 * (about KEPT, trimmed as Valkey finds cheapest): they are read as they come, never replayed.
 */
class StreamTransport implements EventExternalizationTransport {
  private static final Logger LOG = LoggerFactory.getLogger(StreamTransport.class);

  /** How many entries a stream keeps, about. */
  static final long KEPT = 1000;

  static final String TYPE = "type";
  static final String PAYLOAD = "payload";

  private final StringRedisTemplate redis;
  private final JsonMapper json;

  StreamTransport(StringRedisTemplate redis, JsonMapper json) {
    this.redis = redis;
    this.json = json;
  }

  @Override
  public CompletableFuture<?> externalize(Object payload, RoutingTarget target) {
    var stream = BrokerRouting.of(target, new StandardEvaluationContext()).getTarget(payload);
    var id =
        redis
            .opsForStream()
            .add(
                StreamRecords.string(
                        Map.of(
                            TYPE, payload.getClass().getName(),
                            PAYLOAD, json.writeValueAsString(payload)))
                    .withStreamKey(stream),
                XAddOptions.maxlen(KEPT).approximateTrimming(true));
    LOG.debug("Sent {} to stream {} as {}", payload, stream, id);
    return CompletableFuture.completedFuture(id);
  }
}
