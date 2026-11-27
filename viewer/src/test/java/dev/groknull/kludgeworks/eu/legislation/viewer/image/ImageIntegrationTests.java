// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images.Source;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images.Variant;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.application.SourceFetcher;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * End-to-end test of Postgres {@code image_source} + {@code image_rendition}, LRU eviction, digest
 * pinning, and {@code TextReplaced} cleanup.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"viewer.image.cache.max-bytes=4096", "viewer.image.cache.eviction-batch=1"})
@Import(TestcontainersConfiguration.class)
class ImageIntegrationTests {
  private static final String SAMPLE = "06.02.1/02015R1998-20260322.xml";
  private static final String NEXT = "06.02.1/02015R1998-20261001.xml";

  @Autowired Images images;
  @Autowired Content content;
  @Autowired JdbcClient jdbc;
  @MockitoBean SourceFetcher fetcher;

  @Test
  void cachesEvictsPinsDigestsAndCleansUpOnTextReplaced() throws IOException {
    var celex = "02015R1998-IMAGE-TEST";
    var firstText = content.store(celex, Language.ENG, "test", LegalTexts.sample(SAMPLE));
    var uriOne = URI.create("http://cellar/doc/1");
    var uriTwo = URI.create("http://cellar/doc/2");
    images.register(
        firstText,
        URI.create("http://cellar/manifest/1"),
        List.of(
            new Source("fig1.tif", uriOne, "image/tiff", null),
            new Source("fig2.tif", uriTwo, "image/tiff", null)));

    var noisyOne = noisyTiff(1);
    var noisyTwo = noisyTiff(2);
    when(fetcher.fetch(eq(uriOne), anyLong())).thenReturn(noisyOne);
    when(fetcher.fetch(eq(uriTwo), anyLong())).thenReturn(noisyTwo);

    var firstRendered = images.rendition(firstText, "fig1.tif", Variant.PREVIEW).orElseThrow();
    assertThat(firstRendered.webp().length).isGreaterThan(4096);
    assertThat(images.sourceDigest(firstText, "fig1.tif")).contains(firstRendered.sourceSha256());

    // Storing a second oversized rendition evicts the first while keeping source_sha256 pinned.
    var secondRendered = images.rendition(firstText, "fig2.tif", Variant.PREVIEW).orElseThrow();
    assertThat(secondRendered.webp().length).isGreaterThan(4096);
    var cachedRows = jdbc.sql("SELECT count(*) FROM image_rendition").query(Integer.class).single();
    assertThat(cachedRows).isEqualTo(1);
    assertThat(images.sourceDigest(firstText, "fig1.tif")).contains(firstRendered.sourceSha256());

    // After eviction, altered upstream bytes under the pinned digest fail permanently (410 Gone)
    // and remember the failure so repeat requests do not hit Cellar again.
    when(fetcher.fetch(eq(uriOne), anyLong())).thenReturn(noisyTiff(3));
    assertThatThrownBy(() -> images.rendition(firstText, "fig1.tif", Variant.PREVIEW))
        .isInstanceOf(ImageGoneException.class);
    var goneReason =
        jdbc.sql("SELECT gone_reason FROM image_source WHERE file_name = 'fig1.tif'")
            .query(String.class)
            .single();
    assertThat(goneReason).contains("Source bytes changed");
    assertThatThrownBy(() -> images.rendition(firstText, "fig1.tif", Variant.PREVIEW))
        .isInstanceOf(ImageGoneException.class);
    verify(fetcher, times(2)).fetch(eq(uriOne), anyLong());

    // Replacing the text triggers TextReplaced and removes image_source rows for firstText.
    content.store(celex, Language.ENG, "test", LegalTexts.sample(NEXT));
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(images.sourceUris(firstText)).isEmpty());
  }

  private static byte[] noisyTiff(int seed) throws IOException {
    var img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < 128; y++) {
      for (int x = 0; x < 128; x++) {
        int rgb = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791);
        img.setRGB(x, y, rgb);
      }
    }
    var out = new ByteArrayOutputStream();
    assertThat(ImageIO.write(img, "TIFF", out)).isTrue();
    return out.toByteArray();
  }
}
