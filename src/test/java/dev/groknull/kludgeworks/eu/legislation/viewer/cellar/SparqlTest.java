// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;

class SparqlTest {
  static final MediaType RESULTS = MediaType.parseMediaType("application/sparql-results+json");
  static final byte[] RECORDED = Recorded.bytes("sparql-32009D0959.json");

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

    var result = new Sparql(mock.http()).select("SELECT * WHERE {}");

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
        .isThrownBy(() -> new Sparql(mock.http()).select("SELECT * WHERE {}"))
        .withMessageContaining("cut off");
  }

  @Test
  void retriesWhenCellarIsUnavailable() {
    var mock = Recorded.Mock.of();
    mock.server().expect(once(), requestTo(Recorded.SPARQL)).andRespond(withServiceUnavailable());
    mock.server()
        .expect(once(), requestTo(Recorded.SPARQL))
        .andRespond(withSuccess(RECORDED, RESULTS));

    assertThat(new Sparql(mock.http()).select("SELECT * WHERE {}").rows()).hasSize(4);
    mock.server().verify();
  }

  @Test
  void doesNotRetryABadRequest() {
    var mock = Recorded.Mock.of();
    mock.server().expect(once(), requestTo(Recorded.SPARQL)).andRespond(withBadRequest());

    assertThatExceptionOfType(HttpClientErrorException.BadRequest.class)
        .isThrownBy(() -> new Sparql(mock.http()).select("SELECT * WHERE {"));
    mock.server().verify();
  }

  @Test
  void asksCellarOnceForTheSameQueryAskedTwiceAtOnce() throws Exception {
    var mock = Recorded.Mock.of();
    var asked = new CountDownLatch(1);
    var answer = new CountDownLatch(1);
    mock.server()
        .expect(once(), requestTo(Recorded.SPARQL))
        .andRespond(
            request -> {
              asked.countDown();
              try {
                answer.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return withSuccess(RECORDED, RESULTS).createResponse(request);
            });
    var sparql = new Sparql(mock.http());

    var first = CompletableFuture.supplyAsync(() -> sparql.select("SELECT * WHERE {}"));
    asked.await(5, TimeUnit.SECONDS);
    var second = CompletableFuture.supplyAsync(() -> sparql.select("SELECT * WHERE {}"));
    Thread.sleep(100);
    answer.countDown();

    assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(first.get(5, TimeUnit.SECONDS));
    mock.server().verify();
  }
}
