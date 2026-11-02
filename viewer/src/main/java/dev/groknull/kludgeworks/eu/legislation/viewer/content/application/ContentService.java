// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Segment;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Segmentation;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.Optional;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import net.sf.saxon.s9api.XdmNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Storing a text: encoded into its tree and cut into segments (the model), then kept unless it is
 * already; reading or rebuilding one: its tree read back and decoded. Each is one transaction, one
 * observation (content.store, content.read, content.rebuild: a span and a timer) and one log line.
 */
@Service
class ContentService implements Content {
  private static final Logger LOG = LoggerFactory.getLogger(ContentService.class);

  private final TreeStore store;
  private final ObservationRegistry observations;

  ContentService(TreeStore store, ObservationRegistry observations) {
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
    var segments = Segmentation.of(tree);
    var encodedIn = millis(start);
    var root = tree.root();
    if (store.contains(root)) {
      LOG.info(
          "{} is stored already: {} nodes, encoded in {} ms", root, tree.nodes().size(), encodedIn);
      return root;
    }
    var written = System.nanoTime();
    var added = store.add(tree, segments);
    var bytes = tree.nodes().values().stream().mapToLong(n -> n.content().length).sum();
    LOG.info(
        "Stored {}: {} of {} nodes new, {} KB of JSON, {} segment(s); encoded in {} ms, written in"
            + " {} ms",
        root,
        added,
        tree.nodes().size(),
        bytes / 1024,
        segments.size(),
        encodedIn,
        millis(written));
    return root;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Segment> segments(ContentHash root) {
    return store.segments(root);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Element> read(ContentHash root) {
    return Observation.createNotStarted("content.read", observations).observe(() -> doRead(root));
  }

  private Optional<Element> doRead(ContentHash root) {
    var start = System.nanoTime();
    var tree = store.tree(root);
    if (tree.isEmpty()) {
      LOG.debug("Nothing stored at {}", root);
      return Optional.empty();
    }
    var read = millis(start);
    var element = Decoder.element(tree.get());
    LOG.debug(
        "Read {}: {} nodes read in {} ms, decoded in {} ms",
        root,
        tree.get().nodes().size(),
        read,
        millis(start) - read);
    return Optional.of(element);
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
    var tree = store.tree(root);
    if (tree.isEmpty()) {
      LOG.debug("Nothing stored at {}", root);
      return false;
    }
    var read = millis(start);
    Decoder.write(tree.get(), out);
    LOG.debug(
        "Rebuilt {}: {} nodes read in {} ms, written in {} ms",
        root,
        tree.get().nodes().size(),
        read,
        millis(start) - read);
    return true;
  }

  private static long millis(long since) {
    return (System.nanoTime() - since) / 1_000_000;
  }
}
