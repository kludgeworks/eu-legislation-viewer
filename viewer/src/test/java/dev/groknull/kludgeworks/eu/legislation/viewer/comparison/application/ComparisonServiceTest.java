// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.stream.XMLStreamWriter;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.Test;

class ComparisonServiceTest {
  private static final ContentHash FROM = new ContentHash("a".repeat(64));
  private static final ContentHash TO = new ContentHash("b".repeat(64));

  @Test
  void worksOutAComparisonOnceWhenItsAskedForAgainMeanwhile() {
    var texts = new HeldTexts();
    var service =
        new ComparisonService(
            texts, new KeptComparisons(), ObservationRegistry.NOOP, Thread.ofVirtual()::start);

    // The texts are held until both comparisons are asked for. A second working out would read
    // the texts again.
    var first = service.comparing(FROM, TO);
    var second = service.comparing(FROM, TO);
    texts.held.countDown();

    assertThat(second).succeedsWithin(Duration.ofSeconds(5)).isEqualTo(first.join());
    assertThat(texts.reads).hasValue(2);
  }

  /**
   * Gives the same empty {@code legalText} for each text. A read returns when {@link #held} is let
   * go.
   */
  private static final class HeldTexts implements Content {
    final CountDownLatch held = new CountDownLatch(1);
    final AtomicInteger reads = new AtomicInteger();

    @Override
    public Optional<Element> read(ContentHash root) {
      reads.incrementAndGet();
      try {
        held.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return Optional.of(new Element("legalText", Map.of(), List.of(), ""));
    }

    @Override
    public ContentHash store(XdmNode legalText) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Outline> outline(ContentHash root) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean rebuild(ContentHash root, XMLStreamWriter out) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class KeptComparisons implements ComparisonStore {
    private final Map<List<ContentHash>, Comparison> kept = new ConcurrentHashMap<>();

    @Override
    public Optional<Comparison> find(ContentHash from, ContentHash to) {
      return Optional.ofNullable(kept.get(List.of(from, to)));
    }

    @Override
    public Optional<Element> whole(ContentHash from, ContentHash to, String eId) {
      return Optional.empty();
    }

    @Override
    public Comparison add(Comparison comparison) {
      kept.putIfAbsent(List.of(comparison.from(), comparison.to()), comparison);
      return comparison;
    }
  }
}
