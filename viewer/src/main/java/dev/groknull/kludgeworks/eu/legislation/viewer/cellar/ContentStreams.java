// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Cellar's files (content streams: an item, or one entry of a zip item), read one at a time as they
 * come: nothing is buffered or kept, so a file of any size costs only what its reader makes of it.
 */
@Component
public class ContentStreams {
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
                read = reader.apply(body);
              } catch (RuntimeException e) {
                throw body.failure == null ? e : failed(uri, body.failure);
              }
              if (body.failure != null) {
                throw failed(uri, body.failure);
              }
              return read;
            });
  }

  private static ResourceAccessException failed(URI uri, IOException e) {
    return new ResourceAccessException("Reading " + uri + " failed: " + e.getMessage(), e);
  }

  /** A body that remembers the first failure reading it, for the reader may swallow it. */
  private static final class Watched extends FilterInputStream {
    private @Nullable IOException failure;

    Watched(InputStream in) {
      super(in);
    }

    @Override
    public int read() throws IOException {
      try {
        return super.read();
      } catch (IOException e) {
        throw remembered(e);
      }
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      try {
        return super.read(bytes, offset, length);
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
