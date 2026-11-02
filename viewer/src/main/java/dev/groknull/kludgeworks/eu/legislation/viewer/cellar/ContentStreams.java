// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Cellar's files (content streams: an item, or one entry of a zip item), read one at a time as they
 * come: nothing is buffered or kept, so a file of any size costs only what its reader makes of it.
 * Cellar keeps some files gzipped (consolidations' Formex made from 2005 to 2018), sent as they are
 * kept: those are read unzipped, so a reader always has the file itself.
 */
@Component
public class ContentStreams {
  private static final Logger LOG = LoggerFactory.getLogger(ContentStreams.class);

  private final RestClient client;

  ContentStreams(@Qualifier(CellarConfiguration.CLIENT) RestClient client) {
    this.client = client;
  }

  /**
   * What {@code reader} makes of {@code uri}'s bytes, read while the response is open. Reading them
   * failing (the connection lost) is Cellar failing, a ResourceAccessException, whatever the reader
   * made of it: a reader that turns the failure into a result of its own never hides it.
   */
  public <T> T read(URI uri, Function<InputStream, T> reader) {
    var started = System.nanoTime();
    return client
        .get()
        .uri(uri)
        .exchange(
            (request, response) -> {
              var status = response.getStatusCode();
              if (status.is5xxServerError()) {
                throw HttpServerErrorException.create(
                    status, response.getStatusText(), response.getHeaders(), new byte[0], null);
              }
              if (status.isError()) {
                throw HttpClientErrorException.create(
                    status, response.getStatusText(), response.getHeaders(), new byte[0], null);
              }
              var body = new Watched(response.getBody());
              T read;
              try {
                read = reader.apply(unzipped(body));
              } catch (RuntimeException e) {
                throw body.failure == null ? e : failed(uri, body.failure);
              }
              if (body.failure != null) {
                throw failed(uri, body.failure);
              }
              LOG.debug(
                  "Read {}: {} KB in {} ms",
                  uri,
                  body.count / 1024,
                  Duration.ofNanos(System.nanoTime() - started).toMillis());
              return read;
            });
  }

  /** {@code body}, unzipped if it starts as gzip does (0x1f 0x8b). */
  private static InputStream unzipped(InputStream body) {
    var in = new BufferedInputStream(body);
    try {
      in.mark(2);
      var gzip = in.read() == 0x1f && in.read() == 0x8b;
      in.reset();
      return gzip ? new GZIPInputStream(in) : in;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ResourceAccessException failed(URI uri, IOException e) {
    return new ResourceAccessException("Reading " + uri + " failed: " + e.getMessage(), e);
  }

  /**
   * A body that remembers the first failure reading it, for the reader may swallow it, and counts
   * the bytes read (as sent: gzipped if Cellar keeps the file so).
   */
  private static final class Watched extends FilterInputStream {
    private @Nullable IOException failure;
    private long count;

    Watched(InputStream in) {
      super(in);
    }

    @Override
    public int read() throws IOException {
      try {
        var b = super.read();
        if (b >= 0) {
          count++;
        }
        return b;
      } catch (IOException e) {
        throw remembered(e);
      }
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      try {
        var n = super.read(bytes, offset, length);
        if (n > 0) {
          count += n;
        }
        return n;
      } catch (IOException e) {
        throw remembered(e);
      }
    }

    private IOException remembered(IOException e) {
      if (failure == null) {
        failure = e;
      }
      return e;
    }
  }
}
