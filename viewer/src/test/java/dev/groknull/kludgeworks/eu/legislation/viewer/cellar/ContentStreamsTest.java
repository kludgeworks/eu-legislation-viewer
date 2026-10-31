// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class ContentStreamsTest {
  static final URI ITEM =
      URI.create(
          "http://publications.europa.eu/resource/cellar/8f145680-d086-4e3d-b6a0-a8b83cdc04b9.0006.01/DOC_1");
  static final byte[] RECORDED = Recorded.bytes("L_2009330EN.01008001.doc.xml");
  static final MediaType FORMEX = MediaType.parseMediaType("application/xml;type=fmx4");

  @Test
  void givesTheReaderTheBytesAsSent() {
    var mock = Recorded.Mock.of();
    mock.server().expect(requestTo(ITEM)).andRespond(withSuccess(RECORDED, FORMEX));

    assertThat(mock.contentStreams().read(ITEM, ContentStreamsTest::bytes)).isEqualTo(RECORDED);
  }

  @Test
  void givesTheReaderAFileCellarKeepsGzippedUnzipped() throws IOException {
    var gzipped = new ByteArrayOutputStream();
    try (var out = new GZIPOutputStream(gzipped)) {
      out.write(RECORDED);
    }
    var mock = Recorded.Mock.of();
    mock.server().expect(requestTo(ITEM)).andRespond(withSuccess(gzipped.toByteArray(), FORMEX));

    assertThat(mock.contentStreams().read(ITEM, ContentStreamsTest::bytes)).isEqualTo(RECORDED);
  }

  @Test
  void failsWithCellarsStatus() {
    var mock = Recorded.Mock.of();
    mock.server().expect(requestTo(ITEM)).andRespond(withResourceNotFound());
    mock.server().expect(requestTo(ITEM)).andRespond(withServiceUnavailable());

    assertThatExceptionOfType(HttpClientErrorException.NotFound.class)
        .isThrownBy(() -> mock.contentStreams().read(ITEM, ContentStreamsTest::bytes));
    assertThatExceptionOfType(HttpServerErrorException.ServiceUnavailable.class)
        .isThrownBy(() -> mock.contentStreams().read(ITEM, ContentStreamsTest::bytes));
  }

  @Test
  void failsAsCellarFailingWhenTheBytesStopComingWhateverTheReaderMadeOfIt() {
    var mock = Recorded.Mock.of();
    mock.server()
        .expect(requestTo(ITEM))
        .andRespond(withSuccess(new InputStreamResource(new Broken()), FORMEX));

    assertThatExceptionOfType(ResourceAccessException.class)
        .isThrownBy(
            () ->
                mock.contentStreams()
                    .read(
                        ITEM,
                        in -> {
                          try {
                            return in.readAllBytes();
                          } catch (IOException e) {
                            return new byte[0];
                          }
                        }))
        .withMessageContaining("connection reset");
  }

  private static byte[] bytes(InputStream in) {
    try {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A body whose connection is lost after its first bytes. */
  private static final class Broken extends InputStream {
    private int sent;

    @Override
    public int read() throws IOException {
      if (sent++ < 10) {
        return '<';
      }
      throw new IOException("connection reset");
    }
  }
}
