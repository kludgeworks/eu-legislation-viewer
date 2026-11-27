// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.CellarFailures;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.ContentStreams;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.ContentStreams.StreamTooLargeException;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.ImageGoneException;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.ImageUnavailableException;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.application.SourceFetcher;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/** Downloads Formex TIFF items from Cellar through {@link ContentStreams}. */
@Component
class CellarSourceFetcher implements SourceFetcher {

  private final ContentStreams streams;

  CellarSourceFetcher(ContentStreams streams) {
    this.streams = streams;
  }

  @Override
  public byte[] fetch(URI uri, long maxBytes) {
    try {
      return streams.read(
          uri,
          maxBytes,
          in -> {
            try {
              return in.readAllBytes();
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
    } catch (HttpClientErrorException e) {
      var status = e.getStatusCode();
      if (status == HttpStatus.NOT_FOUND || status == HttpStatus.GONE) {
        throw new ImageGoneException("Cellar does not have image " + uri + ": " + status, e);
      }
      throw new ImageUnavailableException("Fetching image " + uri + " failed: " + status, e);
    } catch (StreamTooLargeException e) {
      throw new ImageGoneException("Image " + uri + " rejected: " + e.getMessage(), e);
    } catch (RuntimeException e) {
      if (CellarFailures.unavailable(e)) {
        throw new ImageUnavailableException("Cellar unavailable for image " + uri, e);
      }
      throw e;
    }
  }
}
