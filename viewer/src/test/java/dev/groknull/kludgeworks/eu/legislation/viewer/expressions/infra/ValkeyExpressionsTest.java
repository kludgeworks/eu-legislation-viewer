// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra.FormexFixtures.CONSOLIDATION;
import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.ExpressionIdentifier;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import net.sf.saxon.s9api.Processor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;

/**
 * Expressions kept in a real Valkey, in a container (Testcontainers): converted once, then read
 * from the store, also when several requests ask at once; documents without Formex remembered; keys
 * that carry the converter's version.
 */
class ValkeyExpressionsTest {
  static final Processor SAXON = new Processor(false);
  static final GenericContainer<?> VALKEY = TestcontainersConfiguration.valkey();
  static final LettuceConnectionFactory CONNECTIONS = connections();
  static final ExpressionIdentifier ENGLISH = new ExpressionIdentifier(CONSOLIDATION, Language.ENG);

  final FormexFixtures source = new FormexFixtures();
  final FormexConversion conversion = new FormexConversion(source, new FormexConverter(SAXON));
  final RedisTemplate<String, byte[]> valkey = store();
  final ValkeyExpressions expressions =
      new ValkeyExpressions(
          valkey,
          conversion,
          source,
          new LegalTextReader(SAXON),
          SAXON,
          new StoreProperties(Duration.ofMinutes(1), Duration.ofMillis(50), Duration.ofDays(1)));

  @BeforeEach
  void emptyTheStore() {
    try (var connection = CONNECTIONS.getConnection()) {
      connection.serverCommands().flushAll();
    }
  }

  @AfterAll
  static void stopValkey() {
    CONNECTIONS.destroy();
    VALKEY.stop();
  }

  @Test
  void convertsOnceThenReadsTheStoredText() {
    var converted = expressions.find(ENGLISH).orElseThrow();
    var stored = expressions.find(ENGLISH).orElseThrow();

    assertThat(stored).isEqualTo(converted);
    assertThat(source.asked).containsExactly(CONSOLIDATION + " ENG");
  }

  @Test
  void convertsOnceForRequestsThatComeTogether() throws Exception {
    try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
      var requests =
          IntStream.range(0, 8)
              .mapToObj(i -> threads.submit(() -> expressions.find(ENGLISH)))
              .toList();
      for (var request : requests) {
        assertThat(request.get()).isPresent();
      }
    }

    assertThat(source.asked).containsExactly(CONSOLIDATION + " ENG");
  }

  @Test
  void remembersADocumentWithoutFormexInTheLanguage() {
    var irish = new ExpressionIdentifier(CONSOLIDATION, Language.GLE);

    assertThat(expressions.find(irish)).isEmpty();
    assertThat(expressions.find(irish)).isEmpty();
    assertThat(source.folders).hasSize(1);
    assertThat(valkey.getExpire(expressions.key(irish))).isPositive();
  }

  @Test
  void keysTheTextByDocumentLanguageAndConverterVersion() {
    expressions.find(ENGLISH);

    assertThat(valkey.keys("viewer:legal-text:*"))
        .containsExactly(
            "viewer:legal-text:" + conversion.converterVersion() + ":" + CONSOLIDATION + ":ENG");
  }

  private static RedisTemplate<String, byte[]> store() {
    var template = new ExpressionsConfiguration().legalTextStore(CONNECTIONS);
    template.afterPropertiesSet();
    return template;
  }

  private static LettuceConnectionFactory connections() {
    VALKEY.start();
    var factory = new LettuceConnectionFactory(VALKEY.getHost(), VALKEY.getFirstMappedPort());
    factory.afterPropertiesSet();
    factory.start();
    return factory;
  }
}
