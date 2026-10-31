// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expression;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.ExpressionIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expressions;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Expressions, converted once and kept in Valkey (Design: Caching, Converting off the request
 * path): the legal-text XML, gzipped, under a key of the document, the language and the converter's
 * version, so a new converter never reads an old one's output. A miss converts under a lock in
 * Valkey on the key, so a text is converted once across instances while other requests for it wait
 * for the result. A document without Formex in the language is remembered for a while, as is one
 * the converter refused or failed on (a ConversionException, read back as one of the same kind), so
 * neither is fetched and converted for every request; Cellar failing is never kept. Everything kept
 * is derived, and Valkey may evict any of it.
 */
@Component
class ValkeyExpressions implements Expressions {

  /** What is kept for a document without Formex in the language. */
  private static final byte[] ABSENT = new byte[0];

  /**
   * What starts what is kept for a text the converter refused or failed on, before its kind and its
   * message, a line each: never the start of gzip (0x1f 0x8b), which a text is kept as.
   */
  private static final String FAILED = "failed\n";

  /** Releases the lock only if it is still ours: it may have expired and been taken by another. */
  private static final RedisScript<Long> UNLOCK =
      RedisScript.of(
          "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) end"
              + " return 0",
          Long.class);

  private final RedisTemplate<String, byte[]> valkey;
  private final FormexConversion conversion;
  private final LegalTextReader reader;
  private final Processor saxon;
  private final StoreProperties properties;

  ValkeyExpressions(
      RedisTemplate<String, byte[]> valkey,
      FormexConversion conversion,
      LegalTextReader reader,
      Processor saxon,
      StoreProperties properties) {
    this.valkey = valkey;
    this.conversion = conversion;
    this.reader = reader;
    this.saxon = saxon;
    this.properties = properties;
  }

  @Override
  public Optional<Expression> find(ExpressionIdentifier id) {
    var key = key(id);
    var lock = key + ":lock";
    while (true) {
      var stored = valkey.opsForValue().get(key);
      if (stored != null) {
        return read(stored);
      }
      var token = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
      if (Boolean.TRUE.equals(
          valkey.opsForValue().setIfAbsent(lock, token, properties.lockTimeout()))) {
        try {
          return convert(id, key);
        } finally {
          valkey.execute(UNLOCK, List.of(lock), (Object) token);
        }
      }
      pause();
    }
  }

  /** The key of {@code id}'s text: viewer:legal-text:1.0.0:02015R1998-20260322:ENG. */
  String key(ExpressionIdentifier id) {
    return String.join(
        ":",
        "viewer:legal-text",
        conversion.converterVersion(),
        id.document(),
        id.language().code());
  }

  /** Converts and keeps the text, holding the lock; unless another kept it just before. */
  private Optional<Expression> convert(ExpressionIdentifier id, String key) {
    var stored = valkey.opsForValue().get(key);
    if (stored != null) {
      return read(stored);
    }
    Optional<XdmNode> legalText;
    try {
      legalText = conversion.convert(id);
    } catch (ConversionException e) {
      valkey.opsForValue().set(key, failure(e), properties.failedFor());
      throw e;
    }
    if (legalText.isEmpty()) {
      valkey.opsForValue().set(key, ABSENT, properties.absentFor());
      return Optional.empty();
    }
    valkey.opsForValue().set(key, gzip(legalText.get()));
    return Optional.of(reader.read(legalText.get()));
  }

  private Optional<Expression> read(byte[] stored) {
    if (stored.length == 0) {
      return Optional.empty();
    }
    var failed = FAILED.getBytes(StandardCharsets.UTF_8);
    if (Arrays.equals(
        stored, 0, Math.min(failed.length, stored.length), failed, 0, failed.length)) {
      throw failed(new String(stored, StandardCharsets.UTF_8).substring(FAILED.length()));
    }
    try (var xml = new GZIPInputStream(new ByteArrayInputStream(stored))) {
      return Optional.of(reader.read(saxon.newDocumentBuilder().build(new StreamSource(xml))));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (SaxonApiException e) {
      throw new InvalidLegalTextException("a stored text isn't XML: " + e.getMessage(), e);
    }
  }

  /** What is kept for {@code e}: FAILED, then its kind (failed() reads it back) and its message. */
  private static byte[] failure(ConversionException e) {
    var kind =
        switch (e) {
          case OutOfScopeException _ -> "out-of-scope";
          case UnsupportedFormexException _ -> "unsupported";
          default -> "failed";
        };
    return (FAILED + kind + "\n" + Objects.toString(e.getMessage(), ""))
        .getBytes(StandardCharsets.UTF_8);
  }

  /** The failure kept as {@code kept}, its kind and its message, as it was thrown. */
  private static ConversionException failed(String kept) {
    var kind = kept.substring(0, kept.indexOf('\n'));
    var message = kept.substring(kind.length() + 1);
    return switch (kind) {
      case "out-of-scope" -> new OutOfScopeException(message);
      case "unsupported" -> new UnsupportedFormexException(message);
      default -> new ConversionException(message);
    };
  }

  private byte[] gzip(XdmNode legalText) {
    var bytes = new ByteArrayOutputStream();
    try (var out = new GZIPOutputStream(bytes)) {
      saxon.newSerializer(out).serializeNode(legalText);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (SaxonApiException e) {
      throw new IllegalStateException("legal text doesn't serialise", e);
    }
    return bytes.toByteArray();
  }

  private void pause() {
    try {
      Thread.sleep(properties.pollInterval());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for a conversion", e);
    }
  }
}
