// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.ActSource;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Cellar, as far as the module's tests need it: one act, 2015/1998, with its original text and a
 * consolidation (in English and French), and what it was asked for, from any thread.
 */
@TestConfiguration(proxyBeanMethods = false)
public class OneActCellar {
  static final ActIdentifier CELEX = new ActIdentifier("32015R1998");
  static final Eli ELI = new Eli("reg_impl/2015/1998");

  static final Act ACT =
      new Act(
          CELEX,
          ELI,
          new ResourceType("REG_IMPL"),
          LocalDate.of(2015, 11, 9),
          true,
          null,
          new Timeline(
              List.of(
                  Version.original(
                      LocalDate.of(2015, 11, 30),
                      CELEX,
                      Set.of(Language.ENG, Language.FRA),
                      Instant.parse("2015-11-12T10:15:30Z")),
                  new Version(
                      Version.Kind.CONSOLIDATED,
                      LocalDate.of(2026, 3, 22),
                      "02015R1998-20260322",
                      false,
                      List.of(new ActIdentifier("32026R0001")),
                      Set.of(Language.ENG, Language.FRA),
                      Instant.parse("2026-03-30T08:00:00Z")))));

  final List<String> asked = new CopyOnWriteArrayList<>();

  @Bean
  @Primary
  ActSource cellar() {
    return new ActSource() {
      @Override
      public Optional<Act> find(ActIdentifier id) {
        asked.add(id.celex());
        return id.equals(CELEX) ? Optional.of(ACT) : Optional.empty();
      }

      @Override
      public Optional<Act> find(Eli eli) {
        asked.add("ELI " + eli);
        return eli.equals(ELI) ? Optional.of(ACT) : Optional.empty();
      }
    };
  }
}
