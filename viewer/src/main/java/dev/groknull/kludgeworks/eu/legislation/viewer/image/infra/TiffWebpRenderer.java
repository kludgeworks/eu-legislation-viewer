// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.image.ImageGoneException;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.ImageUnavailableException;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.application.ImageRenderer;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.model.ImageLimits;
import dev.matrixlab.webp4j.WebPCodec;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Decodes Formex TIFF images, checks pixel limits before allocation, converts to sRGB, scales
 * previews to screen DPI, and encodes lossless WebP.
 */
@Component
class TiffWebpRenderer implements ImageRenderer {
  private static final double MM_PER_INCH = 25.4;
  private static final double MIN_DPI = 10.0;
  private static final double MAX_DPI = 10_000.0;
  private static final String STANDARD_METADATA = "javax_imageio_1.0";

  @Override
  public Rendered render(byte[] tiffBytes, Images.Variant variant) {
    try {
      var decoded = decode(tiffBytes);
      var srgb = toSrgb(decoded.image());
      var scaled =
          variant == Images.Variant.PREVIEW
              ? scalePreview(
                  srgb,
                  decoded.dpiX(),
                  decoded.dpiY(),
                  ImageLimits.PREVIEW_DPI,
                  ImageLimits.PREVIEW_MAX_EDGE)
              : srgb;
      var webp = WebPCodec.encodeLosslessImage(scaled);
      return new Rendered(webp, scaled.getWidth(), scaled.getHeight());
    } catch (ImageGoneException | ImageUnavailableException e) {
      throw e;
    } catch (IIOException | IllegalArgumentException | IndexOutOfBoundsException e) {
      throw new ImageGoneException("Invalid or unreadable TIFF image: " + e.getMessage(), e);
    } catch (IOException | RuntimeException e) {
      throw new ImageUnavailableException("Failed to encode WebP image: " + e.getMessage(), e);
    }
  }

  private static DecodedImage decode(byte[] tiffBytes) throws IOException {
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(tiffBytes))) {
      if (input == null) {
        throw new ImageGoneException("No image input stream could be opened for TIFF bytes");
      }
      var readers = ImageIO.getImageReadersByFormatName("TIFF");
      if (!readers.hasNext()) {
        throw new ImageUnavailableException("No TIFF ImageReader registered in JDK ImageIO");
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, false);
        var width = reader.getWidth(0);
        var height = reader.getHeight(0);
        if (width <= 0 || height <= 0) {
          throw new ImageGoneException("Invalid TIFF dimensions: " + width + "x" + height);
        }
        long pixels = Math.multiplyExact((long) width, (long) height);
        if (pixels > ImageLimits.MAX_PIXELS) {
          throw new ImageGoneException(
              "TIFF pixel count " + pixels + " exceeds limit " + ImageLimits.MAX_PIXELS);
        }
        var resolution = readResolution(reader);
        var image = reader.read(0);
        if (image == null) {
          throw new ImageGoneException("TIFF ImageReader returned null image");
        }
        return new DecodedImage(image, resolution.dpiX(), resolution.dpiY());
      } finally {
        reader.dispose();
      }
    }
  }

  private static Resolution readResolution(ImageReader reader) {
    try {
      var metadata = reader.getImageMetadata(0);
      if (metadata == null || !metadata.isStandardMetadataFormatSupported()) {
        return new Resolution(null, null);
      }
      var tree = (IIOMetadataNode) metadata.getAsTree(STANDARD_METADATA);
      var dpiX = pixelSizeToDpi(attributeValue(tree, "HorizontalPixelSize"));
      var dpiY = pixelSizeToDpi(attributeValue(tree, "VerticalPixelSize"));
      if (dpiX == null && dpiY != null) {
        dpiX = dpiY;
      } else if (dpiY == null && dpiX != null) {
        dpiY = dpiX;
      }
      return new Resolution(dpiX, dpiY);
    } catch (IOException | RuntimeException e) {
      return new Resolution(null, null);
    }
  }

  private static @Nullable String attributeValue(IIOMetadataNode root, String elementName) {
    var nodes = root.getElementsByTagName(elementName);
    if (nodes.getLength() == 0) {
      return null;
    }
    var value = ((IIOMetadataNode) nodes.item(0)).getAttribute("value");
    return value.isEmpty() ? null : value;
  }

  private static @Nullable Double pixelSizeToDpi(@Nullable String mmValue) {
    if (mmValue == null) {
      return null;
    }
    try {
      double mm = Double.parseDouble(mmValue);
      if (!Double.isFinite(mm) || mm <= 0) {
        return null;
      }
      double dpi = MM_PER_INCH / mm;
      return dpi >= MIN_DPI && dpi <= MAX_DPI ? dpi : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static BufferedImage toSrgb(BufferedImage image) {
    var hasAlpha = image.getColorModel().hasAlpha();
    var targetType = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    if (image.getType() == targetType && image.getColorModel().getColorSpace().isCS_sRGB()) {
      return image;
    }
    var out = new BufferedImage(image.getWidth(), image.getHeight(), targetType);
    var graphics = out.createGraphics();
    try {
      graphics.drawImage(image, 0, 0, null);
    } finally {
      graphics.dispose();
    }
    return out;
  }

  private static BufferedImage scalePreview(
      BufferedImage image,
      @Nullable Double dpiX,
      @Nullable Double dpiY,
      double targetDpi,
      int maxEdge) {
    var width = image.getWidth();
    var height = image.getHeight();
    double scaleX = dpiX != null && dpiX > targetDpi ? targetDpi / dpiX : 1.0;
    double scaleY = dpiY != null && dpiY > targetDpi ? targetDpi / dpiY : 1.0;
    int targetWidth = Math.max(1, (int) Math.round(width * scaleX));
    int targetHeight = Math.max(1, (int) Math.round(height * scaleY));
    int longest = Math.max(targetWidth, targetHeight);
    if (longest > maxEdge) {
      double fit = (double) maxEdge / longest;
      targetWidth = Math.max(1, (int) Math.round(targetWidth * fit));
      targetHeight = Math.max(1, (int) Math.round(targetHeight * fit));
    }
    if (targetWidth == width && targetHeight == height) {
      return image;
    }
    return downscaleProgressive(image, targetWidth, targetHeight);
  }

  private static BufferedImage downscaleProgressive(
      BufferedImage image, int targetWidth, int targetHeight) {
    var current = image;
    int width = current.getWidth();
    int height = current.getHeight();
    while (width / 2 >= targetWidth && height / 2 >= targetHeight) {
      width = Math.max(targetWidth, width / 2);
      height = Math.max(targetHeight, height / 2);
      current = drawScaled(current, width, height, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }
    if (width == targetWidth && height == targetHeight) {
      return current;
    }
    return drawScaled(
        current, targetWidth, targetHeight, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
  }

  private static BufferedImage drawScaled(
      BufferedImage source, int targetWidth, int targetHeight, Object interpolation) {
    var out = new BufferedImage(targetWidth, targetHeight, source.getType());
    var graphics = out.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
      graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
    } finally {
      graphics.dispose();
    }
    return out;
  }

  private record DecodedImage(BufferedImage image, @Nullable Double dpiX, @Nullable Double dpiY) {}

  private record Resolution(@Nullable Double dpiX, @Nullable Double dpiY) {}
}
