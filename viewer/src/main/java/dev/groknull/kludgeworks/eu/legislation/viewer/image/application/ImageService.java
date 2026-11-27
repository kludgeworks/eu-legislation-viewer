// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextRemoved;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextReplaced;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.ImageGoneException;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.ImageUnavailableException;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.model.ImageSource;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.model.RecipeVersion;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Coordinates image downloads from Cellar, TIFF to WebP conversion, single-flight coalescing,
 * permanent failure recording, and size-limited Postgres caching.
 */
@Service
class ImageService implements Images {
  private static final Logger LOG = LoggerFactory.getLogger(ImageService.class);

  private final ImageStore store;
  private final SourceFetcher fetcher;
  private final ImageRenderer renderer;
  private final ImageProperties properties;
  private final TransactionTemplate transactions;
  private final ObservationRegistry observations;
  private final Semaphore fetches;
  private final Semaphore workers;
  private final Semaphore misses;
  private final ConcurrentMap<FlightKey, CompletableFuture<Rendition>> inFlight =
      new ConcurrentHashMap<>();

  ImageService(
      ImageStore store,
      SourceFetcher fetcher,
      ImageRenderer renderer,
      ImageProperties properties,
      TransactionTemplate transactions,
      ObservationRegistry observations) {
    this.store = store;
    this.fetcher = fetcher;
    this.renderer = renderer;
    this.properties = properties;
    this.transactions = transactions;
    this.observations = observations;
    this.fetches = new Semaphore(properties.maxFetches());
    this.workers = new Semaphore(properties.maxConversions());
    this.misses = new Semaphore(properties.maxFetches() + properties.maxConversions());
  }

