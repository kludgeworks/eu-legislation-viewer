// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.broadcast.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.Externalized;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.modulith.events.support.EventExternalizationTransport;

class StreamExternalizerTest {
  private static final EventExternalizationConfiguration CONFIGURATION =
      EventExternalizationConfiguration.defaults(StreamExternalizerTest.class.getPackageName())
          .build();

  @Test
  void externalizesSupportedEventToItsTargetStream() {
    var delivered = new ArrayList<Sent>();
    EventExternalizationTransport transport =
        (payload, target) -> {
          delivered.add(new Sent(payload, target));
          return CompletableFuture.completedFuture("ok");
        };
    var externalizer = new BroadcastConfiguration.StreamExternalizer(CONFIGURATION, transport);
    var event = new Broadcasted("doc-1");

    assertThat(externalizer.supports(event)).isTrue();
    var result = externalizer.externalize(event);

    assertThat(result).isCompleted();
    assertThat(result.join()).isEqualTo("ok");
    assertThat(delivered)
        .containsExactly(new Sent(event, RoutingTarget.forTarget("broadcast-stream").withoutKey()));
  }

  @Test
  void rejectsEventsWithoutExternalizationTarget() {
    var externalizer =
        new BroadcastConfiguration.StreamExternalizer(
            CONFIGURATION, (payload, target) -> CompletableFuture.completedFuture(null));

    assertThat(externalizer.supports(new Internal("doc-1"))).isFalse();
  }

  @Test
  void turnsSynchronousTransportFailureIntoAFailedFuture() {
    var failure = new IllegalStateException("valkey unavailable");
    EventExternalizationTransport transport =
        (payload, target) -> {
          throw failure;
        };
    var externalizer = new BroadcastConfiguration.StreamExternalizer(CONFIGURATION, transport);

    var result = externalizer.externalize(new Broadcasted("doc-1"));

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).isInstanceOf(CompletionException.class).hasCause(failure);
  }

  @Externalized("broadcast-stream")
  private record Broadcasted(String id) {}

  private record Internal(String id) {}

  private record Sent(Object payload, RoutingTarget target) {}
}
