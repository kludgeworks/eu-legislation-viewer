// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Path template and URL builders for converted Formex WebP images. */
public final class ImageUrls {
  /** Path pattern of the image endpoint. */
  public static final String PATH = "/images/{id}/{file}";

  private ImageUrls() {}

  /** Returns the preview image path for {@code file} in {@code text}. */
  public static String preview(TextId text, String file) {
    return "/images/" + text.value() + "/" + encodeSegment(file);
  }

  /** Returns the versioned preview image path for {@code file} in {@code text}. */
  public static String preview(TextId text, String file, String version) {
    return preview(text, file) + "?v=" + encodeSegment(version);
  }

  /** Returns the full-size image path for {@code file} in {@code text}. */
  public static String original(TextId text, String file) {
    return preview(text, file) + "?variant=original";
  }

  /** Returns the versioned full-size image path for {@code file} in {@code text}. */
  public static String original(TextId text, String file, String version) {
    return preview(text, file) + "?variant=original&v=" + encodeSegment(version);
  }

  private static String encodeSegment(String segment) {
    // URLEncoder uses '+' for spaces; RFC 3986 path segments use '%20'.
    return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
