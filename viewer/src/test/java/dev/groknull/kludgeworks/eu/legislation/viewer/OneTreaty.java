// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expression;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.ExpressionIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expressions;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Acts and expressions held in memory for the whole-application tests, so none asks Cellar: the
 * TFEU, by its CELEX and its ELI, in English and French, with no text converted.
 */
@TestConfiguration
class OneTreaty {
  static final ActIdentifier TFEU = new ActIdentifier("12016E/TXT");

  @Bean
  @Primary
  Acts inMemoryActs() {
    var tfeu =
        new Act(
            TFEU,
            Eli.treaty("tfeu_2016"),
            new ResourceType("TREATY"),
            LocalDate.of(2016, 6, 7),
            true,
            null,
            new Timeline(
                List.of(
                    Version.original(
                        LocalDate.of(2016, 6, 7),
                        TFEU,
                        EnumSet.of(Language.ENG, Language.FRA),
                        Instant.parse("2026-07-10T01:02:19.329Z")))));
    return new Acts() {
      @Override
      public Optional<Act> find(ActIdentifier id) {
        return Optional.of(tfeu).filter(a -> a.id().equals(id));
      }

      @Override
      public Optional<Act> find(Eli eli) {
        return Optional.of(tfeu).filter(a -> eli.equals(a.eli()));
      }
    };
  }

  @Bean
  @Primary
  Expressions inMemoryExpressions() {
    return new Expressions() {
      @Override
      public Optional<Expression> find(ExpressionIdentifier id, Instant formexModified) {
        return Optional.empty();
      }
    };
  }
}
