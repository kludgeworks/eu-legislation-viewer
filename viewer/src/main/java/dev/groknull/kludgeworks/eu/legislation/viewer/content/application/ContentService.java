// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextReplaced;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextStored;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Outlines;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.OutlinesVersion;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.PublicTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import net.sf.saxon.s9api.XdmNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores, reads and outlines texts.
 *
 * <ul>
 *   <li>To store a text, the model writes it as JSON. If the SHA-256 of the JSON is the digest of
 *       the text kept for the version and language, nothing more is done. If not, the service
 *       outlines the text, deletes the text before it and keeps the new one.
 *   <li>To read a text, the service reads its JSON and decodes it.
 *   <li>To outline a text, the service reads the outline that it kept when it stored the text. It
 *       keeps the outlines read last in the process. The decoded texts are not kept. A text whose
 *       outline other outline rules made ({@link OutlinesVersion}) is outlined again from its JSON,
 *       the first time it is outlined.
 * </ul>
 *
 * <p>Storing and reading are each one transaction, one observation and one log line. The
 * observations are {@code content.store}, {@code content.read} and {@code content.outline}. Each
 * gives a span and a timer.
 */
@Service
class ContentService implements Content {
  private static final Logger LOG = LoggerFactory.getLogger(ContentService.class);

  private final TextStore store;
  private final ApplicationEventPublisher events;
  private final ObservationRegistry observations;

  ContentService(
      TextStore store, ApplicationEventPublisher events, ObservationRegistry observations) {
    this.store = store;
    this.events = events;
    this.observations = observations;
  }

  @Override
  @Transactional
  public TextId store(String celex, Language language, String converter, XdmNode legalText) {
    return Observation.createNotStarted("content.store", observations)
        .observe(() -> doStore(celex, language, converter, legalText));
  }

  private TextId doStore(String celex, Language language, String converter, XdmNode legalText) {
    var start = System.nanoTime();
    var json = Encoder.encode(legalText);
    var digest = sha256(json);
    var kept = store.lock(celex, language);
    if (kept.isPresent() && MessageDigest.isEqual(kept.get().digest(), digest)) {
      LOG.info(
          "Text {} of {} in {} is unchanged: {} KB of JSON, in {} ms",
          kept.get().id(),
          celex,
          language.code(),
          json.length / 1024,
          millis(start));
      return kept.get().id();
    }
    var outline = Outlines.of(Decoder.element(json));
    kept.ifPresent(old -> store.delete(old.id()));
    var added =
        store.add(celex, language, converter, digest, outline, OutlinesVersion.value(), json);
    events.publishEvent(
        kept.isPresent()
            ? new TextReplaced(kept.get().id(), added.id(), celex, language)
            : new TextStored(added.id()));
    LOG.info(
        "Stored text {} of {} in {}{}: {} KB of JSON, {} KB stored, in {} ms",
        added.id(),
        celex,
        language.code(),
        kept.map(old -> ", replacing " + old.id()).orElse(""),
        json.length / 1024,
        added.bytes() / 1024,
        millis(start));
    return added.id();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Element> read(TextId text) {
    return Observation.createNotStarted("content.read", observations).observe(() -> doRead(text));
  }

  private Optional<Element> doRead(TextId text) {
    var start = System.nanoTime();
    var json = store.json(text);
    if (json.isEmpty()) {
      LOG.debug("No text {}", text);
      return Optional.empty();
    }
    var read = millis(start);
    var element = Decoder.element(json.get());
    LOG.debug(
        "Read text {}: {} KB of JSON read in {} ms, decoded in {} ms",
        text,
        json.get().length / 1024,
        read,
        millis(start) - read);
    return Optional.of(element);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PublicText> publicText(TextId text) {
    return read(text)
        .map(
            element -> {
              var start = System.nanoTime();
              var model = PublicTexts.of(element);
              LOG.debug("Text {} in the public model in {} ms", text, millis(start));
              return model;
            });
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PublicText.Extract> publicOccurrence(TextId text, String eId) {
    var start = System.nanoTime();
    var occurrence = read(text).flatMap(element -> PublicTexts.extract(element, eId));
    LOG.debug(
        "Occurrence {} of text {} in the public model: {} in {} ms",
        eId,
        text,
        occurrence.isPresent() ? "found" : "none",
        millis(start));
    return occurrence;
  }

  @Override
  public PublicText.Occurrence publicOccurrence(Element occurrence) {
    return PublicTexts.occurrence(occurrence);
  }

  @Override
  public PublicText.Edited publicWording(Element occurrence) {
    return PublicTexts.edited(occurrence);
  }

  /**
   * The outline is cached in the process, by the identity of the text ({@link ContentCaches}). A
   * text whose outline other outline rules made is outlined again from its JSON, once, and its new
   * outline is kept.
   */
  @Override
  @Cacheable(cacheNames = ContentCaches.OUTLINES, unless = "#result == null")
  @Transactional
  public Optional<Outline> outline(TextId text) {
    return Observation.createNotStarted("content.outline", observations)
        .observe(() -> doOutline(text));
  }

  private Optional<Outline> doOutline(TextId text) {
    var start = System.nanoTime();
    var outline = store.outline(text, OutlinesVersion.value()).or(() -> outlineAgain(text, start));
    LOG.debug(
        "Outlined text {}, not cached: {} in {} ms",
        text,
        outline.isPresent() ? "kept" : "no text",
        millis(start));
    return outline;
  }

  /**
   * Outlines the text again from its JSON, and keeps the new outline. Returns empty if no such text
   * is kept.
   */
  private Optional<Outline> outlineAgain(TextId text, long start) {
    return store
        .json(text)
        .map(
            json -> {
              var outline = Outlines.of(Decoder.element(json));
              store.outline(text, outline, OutlinesVersion.value());
              LOG.info(
                  "Outlined text {} again, by the outline rules {}: {} occurrences tagged, in {}"
                      + " ms",
                  text,
                  OutlinesVersion.value(),
                  outline.tags().size(),
                  millis(start));
              return outline;
            });
  }

  @Override
  @Transactional(readOnly = true)
  public boolean contains(TextId text) {
    return store.contains(text);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, TextId> texts(Collection<String> celexes, Language language) {
    return celexes.isEmpty() ? Map.of() : store.texts(celexes, language);
  }

  private static byte[] sha256(byte[] json) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(json);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every JDK has SHA-256", e);
    }
  }

  private static long millis(long since) {
    return (System.nanoTime() - since) / 1_000_000;
  }
}
