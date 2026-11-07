// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchRuntimeException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Tests which failures of the module {@link CellarFailures} counts as Cellar not answering. The
 * failures are as the callers see them.
 */
class CellarFailuresTest {
  static final URI ITEM =
      URI.create(
          "http://publications.europa.eu/resource/cellar/8f145680-d086-4e3d-b6a0-a8b83cdc04b9.0006.01/DOC_1");
  static final MediaType FORMEX = MediaType.parseMediaType("application/xml;type=fmx4");
  static final MediaType RESULTS = MediaType.parseMediaType("application/sparql-results+json");

  @Test
  void isCellarNotAnsweringWhenItIsBusyOrDown() {
    var mock = Recorded.Mock.of();
    mock.server()
        .expect(requestTo(Recorded.SPARQL))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    mock.server().expect(requestTo(ITEM)).andRespond(withServiceUnavailable());

    assertThat(CellarFailures.unavailable(catchRuntimeException(() -> mock.sparql().select("{}"))))
        .isTrue();
    assertThat(CellarFailures.unavailable(catchRuntimeException(() -> read(mock)))).isTrue();
  }

  @Test
  void isCellarNotAnsweringWhenItIsTooSlowOrItsAnswerBreaksOff() {
    var mock = Recorded.Mock.of();
    mock.server()
        .expect(requestTo(Recorded.SPARQL))
        .andRespond(withException(new SocketTimeoutException("Read timed out")));
    mock.server()
        .expect(requestTo(Recorded.SPARQL))
        .andRespond(withSuccess(new InputStreamResource(new Broken()), RESULTS));
    mock.server()
        .expect(requestTo(ITEM))
        .andRespond(withSuccess(new InputStreamResource(new Broken()), FORMEX));

    assertThat(CellarFailures.unavailable(catchRuntimeException(() -> mock.sparql().select("{}"))))
        .isTrue();
    assertThat(CellarFailures.unavailable(catchRuntimeException(() -> mock.sparql().select("{}"))))
        .isTrue();
    assertThat(CellarFailures.unavailable(catchRuntimeException(() -> read(mock)))).isTrue();
  }

  @Test
  void isntCellarNotAnsweringWhenItRefusesOrAnswersWhatCantBeUsed() {
    var mock = Recorded.Mock.of(Recorded.properties(4));
    mock.server().expect(requestTo(ITEM)).andRespond(withResourceNotFound());
    mock.server()
        .expect(requestTo(Recorded.SPARQL))
        .andRespond(withSuccess(Recorded.bytes("sparql-32009D0959.json"), RESULTS));

    assertThat(CellarFailures.unavailable(catchRuntimeException(() -> read(mock)))).isFalse();
    // The answer has as many rows as the limit of the endpoint, so it can be cut off.
    assertThat(CellarFailures.unavailable(catchRuntimeException(() -> mock.sparql().select("{}"))))
        .isFalse();
  }

  private static byte[] read(Recorded.Mock mock) {
    return mock.contentStreams()
        .read(
            ITEM,
            in -> {
              try {
                return in.readAllBytes();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  /** A body whose connection is lost after its first bytes. */
  private static final class Broken extends InputStream {
    private int sent;

    @Override
    public int read() throws IOException {
      if (sent++ < 10) {
        return '{';
      }
      throw new IOException("connection reset");
    }
  }
}
