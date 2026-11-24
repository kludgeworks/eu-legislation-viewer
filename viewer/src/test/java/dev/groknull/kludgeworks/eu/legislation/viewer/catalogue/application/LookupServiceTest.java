// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import java.time.Duration;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the lookups of acts in the background: they join, they keep the answer that no act has an
 * identifier, and they forget a failure. The acts are a fake catalogue that counts how often it
 * asks the Publications Office.
 */
class LookupServiceTest {
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final Catalogue catalogue = new Catalogue();
  private final LookupService lookups = new LookupService(catalogue, executor);

  @AfterEach
  void stop() {
    executor.shutdownNow();
  }

  @Test
  void answersAtOnceWithAnActThatTheCatalogueHas() {
    catalogue.held = OneActCellar.ACT;

    var lookup = lookups.lookUp(OneActCellar.ELI);

    assertThat(lookup).isCompletedWithValue(Optional.of(OneActCellar.ACT));
    assertThat(catalogue.asked).hasValue(0);
  }

  @Test
  void joinsTwoLookupsOfOneAct() throws Exception {
    var release = new CountDownLatch(1);
    catalogue.answers.add(() -> {
      waitFor(release);
      return Optional.of(OneActCellar.ACT);
    });

    var first = lookups.lookUp(OneActCellar.ELI);
    var second = lookups.lookUp(OneActCellar.ELI);
    release.countDown();

    assertThat(second).isSameAs(first);
    assertThat(first.get(10, TimeUnit.SECONDS)).contains(OneActCellar.ACT);
    assertThat(catalogue.asked).hasValue(1);
  }

  @Test
  void keepsTheAnswerThatNoActHasTheIdentifier() throws Exception {
    catalogue.answers.add(Optional::empty);

    assertThat(lookups.lookUp(OneActCellar.ELI).get(10, TimeUnit.SECONDS)).isEmpty();
    var again = lookups.lookUp(OneActCellar.ELI);

    assertThat(again).isCompletedWithValue(Optional.empty());
    assertThat(catalogue.asked).hasValue(1);
  }

  @Test
  void asksAgainAfterThePublicationsOfficeDidNotAnswer() {
    catalogue.answers.add(() -> {
      throw new IllegalStateException("Read timed out");
    });
    catalogue.answers.add(() -> Optional.of(OneActCellar.ACT));

    var failed = lookups.lookUp(OneActCellar.CELEX);
    // Until the failure is forgotten, the failed lookup answers. Then a new lookup starts.
    var again =
        await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> lookups.lookUp(OneActCellar.CELEX), each -> each != failed);

    assertThat(failed).isCompletedExceptionally();
    assertThat(again)
        .succeedsWithin(Duration.ofSeconds(10))
        .isEqualTo(Optional.of(OneActCellar.ACT));
    assertThat(catalogue.asked).hasValue(2);
  }

  private static void waitFor(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** A catalogue that has {@link #held}, and else answers in turn from {@link #answers}. */
  private static final class Catalogue implements Acts {
    final Deque<Supplier<Optional<Act>>> answers = new ConcurrentLinkedDeque<>();
    final AtomicInteger asked = new AtomicInteger();
    volatile @Nullable Act held;

    @Override
    public Optional<Act> find(ActIdentifier id) {
      return ask();
    }

    @Override
    public Optional<Act> find(Eli eli) {
      return ask();
    }

    @Override
    public Optional<Act> findByVersion(String celex) {
      return ask();
    }

    @Override
    public Optional<Act> catalogued(ActIdentifier id) {
      return Optional.ofNullable(held);
    }

    @Override
    public Optional<Act> catalogued(Eli eli) {
      return Optional.ofNullable(held);
    }

    private Optional<Act> ask() {
      asked.incrementAndGet();
      var answer = answers.poll();
      return answer == null ? Optional.empty() : answer.get();
    }
  }
}
