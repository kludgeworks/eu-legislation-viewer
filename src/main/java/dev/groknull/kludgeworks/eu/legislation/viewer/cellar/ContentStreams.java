// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/**
 * Cellar's files (content streams: an item, or one entry of a zip item), fetched one at a time as
 * raw bytes: never parsed, re-encoded or re-indented. A conditional GET with the ETag of the copy
 * we have answers 304 while it is current.
 */
@Component
public class ContentStreams {
  private final CellarHttp http;

  ContentStreams(CellarHttp http) {
    this.http = http;
  }

  /**
   * Fetches {@code uri} into {@code target}: the bytes go to a temporary file next to it, which
   * replaces the target only once complete, so a failed download leaves no partial file.
   *
   * @param etag the ETag of the copy we have, or null to fetch unconditionally
   */
  public Download download(URI uri, @Nullable String etag, Path target) {
    return http.call(() -> fetch(uri, etag, target));
  }

  private Download fetch(URI uri, @Nullable String etag, Path target) {
    return http.client()
        .get()
        .uri(uri)
        .headers(
            headers -> {
              if (etag != null) {
                headers.setIfNoneMatch(etag);
              }
            })
        .exchange(
            (request, response) -> {
              var status = response.getStatusCode();
              if (status.value() == HttpStatus.NOT_MODIFIED.value()) {
                return new Download.NotModified();
              }
              if (status.is5xxServerError()) {
                throw HttpServerErrorException.create(
                    status, response.getStatusText(), response.getHeaders(), new byte[0], null);
              }
              if (status.isError()) {
                throw HttpClientErrorException.create(
                    status, response.getStatusText(), response.getHeaders(), new byte[0], null);
              }
              var headers = response.getHeaders();
              var written = write(response.getBody(), target);
              var lastModified = headers.getLastModified();
              return new Download.Downloaded(
                  headers.getETag(),
                  lastModified < 0 ? null : Instant.ofEpochMilli(lastModified),
                  headers.getContentType() == null ? null : headers.getContentType().toString(),
                  written.size(),
                  written.sha1());
            });
  }

  private record Written(long size, String sha1) {}

  private static Written write(java.io.InputStream body, Path target) {
    try {
      var directory = target.toAbsolutePath().getParent();
      Files.createDirectories(directory);
      var part = Files.createTempFile(directory, target.getFileName().toString(), ".part");
      try {
        var sha1 = MessageDigest.getInstance("SHA-1");
        long size;
        try (var in = body;
            var out = new DigestOutputStream(Files.newOutputStream(part), sha1)) {
          size = in.transferTo(out);
        }
        Files.move(
            part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return new Written(size, HexFormat.of().formatHex(sha1.digest()));
      } finally {
        Files.deleteIfExists(part);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
