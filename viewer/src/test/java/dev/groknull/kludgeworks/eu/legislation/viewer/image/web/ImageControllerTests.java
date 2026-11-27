// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.ImageGoneException;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.ImageUnavailableException;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images.Rendition;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images.Variant;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.model.RecipeVersion;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Tests HTTP delivery, cache headers, variant validation, and error codes of {@link
 * ImageController}.
 */
@WebMvcTest(ImageController.class)
class ImageControllerTests {
  private static final TextId TEXT = new TextId(7);
  private static final String FILE = "L_2009330EN.01008102.tif";
  private static final String PATH = "/images/7/" + FILE;
  private static final String DIGEST =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  @Autowired MockMvcTester mvc;
  @MockitoBean Images images;

  @Test
  void servesVersionedImmutableAndUnversionedRevalidatedWebp() {
    var previewBytes = new byte[] {'R', 'I', 'F', 'F', 1};
    var originalBytes = new byte[] {'R', 'I', 'F', 'F', 2};
    var recipe = RecipeVersion.value();
    when(images.sourceDigest(TEXT, FILE)).thenReturn(Optional.empty());
    when(images.rendition(TEXT, FILE, Variant.PREVIEW))
        .thenReturn(Optional.of(new Rendition(previewBytes, DIGEST, recipe, 800, 600)));
    when(images.rendition(TEXT, FILE, Variant.ORIGINAL))
        .thenReturn(Optional.of(new Rendition(originalBytes, DIGEST, recipe, 1600, 1200)));

    var versioned = mvc.get().uri(PATH + "?v=" + recipe).exchange();
    assertThat(versioned)
        .hasStatusOk()
        .hasContentType("image/webp")
        .headers()
        .hasValue(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public")
        .hasValue(HttpHeaders.ETAG, "W/\"" + recipe + "-" + DIGEST + "\"");
    assertThat(versioned.getResponse().getContentAsByteArray()).isEqualTo(previewBytes);

    var unversioned = mvc.get().uri(PATH).exchange();
    assertThat(unversioned)
        .hasStatusOk()
        .hasContentType("image/webp")
        .headers()
        .hasValue(HttpHeaders.CACHE_CONTROL, "no-cache")
        .hasValue(HttpHeaders.ETAG, "W/\"" + recipe + "-" + DIGEST + "\"");

    var original = mvc.get().uri(PATH + "?variant=original&v=" + recipe).exchange();
    assertThat(original)
        .hasStatusOk()
        .hasContentType("image/webp")
        .headers()
        .hasValue(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public");
    assertThat(original.getResponse().getContentAsByteArray()).isEqualTo(originalBytes);
  }

  @Test
  void returnsNotModifiedFromKnownDigestWithoutRendering() {
    var etag = "W/\"" + RecipeVersion.value() + "-" + DIGEST + "\"";
    when(images.sourceDigest(TEXT, FILE)).thenReturn(Optional.of(DIGEST));

    var result = mvc.get().uri(PATH).header(HttpHeaders.IF_NONE_MATCH, etag).exchange();

    assertThat(result)
        .hasStatus(HttpStatus.NOT_MODIFIED)
        .headers()
        .hasValue(HttpHeaders.CACHE_CONTROL, "no-cache")
        .hasValue(HttpHeaders.ETAG, etag);
    verify(images, never()).rendition(any(), any(), any());
  }

  @Test
  void returns404OnMissingFileInvalidVariantOrPathTraversal() {
    when(images.sourceDigest(TEXT, FILE)).thenReturn(Optional.empty());
    when(images.rendition(TEXT, FILE, Variant.PREVIEW)).thenReturn(Optional.empty());

    assertThat(mvc.get().uri(PATH)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri(PATH + "?variant=thumb")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/images/7/..")).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void mapsPermanentAndTransientFailuresTo410And503WithoutCaching() {
    var recipe = RecipeVersion.value();
    when(images.sourceDigest(TEXT, FILE)).thenReturn(Optional.of(DIGEST));
    when(images.rendition(TEXT, FILE, Variant.PREVIEW))
        .thenThrow(new ImageGoneException("gone"))
        .thenThrow(new ImageUnavailableException("down"))
        .thenReturn(Optional.empty());

    assertThat(
            mvc.get()
                .uri(PATH + "?v=" + recipe)
                .header(HttpHeaders.IF_NONE_MATCH, "W/\"stale\"")
                .exchange())
        .hasStatus(HttpStatus.GONE)
        .headers()
        .hasValue(HttpHeaders.CACHE_CONTROL, "no-store");
    assertThat(mvc.get().uri(PATH + "?v=" + recipe).exchange())
        .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
        .headers()
        .hasValue(HttpHeaders.CACHE_CONTROL, "no-store");
    assertThat(mvc.get().uri(PATH + "?v=" + recipe).exchange())
        .hasStatus(HttpStatus.NOT_FOUND)
        .headers()
        .hasValue(HttpHeaders.CACHE_CONTROL, "no-store");
  }
}
