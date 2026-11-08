// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.cellar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpServerErrorException;

class SparqlTest {
  static final MediaType RESULTS = MediaType.parseMediaType("application/sparql-results+json");
  static final byte[] RECORDED = Recorded.bytes("sparql-32009D0959.json");

  @Test
  void fillsAValueInAsTheInsideOfAStringLiteral() {
    assertThat(
            Sparql.fill(
                "SELECT * WHERE { ?w ?p \"{{celex}}\" }", Map.of("celex", "32015R1998\"} #\\")))
        .isEqualTo("SELECT * WHERE { ?w ?p \"32015R1998\\\"} #\\\\\" }");
  }

  @Test
  void postsTheQueryAsAFormAndReadsTheBindings() {
    var mock = Recorded.Mock.of();
    mock.server()
        .expect(requestTo(Recorded.SPARQL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(content().string("query=SELECT+*+WHERE+%7B%7D"))
        .andExpect(header("Accept", RESULTS.toString()))
        .andRespond(withSuccess(RECORDED, RESULTS));

    var result = mock.sparql().select("SELECT * WHERE {}");

    assertThat(result.variables())
        .containsExactly("manifestation", "item", "mime", "file", "sha1", "modified");
    assertThat(result.rows()).hasSize(4);
    assertThat(result.rows().get(3).get("file")).isEqualTo("L_2009330EN.01008102.tif");
    assertThat(result.rows().get(3).find("sha1"))
        .contains("0e2f1dcaa096a07478cfb6a82a4b74d4e832f621");
    mock.server().verify();
  }

  @Test
  void refusesAResultAsLongAsTheEndpointsLimit() {
    var mock = Recorded.Mock.of(Recorded.properties(4));
    mock.server().expect(requestTo(Recorded.SPARQL)).andRespond(withSuccess(RECORDED, RESULTS));

    assertThatExceptionOfType(CellarException.class)
        .isThrownBy(() -> mock.sparql().select("SELECT * WHERE {}"))
        .withMessageContaining("cut off");
  }

  @Test
  void failsAtOnceWhenCellarIsUnavailable() {
    var mock = Recorded.Mock.of();
    mock.server().expect(once(), requestTo(Recorded.SPARQL)).andRespond(withServiceUnavailable());

    assertThatExceptionOfType(HttpServerErrorException.ServiceUnavailable.class)
        .isThrownBy(() -> mock.sparql().select("SELECT * WHERE {}"));
    mock.server().verify();
  }

  @Test
  void asksCellarOnceForTheSameQueryAskedTwiceAtOnce() {
    var mock = Recorded.Mock.of();
    var answer = new CountDownLatch(1);
    mock.server()
        .expect(once(), requestTo(Recorded.SPARQL))
        .andRespond(
            request -> {
              try {
                answer.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return withSuccess(RECORDED, RESULTS).createResponse(request);
            });
    var sparql = mock.sparql();

    // Cellar holds its answer to the first query until both queries are asked. The mock expects
    // one request, so a second request would fail.
    var first = sparql.answer("SELECT * WHERE {}");
    var second = sparql.answer("SELECT * WHERE {}");
    answer.countDown();

    assertThat(second).succeedsWithin(Duration.ofSeconds(5)).isEqualTo(first.join());
    mock.server().verify();
  }
}
