// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.groknull.kludgeworks.eu.legislation.viewer.image.ImageGoneException;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images.Variant;
import dev.matrixlab.webp4j.WebPCodec;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataNode;
import org.junit.jupiter.api.Test;

/** Tests TIFF decoding, sRGB conversion, pixel-limit enforcement, and lossless WebP encoding. */
class TiffWebpRendererTest {
  private final TiffWebpRenderer renderer = new TiffWebpRenderer();

  @Test
  void rendersGrayscaleAndRgbTiffsAsLosslessWebp() throws IOException {
    var rgb = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
    var graphics = rgb.createGraphics();
    try {
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, 120, 80);
      graphics.setColor(new Color(12, 34, 56));
      graphics.fillRect(10, 10, 40, 20);
    } finally {
      graphics.dispose();
    }

    var rendered = renderer.render(tiffBytes(rgb), Variant.PREVIEW);

    assertThat(rendered.width()).isEqualTo(120);
    assertThat(rendered.height()).isEqualTo(80);
    var decoded = WebPCodec.decodeImage(rendered.webp());
    assertThat(decoded.getWidth()).isEqualTo(120);
    assertThat(decoded.getHeight()).isEqualTo(80);

    var gray = new BufferedImage(60, 40, BufferedImage.TYPE_BYTE_GRAY);
    var grayRendered = renderer.render(tiffBytes(gray), Variant.ORIGINAL);
    assertThat(grayRendered.width()).isEqualTo(60);
    assertThat(grayRendered.height()).isEqualTo(40);
  }

  @Test
  void scalesPreviewDownToLongestEdgeWhileKeepingOriginalAtNativeResolution() throws IOException {
    var large = new BufferedImage(2400, 1200, BufferedImage.TYPE_INT_RGB);
    var tiff = tiffBytes(large);

    var preview = renderer.render(tiff, Variant.PREVIEW);
    var original = renderer.render(tiff, Variant.ORIGINAL);

    assertThat(preview.width()).isEqualTo(1600);
    assertThat(preview.height()).isEqualTo(800);
    assertThat(original.width()).isEqualTo(2400);
    assertThat(original.height()).isEqualTo(1200);
  }

  @Test
  void scalesHighDpiPreviewToScreenDpiWhileKeepingOriginalAtNativeResolution() throws IOException {
    var formex = new BufferedImage(557, 568, BufferedImage.TYPE_INT_RGB);
    var tiff = tiffBytesWithDpi(formex, 400, 400);

    var preview = renderer.render(tiff, Variant.PREVIEW);
    var original = renderer.render(tiff, Variant.ORIGINAL);

    assertThat(preview.width()).isEqualTo(167);
    assertThat(preview.height()).isEqualTo(170);
    assertThat(original.width()).isEqualTo(557);
    assertThat(original.height()).isEqualTo(568);
  }

  @Test
  void scalesBinaryAndWideHighDpiPreviewsWithoutUpscalingLowDpiTiffs() throws IOException {
    var binary = new BufferedImage(868, 150, BufferedImage.TYPE_BYTE_BINARY);
    var binaryPreview = renderer.render(tiffBytesWithDpi(binary, 400, 400), Variant.PREVIEW);
    assertThat(binaryPreview.width()).isEqualTo(260);
    assertThat(binaryPreview.height()).isEqualTo(45);

    var wide = new BufferedImage(6000, 1500, BufferedImage.TYPE_BYTE_BINARY);
    var widePreview = renderer.render(tiffBytesWithDpi(wide, 400, 400), Variant.PREVIEW);
    assertThat(widePreview.width()).isEqualTo(1600);
    assertThat(widePreview.height()).isEqualTo(400);

    var lowDpi = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
    var lowDpiPreview = renderer.render(tiffBytesWithDpi(lowDpi, 96, 96), Variant.PREVIEW);
    assertThat(lowDpiPreview.width()).isEqualTo(120);
    assertThat(lowDpiPreview.height()).isEqualTo(80);

    var fax = new BufferedImage(400, 200, BufferedImage.TYPE_BYTE_BINARY);
    var faxPreview = renderer.render(tiffBytesWithDpi(fax, 400, 200), Variant.PREVIEW);
    assertThat(faxPreview.width()).isEqualTo(120);
    assertThat(faxPreview.height()).isEqualTo(120);
  }

  @Test
  void rejectsTiffsExceedingPixelLimitFromHeaderMetadata() {
    var oversizedHeader = minimalTiffHeader(5000, 5000);

    assertThatThrownBy(() -> renderer.render(oversizedHeader, Variant.PREVIEW))
        .isInstanceOf(ImageGoneException.class)
        .hasMessageContaining("exceeds limit");
  }

  private static byte[] tiffBytes(BufferedImage image) throws IOException {
    var out = new ByteArrayOutputStream();
    assertThat(ImageIO.write(image, "TIFF", out)).isTrue();
    return out.toByteArray();
  }

  private static byte[] tiffBytesWithDpi(BufferedImage image, double dpiX, double dpiY)
      throws IOException {
    var writer = ImageIO.getImageWritersByFormatName("TIFF").next();
    var out = new ByteArrayOutputStream();
    try (var imageOut = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(imageOut);
      var param = writer.getDefaultWriteParam();
      var metadata =
          writer.getDefaultImageMetadata(
              ImageTypeSpecifier.createFromBufferedImageType(image.getType()), param);
      var root = new IIOMetadataNode("javax_imageio_1.0");
      var dimension = new IIOMetadataNode("Dimension");
      var hps = new IIOMetadataNode("HorizontalPixelSize");
      hps.setAttribute("value", Double.toString(25.4 / dpiX));
      var vps = new IIOMetadataNode("VerticalPixelSize");
      vps.setAttribute("value", Double.toString(25.4 / dpiY));
      dimension.appendChild(hps);
      dimension.appendChild(vps);
      root.appendChild(dimension);
      metadata.mergeTree("javax_imageio_1.0", root);
      writer.write(new IIOImage(image, null, metadata));
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }

  private static byte[] minimalTiffHeader(int width, int height) {
    // Minimal little-endian TIFF header + 2-entry IFD (ImageWidth=256, ImageLength=257).
    var buf = ByteBuffer.allocate(8 + 2 + 2 * 12 + 4).order(ByteOrder.LITTLE_ENDIAN);
    buf.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
    buf.putShort((short) 2);
    buf.putShort((short) 256).putShort((short) 4).putInt(1).putInt(width);
    buf.putShort((short) 257).putShort((short) 4).putInt(1).putInt(height);
    buf.putInt(0);
    return buf.array();
  }
}
