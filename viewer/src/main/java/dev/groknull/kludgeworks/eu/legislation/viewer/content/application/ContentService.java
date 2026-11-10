// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Chunker;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Outlines;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Optional;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import net.sf.saxon.s9api.XdmNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores, reads, rebuilds and outlines texts.
 *
 * <ul>
 *   <li>To store a text, the model encodes it into its tree, cuts the tree into chunks and outlines
 *       it. The service then keeps the chunks and the outline, unless the text is kept already.
 *   <li>To read or rebuild a text, the service reads its chunks back and decodes them.
 *   <li>To outline a text, the service reads the outline that it kept when it stored the text.
 * </ul>
 *
 * <p>Each operation is one transaction, one observation and one log line. The observations are
 * {@code content.store}, {@code content.read}, {@code content.outline} and {@code content.rebuild}.
 * Each gives a span and a timer.
 */
@Service
class ContentService implements Content {
  private static final Logger LOG = LoggerFactory.getLogger(ContentService.class);

  private final TextStore store;
  private final ObservationRegistry observations;

  ContentService(TextStore store, ObservationRegistry observations) {
    this.store = store;
    this.observations = observations;
  }

  @Override
  @Transactional
  public ContentHash store(XdmNode legalText) {
    return Observation.createNotStarted("content.store", observations)
        .observe(() -> doStore(legalText));
  }

  private ContentHash doStore(XdmNode legalText) {
    var start = System.nanoTime();
    var tree = Encoder.encode(legalText);
    var root = tree.root();
    if (store.contains(root)) {
      LOG.info(
          "{} is stored already: {} nodes, encoded in {} ms",
          root,
          tree.nodes().size(),
          millis(start));
      return root;
    }
    var chunks = Chunker.of(tree);
    var outline = Outlines.of(tree);
    var encodedIn = millis(start);
    var written = System.nanoTime();
    var added = store.add(root, chunks, outline);
    var bytes = chunks.bodies().values().stream().mapToLong(body -> body.length).sum();
    LOG.info(
        "Stored {}: {} of {} chunks new, {} KB of JSON, {} nodes; encoded in {} ms, written in {}"
            + " ms",
        root,
        added,
        chunks.bodies().size(),
        bytes / 1024,
        tree.nodes().size(),
        encodedIn,
        millis(written));
    return root;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Element> read(ContentHash root) {
    return Observation.createNotStarted("content.read", observations).observe(() -> doRead(root));
  }

  private Optional<Element> doRead(ContentHash root) {
    var start = System.nanoTime();
    var chunks = store.chunks(root);
    if (chunks.isEmpty()) {
      LOG.debug("Nothing stored at {}", root);
      return Optional.empty();
    }
    var read = millis(start);
    var element = Decoder.element(chunks.get());
    LOG.debug(
        "Read {}: {} chunks read in {} ms, decoded in {} ms",
        root,
        chunks.get().bodies().size(),
        read,
        millis(start) - read);
    return Optional.of(element);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Outline> outline(ContentHash root) {
    return Observation.createNotStarted("content.outline", observations)
        .observe(() -> doOutline(root));
  }

  private Optional<Outline> doOutline(ContentHash root) {
    var start = System.nanoTime();
    var outline = store.outline(root);
    LOG.debug(
        "Outlined {}: {} in {} ms",
        root,
        outline.isPresent() ? "kept" : "nothing stored",
        millis(start));
    return outline;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean rebuild(ContentHash root, XMLStreamWriter out) throws XMLStreamException {
    var observation = Observation.start("content.rebuild", observations);
    try (var scope = observation.openScope()) {
      return doRebuild(root, out);
    } catch (XMLStreamException | RuntimeException e) {
      observation.error(e);
      throw e;
    } finally {
      observation.stop();
    }
  }

  private boolean doRebuild(ContentHash root, XMLStreamWriter out) throws XMLStreamException {
    var start = System.nanoTime();
    var chunks = store.chunks(root);
    if (chunks.isEmpty()) {
      LOG.debug("Nothing stored at {}", root);
      return false;
    }
    var read = millis(start);
    Decoder.write(chunks.get(), out);
    LOG.debug(
        "Rebuilt {}: {} chunks read in {} ms, written in {} ms",
        root,
        chunks.get().bodies().size(),
        read,
        millis(start) - read);
    return true;
  }

  private static long millis(long since) {
    return (System.nanoTime() - since) / 1_000_000;
  }
}
