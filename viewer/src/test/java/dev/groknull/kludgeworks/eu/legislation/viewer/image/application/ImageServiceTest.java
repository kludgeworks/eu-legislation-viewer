// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextRemoved;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextReplaced;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.ImageGoneException;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.ImageUnavailableException;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images.Rendition;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images.Source;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images.Variant;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.application.ImageProperties.Cache;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.application.ImageRenderer.Rendered;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.model.ImageSource;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.micrometer.observation.ObservationRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tests {@link ImageService} coalescing, digest checks, remembered permanent failures, and text
 * removal listeners.
 */
class ImageServiceTest {
  private static final TextId TEXT = new TextId(11);
  private static final String FILE = "fig_1.tif";
  private static final URI ITEM = URI.create("http://cellar/item/fig_1");

  @Test
  void coalescesConcurrentCacheMissesIntoOneFetch() throws Exception {
    var store = new InMemoryStore();
    var secondText = new TextId(12);
    store.replaceSources(
        TEXT,
        URI.create("http://cellar/manifest"),
        List.of(new Source(FILE, ITEM, "image/tiff", null)));
    store.replaceSources(
        secondText,
        URI.create("http://cellar/manifest-2"),
        List.of(new Source(FILE, ITEM, "image/tiff", null)));
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var fetches = new AtomicInteger();
    SourceFetcher fetcher =
        (uri, maxBytes) -> {
          fetches.incrementAndGet();
          entered.countDown();
          try {
            release.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }
          return "tiff-bytes".getBytes(StandardCharsets.UTF_8);
        };
    var service =
        service(store, fetcher, (bytes, variant) -> new Rendered(new byte[] {1, 2, 3}, 100, 50));

    var first =
        CompletableFuture.supplyAsync(
            () -> service.rendition(TEXT, FILE, Variant.PREVIEW).orElseThrow());
    entered.await();
    var second =
        CompletableFuture.supplyAsync(
            () -> service.rendition(secondText, FILE, Variant.PREVIEW).orElseThrow());
    await().atMost(Duration.ofSeconds(5)).until(() -> store.sourceLookups() == 2);
    await()
        .during(Duration.ofMillis(30))
        .atMost(Duration.ofSeconds(1))
        .until(() -> fetches.get() == 1);
    release.countDown();

    var a = first.get();
    var b = second.get();
    assertThat(store.sourceLookups()).isEqualTo(2);
    assertThat(fetches.get()).isEqualTo(1);
    assertThat(a.sourceSha256()).isEqualTo(b.sourceSha256());
    assertThat(a.webp()).isEqualTo(new byte[] {1, 2, 3});
    assertThat(store.findSourceDigest(secondText, FILE)).isPresent();
  }

  @Test
  void isolatesConcurrentMissesWhenPinnedDigestsDifferForSameItemUri() throws Exception {
    var store = new InMemoryStore();
    var otherText = new TextId(12);
    var payload = "valid-tiff".getBytes(StandardCharsets.UTF_8);
    var validSha1 = "a35a551de39fccbd49f5baf430afbe192e28a2c8";
    store.replaceSources(
        TEXT,
        URI.create("http://cellar/manifest"),
        List.of(new Source(FILE, ITEM, "image/tiff", "0000000000000000000000000000000000000000")));
    store.replaceSources(
        otherText,
        URI.create("http://cellar/manifest-2"),
        List.of(new Source(FILE, ITEM, "image/tiff", validSha1)));
    var firstEntered = new CountDownLatch(1);
    var secondEntered = new CountDownLatch(1);
    var releaseFirst = new CountDownLatch(1);
    var fetches = new AtomicInteger();
    SourceFetcher fetcher =
        (uri, maxBytes) -> {
          if (fetches.incrementAndGet() == 1) {
            firstEntered.countDown();
            try {
              releaseFirst.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(e);
            }
          } else {
            secondEntered.countDown();
          }
          return payload;
        };
    var service =
        service(store, fetcher, (bytes, variant) -> new Rendered(new byte[] {1, 2, 3}, 100, 50));

    var badFirst =
        CompletableFuture.supplyAsync(
            () -> service.rendition(TEXT, FILE, Variant.PREVIEW).orElseThrow());
    firstEntered.await();
    var goodSecond =
        CompletableFuture.supplyAsync(
            () -> service.rendition(otherText, FILE, Variant.PREVIEW).orElseThrow());
    assertThat(secondEntered.await(5, TimeUnit.SECONDS)).isTrue();
    releaseFirst.countDown();

    assertThatThrownBy(badFirst::get).hasCauseInstanceOf(ImageGoneException.class);
    assertThat(goodSecond.get().webp()).isEqualTo(new byte[] {1, 2, 3});
    assertThat(fetches.get()).isEqualTo(2);
  }

