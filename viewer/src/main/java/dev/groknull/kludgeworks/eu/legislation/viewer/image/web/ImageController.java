// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.ImageGoneException;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.ImageUnavailableException;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.ImageUrls;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.model.RecipeVersion;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Release;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Serves WebP images converted from Formex TIFF files for a stored text.
 *
 * <p>When the URL carries the current version token ({@code ?v=...}), responses are immutable for a
 * year because a {@link TextId} never changes its content. Unversioned requests use {@link
 * Release#REVALIDATED} with an entity tag and return {@code 304} without reading bytes when the
 * saved digest matches {@code If-None-Match}.
 */
@Controller
class ImageController {
  private static final MediaType IMAGE_WEBP = MediaType.parseMediaType("image/webp");
  private static final CacheControl IMMUTABLE =
      CacheControl.maxAge(Duration.ofDays(365)).cachePublic();
  private static final CacheControl NO_STORE = CacheControl.noStore();

  private final Images images;

  ImageController(Images images) {
    this.images = images;
  }

  @GetMapping(path = ImageUrls.PATH)
  ResponseEntity<byte[]> image(
      @PathVariable TextId id,
      @PathVariable String file,
      @RequestParam(defaultValue = "preview") String variant,
      @RequestParam(required = false) @Nullable String v,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (file.isEmpty()
        || file.contains("/")
        || file.contains("\\")
        || file.contains("..")
        || file.indexOf('\r') >= 0
        || file.indexOf('\n') >= 0) {
      throw Requests.notFound();
    }
    var parsedVariant = parseVariant(variant);
    var recipe = RecipeVersion.value();
    var cacheControl = recipe.equals(v) ? IMMUTABLE : Release.REVALIDATED;
    if (request.getHeader(HttpHeaders.IF_NONE_MATCH) != null) {
      var knownDigest = images.sourceDigest(id, file);
      if (knownDigest.isPresent()) {
        var etag = etag(recipe, knownDigest.get());
        if (Release.notModified(request, response, cacheControl, etag)) {
          return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
      }
    }
    try {
      var rendition = images.rendition(id, file, parsedVariant);
      if (rendition.isEmpty()) {
        return notCached(HttpStatus.NOT_FOUND, response);
      }
      var rendered = rendition.get();
      var etag = etag(rendered.recipe(), rendered.sourceSha256());
      return ResponseEntity.ok()
          .contentType(IMAGE_WEBP)
          .cacheControl(cacheControl)
          .eTag(etag)
          .body(rendered.webp());
    } catch (ImageGoneException _) {
      return notCached(HttpStatus.GONE, response);
    } catch (ImageUnavailableException _) {
      return notCached(HttpStatus.SERVICE_UNAVAILABLE, response);
    }
  }

  private static ResponseEntity<byte[]> notCached(HttpStatus status, HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE.getHeaderValue());
    return ResponseEntity.status(status).cacheControl(NO_STORE).build();
  }

  private static Images.Variant parseVariant(String variant) {
    return switch (variant.toLowerCase(Locale.ROOT)) {
      case "preview" -> Images.Variant.PREVIEW;
      case "original" -> Images.Variant.ORIGINAL;
      default -> throw Requests.notFound();
    };
  }

  private static String etag(String recipe, String sourceSha256) {
    return "W/\"" + recipe + "-" + sourceSha256 + "\"";
  }
}
