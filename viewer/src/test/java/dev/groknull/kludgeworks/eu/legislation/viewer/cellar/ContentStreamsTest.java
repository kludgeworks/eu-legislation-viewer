// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;

class ContentStreamsTest {
  static final URI ITEM =
      URI.create(
          "http://publications.europa.eu/resource/cellar/8f145680-d086-4e3d-b6a0-a8b83cdc04b9.0006.01/DOC_1");
  static final byte[] RECORDED = Recorded.bytes("L_2009330EN.01008001.doc.xml");
  static final String ETAG = "\"Con-20190719054558000\"";

  @TempDir Path dir;

  @Test
  void writesTheBytesAsSentWithTheirEtagAndSha1() throws IOException {
    var mock = Recorded.Mock.of();
    mock.server()
        .expect(requestTo(ITEM))
        .andRespond(
            withSuccess(
                    RECORDED, MediaType.parseMediaType("application/xml;type=fmx4;charset=UTF-8"))
                .header("ETag", ETAG)
                .header("Last-Modified", "Fri, 19 Jul 2019 03:45:58 GMT"));
    var target = dir.resolve("L_2009330EN.01008001.doc.xml");

    var download = mock.contentStreams().download(ITEM, null, target);

    assertThat(Files.readAllBytes(target)).isEqualTo(RECORDED);
    assertThat(download)
        .isEqualTo(
            new Download.Downloaded(
                ETAG,
                Instant.parse("2019-07-19T03:45:58Z"),
                "application/xml;type=fmx4;charset=UTF-8",
                RECORDED.length,
                // as the RDF records it (tdm:stream_fixity)
                "c67389658f586157146442136839b0d345b7643c"));
  }

  @Test
  void asksWithTheEtagAndKeepsTheCopyWhileItIsCurrent() throws IOException {
    var mock = Recorded.Mock.of();
    mock.server()
        .expect(requestTo(ITEM))
        .andExpect(header("If-None-Match", ETAG))
        .andRespond(withStatus(HttpStatus.NOT_MODIFIED));
    var target = Files.write(dir.resolve("copy.xml"), RECORDED);

    var download = mock.contentStreams().download(ITEM, ETAG, target);

    assertThat(download).isInstanceOf(Download.NotModified.class);
    assertThat(Files.readAllBytes(target)).isEqualTo(RECORDED);
  }

  @Test
  void leavesNoFileBehindWhenItFails() throws IOException {
    var mock = Recorded.Mock.of();
    mock.server().expect(requestTo(ITEM)).andRespond(withResourceNotFound());
    var target = dir.resolve("missing.xml");

    assertThatExceptionOfType(HttpClientErrorException.NotFound.class)
        .isThrownBy(() -> mock.contentStreams().download(ITEM, null, target));
    try (var files = Files.list(dir)) {
      assertThat(files).isEmpty();
    }
  }
}