  @Test
  void throwsGoneWhenEvictedRenditionReturnsChangedBytesFromUpstream() {
    var store = new InMemoryStore();
    var originalBytes = "original-tiff".getBytes(StandardCharsets.UTF_8);
    store.replaceSources(
        TEXT,
        URI.create("http://cellar/manifest"),
        List.of(new Source(FILE, ITEM, "image/tiff", null)));
    var payload = new byte[][] {originalBytes};
    var service =
        service(
            store,
            (uri, maxBytes) -> payload[0],
            (bytes, variant) -> new Rendered(new byte[] {9}, 10, 10));

    assertThat(service.rendition(TEXT, FILE, Variant.PREVIEW)).isPresent();
    store.evictAllRenditions();
    payload[0] = "mutated-tiff".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> service.rendition(TEXT, FILE, Variant.PREVIEW))
        .isInstanceOf(ImageGoneException.class)
        .hasMessageContaining("changed");
  }

  @Test
  void throwsGoneOnRdfSha1MismatchAndDoesNotRetryCellar() {
    var store = new InMemoryStore();
    store.replaceSources(
        TEXT,
        URI.create("http://cellar/manifest"),
        List.of(new Source(FILE, ITEM, "image/tiff", "0000000000000000000000000000000000000000")));
    var fetches = new AtomicInteger();
    var service =
        service(
            store,
            (uri, maxBytes) -> {
              fetches.incrementAndGet();
              return "actual".getBytes(StandardCharsets.UTF_8);
            },
            (bytes, variant) -> new Rendered(new byte[] {9}, 10, 10));

    assertThatThrownBy(() -> service.rendition(TEXT, FILE, Variant.PREVIEW))
        .isInstanceOf(ImageGoneException.class)
        .hasMessageContaining("SHA-1");
    assertThatThrownBy(() -> service.rendition(TEXT, FILE, Variant.PREVIEW))
        .isInstanceOf(ImageGoneException.class);
    assertThat(fetches.get()).isEqualTo(1);
  }

  @Test
  void retriesAfterTransientUnavailableWithoutMarkingGone() {
    var store = new InMemoryStore();
    store.replaceSources(
        TEXT,
        URI.create("http://cellar/manifest"),
        List.of(new Source(FILE, ITEM, "image/tiff", null)));
    var calls = new AtomicInteger();
    var service =
        service(
            store,
            (uri, maxBytes) -> {
              if (calls.incrementAndGet() == 1) {
                throw new ImageUnavailableException("timeout");
              }
              return "ok-tiff".getBytes(StandardCharsets.UTF_8);
            },
            (bytes, variant) -> new Rendered(new byte[] {7}, 10, 10));

    assertThatThrownBy(() -> service.rendition(TEXT, FILE, Variant.PREVIEW))
        .isInstanceOf(ImageUnavailableException.class);
    assertThat(service.rendition(TEXT, FILE, Variant.PREVIEW)).isPresent();
    assertThat(calls.get()).isEqualTo(2);
  }

  @Test
  void limitsConcurrentCellarDownloads() throws Exception {
    var store = new InMemoryStore();
    var fileTwo = "fig_2.tif";
    store.replaceSources(
        TEXT,
        URI.create("http://cellar/manifest"),
        List.of(
            new Source(FILE, ITEM, "image/tiff", null),
            new Source(fileTwo, URI.create("http://cellar/item/fig_2"), "image/tiff", null)));
    var active = new AtomicInteger();
    var peak = new AtomicInteger();
    var firstEntered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    SourceFetcher fetcher =
        (uri, maxBytes) -> {
          int current = active.incrementAndGet();
          peak.accumulateAndGet(current, Math::max);
          firstEntered.countDown();
          try {
            release.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          } finally {
            active.decrementAndGet();
          }
          return uri.toString().getBytes(StandardCharsets.UTF_8);
        };
    var props =
        new ImageProperties(1, 2, 1024 * 1024, new Cache(1024 * 1024, 10, Duration.ofHours(1)));
    var service =
        service(store, fetcher, (bytes, variant) -> new Rendered(new byte[] {1}, 10, 10), props);

    var first =
        CompletableFuture.supplyAsync(
            () -> service.rendition(TEXT, FILE, Variant.PREVIEW).orElseThrow());
    firstEntered.await();
    var second =
        CompletableFuture.supplyAsync(
            () -> service.rendition(TEXT, fileTwo, Variant.PREVIEW).orElseThrow());
    Thread.sleep(30);
    assertThat(peak.get()).isEqualTo(1);
    release.countDown();

    first.get();
    second.get();
    assertThat(peak.get()).isEqualTo(1);
  }

  @Test
  void boundsConcurrentInFlightMissesToMaxFetchesPlusMaxConversions() throws Exception {
    var store = new InMemoryStore();
    var item2 = URI.create("http://cellar/item/fig_2");
    var item3 = URI.create("http://cellar/item/fig_3");
    store.replaceSources(
        TEXT,
        URI.create("http://cellar/manifest"),
        List.of(
            new Source(FILE, ITEM, "image/tiff", null),
            new Source("fig_2.tif", item2, "image/tiff", null),
            new Source("fig_3.tif", item3, "image/tiff", null)));
    var fetched = new AtomicInteger();
    var renderEntered = new CountDownLatch(1);
    var releaseRender = new CountDownLatch(1);
    ImageRenderer renderer =
        (bytes, variant) -> {
          renderEntered.countDown();
          try {
            releaseRender.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }
          return new Rendered(new byte[] {1}, 10, 10);
        };
    SourceFetcher fetcher =
        (uri, maxBytes) -> {
          fetched.incrementAndGet();
          return uri.toString().getBytes(StandardCharsets.UTF_8);
        };
    var props =
        new ImageProperties(1, 1, 1024 * 1024, new Cache(1024 * 1024, 10, Duration.ofHours(1)));
    var service = service(store, fetcher, renderer, props);

    var first =
        CompletableFuture.supplyAsync(
            () -> service.rendition(TEXT, FILE, Variant.PREVIEW).orElseThrow());
    renderEntered.await();
    // 1 miss is rendering; maxFetches + maxConversions = 2 permits allow fig_2 to download while
    // blocking fig_3 until a miss completes.
    var second =
        CompletableFuture.supplyAsync(
            () -> service.rendition(TEXT, "fig_2.tif", Variant.PREVIEW).orElseThrow());
    await().atMost(Duration.ofSeconds(5)).until(() -> fetched.get() == 2);
    var third =
        CompletableFuture.supplyAsync(
            () -> service.rendition(TEXT, "fig_3.tif", Variant.PREVIEW).orElseThrow());
    await().atMost(Duration.ofSeconds(5)).until(() -> store.sourceLookups() == 3);
    await()
        .during(Duration.ofMillis(50))
        .atMost(Duration.ofSeconds(1))
        .until(() -> fetched.get() == 2);
    assertThat(fetched.get()).isEqualTo(2);
    releaseRender.countDown();

    first.get();
    second.get();
    third.get();
    assertThat(fetched.get()).isEqualTo(3);
  }

  @Test
  void timesOutWhenJoiningAnInFlightConversionThatDoesNotFinish() throws Exception {
    var store = new InMemoryStore();
    var secondText = new TextId(12);
    store.replaceSources(
        TEXT,
        URI.create("http://cellar/manifest"),
        List.of(new Source(FILE, ITEM, "image/tiff", null)));
    store.replaceSources(
        secondText,
        URI.create("http://cellar/manifest-2"),
        List.of(new Source(FILE, ITEM, "image/tiff", null)));
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    ImageRenderer renderer =
        (bytes, variant) -> {
          entered.countDown();
          try {
            release.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }
          return new Rendered(new byte[] {1, 2, 3}, 100, 50);
        };
    var props =
        new ImageProperties(4, 2, 1024 * 1024, new Cache(1024 * 1024, 10, Duration.ofHours(1)));
    var service =
        service(
            store,
            (uri, maxBytes) -> "tiff-bytes".getBytes(StandardCharsets.UTF_8),
            renderer,
            props,
            Duration.ofMillis(80));

    var first =
        CompletableFuture.supplyAsync(
            () -> service.rendition(TEXT, FILE, Variant.PREVIEW).orElseThrow());
    entered.await();

    assertThatThrownBy(() -> service.rendition(secondText, FILE, Variant.PREVIEW))
        .isInstanceOf(ImageUnavailableException.class)
        .hasMessageContaining("Timed out");
    release.countDown();
    assertThat(first.get().webp()).isEqualTo(new byte[] {1, 2, 3});
  }

  @Test
  void removesSourcesOnTextReplacedAndTextRemoved() {
    var store = new InMemoryStore();
    store.replaceSources(
        TEXT,
        URI.create("http://cellar/manifest"),
        List.of(new Source(FILE, ITEM, "image/tiff", null)));
    var service =
        service(
            store,
            (uri, maxBytes) -> new byte[0],
            (bytes, variant) -> new Rendered(new byte[0], 1, 1));

    service.on(new TextReplaced(TEXT, new TextId(12), "32015R1998", Language.ENG));
    assertThat(service.sourceUris(TEXT)).isEmpty();

    store.replaceSources(
        new TextId(12),
        URI.create("http://cellar/manifest"),
        List.of(new Source(FILE, ITEM, "image/tiff", null)));
    service.on(new TextRemoved(new TextId(12), "32015R1998", Language.ENG));
    assertThat(service.sourceUris(new TextId(12))).isEmpty();
  }

  private static ImageService service(
      ImageStore store, SourceFetcher fetcher, ImageRenderer renderer) {
    var props =
        new ImageProperties(4, 2, 1024 * 1024, new Cache(1024 * 1024, 10, Duration.ofHours(1)));
    return service(store, fetcher, renderer, props);
  }

  private static ImageService service(
      ImageStore store, SourceFetcher fetcher, ImageRenderer renderer, ImageProperties props) {
    return service(store, fetcher, renderer, props, Duration.ofSeconds(30));
  }

  private static ImageService service(
      ImageStore store,
      SourceFetcher fetcher,
      ImageRenderer renderer,
      ImageProperties props,
      Duration waitTimeout) {
    var tx =
        new TransactionTemplate() {
          @Override
          public void executeWithoutResult(Consumer<TransactionStatus> action) {
            action.accept(new SimpleTransactionStatus());
          }
        };
    return ImageService.withWaitTimeout(
        store, fetcher, renderer, props, tx, ObservationRegistry.NOOP, waitTimeout);
  }

  private static final class InMemoryStore implements ImageStore {
    private final ConcurrentMap<String, ImageSource> sources = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Rendition> renditions = new ConcurrentHashMap<>();
    private final AtomicInteger sourceLookupCount = new AtomicInteger();

    @Override
    public void replaceSources(TextId text, URI manifestation, List<Source> list) {
      removeSources(text);
      for (var each : list) {
        sources.put(
            key(text, each.file()),
            new ImageSource(
                text,
                each.file(),
                manifestation,
                each.uri(),
                each.mediaType(),
                each.rdfSha1(),
                null,
                null));
      }
    }

    @Override
    public Optional<ImageSource> findSource(TextId text, String file) {
      sourceLookupCount.incrementAndGet();
      return Optional.ofNullable(sources.get(key(text, file)));
    }

    int sourceLookups() {
      return sourceLookupCount.get();
    }

    @Override
    public Optional<byte[]> findSourceDigest(TextId text, String file) {
      var source = sources.get(key(text, file));
      return Optional.ofNullable(source == null ? null : source.sourceSha256());
    }

    @Override
    public Map<String, URI> sourceUris(TextId text) {
      var prefix = text.value() + ":";
      var result = new java.util.HashMap<String, URI>();
      sources.forEach((k, v) -> {
        if (k.startsWith(prefix)) {
          result.put(v.file(), v.itemUri());
        }
      });
      return result;
    }

    @Override
    public void recordSourceDigest(TextId text, String file, byte[] sha256) {
      sources.computeIfPresent(
          key(text, file),
          (k, old) ->
              new ImageSource(
                  old.text(),
                  old.file(),
                  old.manifestation(),
                  old.itemUri(),
                  old.mediaType(),
                  old.rdfSha1(),
                  sha256.clone(),
                  old.goneReason()));
    }

    @Override
    public void markGone(TextId text, String file, String reason) {
      sources.computeIfPresent(
          key(text, file),
          (k, old) ->
              new ImageSource(
                  old.text(),
                  old.file(),
                  old.manifestation(),
                  old.itemUri(),
                  old.mediaType(),
                  old.rdfSha1(),
                  old.sourceSha256(),
                  old.goneReason() == null ? reason : old.goneReason()));
    }

    @Override
    public void removeSources(TextId text) {
      var prefix = text.value() + ":";
      sources.keySet().removeIf(k -> k.startsWith(prefix));
    }

    @Override
    public Optional<Rendition> findRendition(byte[] sha256, Variant variant, String recipe) {
      return Optional.ofNullable(renditions.get(renditionKey(sha256, variant, recipe)));
    }

    @Override
    public void touchRenditionIfStale(
        byte[] sha256, Variant variant, String recipe, Duration minAge) {}

    @Override
    public void storeRendition(
        byte[] sha256,
        Variant variant,
        String recipe,
        int width,
        int height,
        byte[] webp,
        long maxBytes,
        int evictionBatch) {
      renditions.put(
          renditionKey(sha256, variant, recipe),
          new Rendition(webp, HexFormat.of().formatHex(sha256), recipe, width, height));
    }

    void evictAllRenditions() {
      renditions.clear();
    }

    private static String key(TextId text, String file) {
      return text.value() + ":" + file;
    }

    private static String renditionKey(byte[] sha256, Variant variant, String recipe) {
      return HexFormat.of().formatHex(sha256) + ":" + variant.name() + ":" + recipe;
    }
  }
}
