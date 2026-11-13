// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.Test;

class ComparisonServiceTest {
  private static final TextId FROM = new TextId(1);
  private static final TextId TO = new TextId(2);

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
    public Optional<Element> read(TextId text) {
      reads.incrementAndGet();
      try {
        held.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return Optional.of(new Element("legalText", Map.of(), List.of(), ""));
    }

    @Override
    public TextId store(String celex, Language language, String converter, XdmNode legalText) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Outline> outline(TextId text) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(TextId text) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, TextId> texts(Collection<String> celexes, Language language) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class KeptComparisons implements ComparisonStore {
    private final Map<List<TextId>, Comparison> kept = new ConcurrentHashMap<>();

    @Override
    public Optional<Comparison> find(TextId from, TextId to, String version) {
      return Optional.ofNullable(kept.get(List.of(from, to)));
    }

    @Override
    public Optional<Element> whole(TextId from, TextId to, String eId) {
      return Optional.empty();
    }

    @Override
    public Comparison add(Comparison comparison, String version) {
      kept.putIfAbsent(List.of(comparison.from(), comparison.to()), comparison);
      return comparison;
    }

    @Override
    public int remove(TextId text) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(TextId from, TextId to) {
      throw new UnsupportedOperationException();
    }
  }
}
