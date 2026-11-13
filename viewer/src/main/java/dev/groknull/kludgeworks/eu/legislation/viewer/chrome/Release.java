// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.chrome;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * The release of the app, and how a response that only it and the identities in its address decide
 * is cached.
 *
 * <p>The release is {@code build.version} of Boot's build info, which a release build stamps from
 * its tag ({@code --embed_label}). A build without it, in development, gets a release of its own
 * each time it starts. The templates can change from one release to the next, so the release is
 * part of every key that HTML is cached by: in Valkey, and in the entity tag of a response.
 */
@Component
public class Release {
  private static final Logger LOG = LoggerFactory.getLogger(Release.class);

  /**
   * How a browser keeps a response whose address can come to name other texts, for example a
   * version's: it asks again each time, with the entity tag that it has.
   */
  public static final CacheControl REVALIDATED = CacheControl.noCache();

  private final String value;

  @Autowired
  Release(ObjectProvider<BuildProperties> build) {
    this(of(build.getIfAvailable()));
    LOG.info("Release {}: in the entity tags and the keys of cached HTML", value);
  }

  /** A release of this name, for example {@code 2026.11.5}. */
  public Release(String value) {
    this.value = value;
  }

  /** Returns the release, for example {@code 2026.11.5}, or {@code dev-…} in development. */
  public String value() {
    return value;
  }

  /**
   * Returns the entity tag of a response that this release renders from {@code parts} alone, for
   * example the identity of a text. It is weak: Tomcat does not compress a response with a strong
   * entity tag, and {@code If-None-Match} compares tags weakly, so a weak tag is all a browser
   * needs. For example, {@code W/"2026.11.5-42"}.
   */
  public String etag(Object... parts) {
    return Arrays.stream(parts)
        .map(String::valueOf)
        .collect(Collectors.joining("-", "W/\"" + value + "-", "\""));
  }

  /**
   * Tags {@code response} with {@code etag} and {@code cache}, and answers 304 if the browser has
   * it already. Returns whether it answered: if so, the controller writes nothing.
   */
  public static boolean notModified(
      HttpServletRequest request, HttpServletResponse response, CacheControl cache, String etag) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, cache.getHeaderValue());
    return new ServletWebRequest(request, response).checkNotModified(etag);
  }

  private static String of(@Nullable BuildProperties build) {
    var version = build == null ? null : build.getVersion();
    return version != null ? version : "dev-" + Long.toString(System.currentTimeMillis(), 36);
  }

  @Override
  public String toString() {
    return value;
  }
}
