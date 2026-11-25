// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Lookups;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.LawService;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Gives a controller test, with {@code @WebMvcTest}, how its controller gets an act ({@link
 * Catalogued}). The test mocks {@link Acts}. Each lookup is done at once, with what the mock finds
 * or throws: the test slice has no background lookup. It also gives the service that tells where
 * the text of an act is not the law, as the app has it.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(LawService.class)
public class CataloguedTesting {

  /** The lookups, from the mocked acts. A test of a page that shows no act mocks none. */
  @Bean
  Lookups lookups(ObjectProvider<Acts> acts) {
    return new Lookups() {
      @Override
      public CompletableFuture<Optional<Act>> lookUp(Eli eli) {
        return CompletableFuture.supplyAsync(() -> acts.getObject().find(eli), Runnable::run);
      }

      @Override
      public CompletableFuture<Optional<Act>> lookUp(ActIdentifier id) {
        return CompletableFuture.supplyAsync(() -> acts.getObject().find(id), Runnable::run);
      }
    };
  }

  @Bean
  Catalogued catalogued(Lookups lookups) {
    return new Catalogued(lookups);
  }
}