  @Override
  @Transactional
  public void register(TextId text, URI manifestation, List<Source> sources) {
    store.replaceSources(text, manifestation, sources);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<String> sourceDigest(TextId text, String file) {
    return store.findSourceDigest(text, file).map(HexFormat.of()::formatHex);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, URI> sourceUris(TextId text) {
    return store.sourceUris(text);
  }

  @Override
  public String version() {
    return RecipeVersion.value();
  }

  @Override
  public Optional<Rendition> rendition(TextId text, String file, Variant variant) {
    var source = store.findSource(text, file);
    if (source.isEmpty()) {
      return Optional.empty();
    }
    var foundSource = source.get();
    if (foundSource.goneReason() != null) {
      throw new ImageGoneException(foundSource.goneReason());
    }
    var recipe = RecipeVersion.value();
    var existing = cached(foundSource, variant, recipe);
    if (existing.isPresent()) {
      return existing;
    }
    var pinnedHex =
        foundSource.sourceSha256() == null
            ? null
            : HexFormat.of().formatHex(foundSource.sourceSha256());
    var key = new FlightKey(foundSource.itemUri(), variant, pinnedHex, foundSource.rdfSha1());
    var created = new CompletableFuture<Rendition>();
    var running = inFlight.putIfAbsent(key, created);
    if (running != null) {
      var rendered = await(running, properties.waitTimeout());
      if (foundSource.sourceSha256() == null) {
        var digest = HexFormat.of().parseHex(rendered.sourceSha256());
        transactions.executeWithoutResult(
            status -> store.recordSourceDigest(foundSource.text(), foundSource.file(), digest));
      }
      return Optional.of(rendered);
    }
    try {
      var rendered =
          Objects.requireNonNull(
              Observation.createNotStarted("image.render", observations)
                  .observe(() -> loadAndRender(foundSource, variant, recipe)));
      created.complete(rendered);
      return Optional.of(rendered);
    } catch (RuntimeException | Error e) {
      created.completeExceptionally(e);
      throw e;
    } finally {
      inFlight.remove(key, created);
    }
  }

  @ApplicationModuleListener
  void on(TextReplaced event) {
    store.removeSources(event.replaced());
  }

  @ApplicationModuleListener
  void on(TextRemoved event) {
    store.removeSources(event.text());
  }

  private Optional<Rendition> cached(ImageSource source, Variant variant, String recipe) {
    var sha256 = source.sourceSha256();
    if (sha256 == null) {
      return Optional.empty();
    }
    var found = store.findRendition(sha256, variant, recipe);
    found.ifPresent(ignored ->
        store.touchRenditionIfStale(sha256, variant, recipe, properties.cache().touchInterval()));
    return found;
  }

  private Rendition loadAndRender(ImageSource source, Variant variant, String recipe) {
    var rechecked = cached(source, variant, recipe);
    if (rechecked.isPresent()) {
      return rechecked.get();
    }
    var started = System.nanoTime();
    var deadlineNanos = started + properties.waitTimeout().toNanos();
    try {
      acquirePermit(
          misses,
          deadlineNanos,
          "Timed out waiting for image cache miss slot",
          "Interrupted waiting for image cache miss slot");
      try {
        var tiff =
            Objects.requireNonNull(
                Observation.createNotStarted("image.fetch", observations)
                    .observe(() -> fetchWithPermit(source.itemUri(), deadlineNanos)));
        var sha256 = digest("SHA-256", tiff);
        verifyDigests(source, tiff, sha256);
        var sha256Hex = HexFormat.of().formatHex(sha256);
        var existing = store.findRendition(sha256, variant, recipe);
        if (existing.isPresent()) {
          transactions.executeWithoutResult(status -> {
            store.recordSourceDigest(source.text(), source.file(), sha256);
            store.touchRenditionIfStale(
                sha256, variant, recipe, properties.cache().touchInterval());
          });
          return existing.get();
        }
        var rendered = renderWithPermit(tiff, variant, deadlineNanos);
        transactions.executeWithoutResult(status -> {
          store.recordSourceDigest(source.text(), source.file(), sha256);
          store.storeRendition(
              sha256,
              variant,
              recipe,
              rendered.width(),
              rendered.height(),
              rendered.webp(),
              properties.cache().maxBytes(),
              properties.cache().evictionBatch());
        });
        LOG.debug(
            "Converted {} ({}) of text {}: {} KB TIFF -> {}x{} {} KB WebP in {} ms",
            source.file(),
            variant,
            source.text().value(),
            tiff.length / 1024,
            rendered.width(),
            rendered.height(),
            rendered.webp().length / 1024,
            Duration.ofNanos(System.nanoTime() - started).toMillis());
        return new Rendition(
            rendered.webp(), sha256Hex, recipe, rendered.width(), rendered.height());
      } finally {
        misses.release();
      }
    } catch (ImageGoneException gone) {
      var reason = Objects.requireNonNullElse(gone.getMessage(), "Image permanently unavailable");
      transactions.executeWithoutResult(
          status -> store.markGone(source.text(), source.file(), reason));
      LOG.warn(
          "Marked image {} of text {} permanently gone: {}",
          source.file(),
          source.text().value(),
          reason);
      throw gone;
    } catch (ImageUnavailableException unavailable) {
      LOG.warn(
          "Image {} of text {} is temporarily unavailable: {}",
          source.file(),
          source.text().value(),
          unavailable.getMessage());
      throw unavailable;
    }
  }

  private byte[] fetchWithPermit(URI itemUri, long deadlineNanos) {
    acquirePermit(
        fetches,
        deadlineNanos,
        "Timed out waiting for Cellar download slot",
        "Interrupted waiting for Cellar download slot");
    try {
      return fetcher.fetch(itemUri, properties.maxSourceBytes());
    } finally {
      fetches.release();
    }
  }

  private ImageRenderer.Rendered renderWithPermit(
      byte[] tiff, Variant variant, long deadlineNanos) {
    acquirePermit(
        workers,
        deadlineNanos,
        "Timed out waiting for image conversion worker",
        "Interrupted waiting for image conversion worker");
    try {
      return renderer.render(tiff, variant);
    } finally {
      workers.release();
    }
  }

  private static void acquirePermit(
      Semaphore permit, long deadlineNanos, String timeoutMessage, String interruptedMessage) {
    var remainingNanos = deadlineNanos - System.nanoTime();
    if (remainingNanos <= 0) {
      throw new ImageUnavailableException(timeoutMessage);
    }
    try {
      if (!permit.tryAcquire(remainingNanos, TimeUnit.NANOSECONDS)) {
        throw new ImageUnavailableException(timeoutMessage);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ImageUnavailableException(interruptedMessage, e);
    }
  }

  private static void verifyDigests(ImageSource source, byte[] tiff, byte[] sha256) {
    var pinned = source.sourceSha256();
    if (pinned != null && !MessageDigest.isEqual(pinned, sha256)) {
      throw new ImageGoneException(
          "Source bytes changed for " + source.file() + " in text " + source.text().value());
    }
    var rdfSha1 = source.rdfSha1();
    if (rdfSha1 != null && rdfSha1.length() == 40) {
      var actualSha1 = HexFormat.of().formatHex(digest("SHA-1", tiff));
      if (!actualSha1.equalsIgnoreCase(rdfSha1)) {
        throw new ImageGoneException(
            "Source SHA-1 mismatch for " + source.file() + " in text " + source.text().value());
      }
    }
  }

  private static Rendition await(CompletableFuture<Rendition> future, Duration timeout) {
    try {
      return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      throw new ImageUnavailableException("Timed out waiting for in-flight image", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ImageUnavailableException("Interrupted waiting for in-flight image", e);
    } catch (ExecutionException e) {
      var cause = e.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new ImageUnavailableException("Image conversion failed", cause == null ? e : cause);
    }
  }

  private static byte[] digest(String algorithm, byte[] bytes) {
    try {
      return MessageDigest.getInstance(algorithm).digest(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Missing standard digest " + algorithm, e);
    }
  }

  private record FlightKey(
      URI itemUri, Variant variant, @Nullable String pinnedSha256, @Nullable String rdfSha1) {}
}
