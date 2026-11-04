// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Anchor;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Clause;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Provision;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.ActSource;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Cellar, as far as the module's tests need it: one act, 2015/1998, with its original text and
 * three of its consolidations (in English and French), its own dates and some of those of the acts
 * they integrate, as Cellar has them, and what it was asked for, from any thread.
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
          Map.of(
              Language.ENG,
              "Commission Implementing Regulation (EU) 2015/1998 of 5 November 2015 laying down"
                  + " detailed measures for the implementation of the common basic standards on"
                  + " aviation security (Text with EEA relevance)"),
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
                  consolidation(
                      LocalDate.of(2021, 3, 15),
                      Instant.parse("2021-03-24T08:00:00Z"),
                      "32019R0413",
                      "32021R0255"),
                  consolidation(
                      LocalDate.of(2021, 12, 31),
                      Instant.parse("2022-01-14T08:00:00Z"),
                      "32019R0103",
                      "32019R1583",
                      "32020R0910"),
                  consolidation(
                      LocalDate.of(2026, 3, 22),
                      Instant.parse("2026-03-30T08:00:00Z"),
                      "32026R0449"))),
          List.of(
              date(2015, 11, 15, Kind.ENTRY_INTO_FORCE, false, Anchor.PUBLICATION, 1, "3"),
              date(2016, 2, 1, Kind.APPLICATION, false, null, null, "2")),
          Map.of(
              new ActIdentifier("32019R0413"),
              List.of(
                  new ActDate(
                      null,
                      Kind.APPLICATION,
                      false,
                      new Clause(null, null, List.of(article("3")), List.of(), "V ART 3")),
                  date(2019, 3, 16, Kind.ENTRY_INTO_FORCE, false, Anchor.PUBLICATION, 1, "2")),
              new ActIdentifier("32019R0103"),
              List.of(
                  date(2019, 2, 1, Kind.ENTRY_INTO_FORCE, false, null, null, "2"),
                  new ActDate(
                      LocalDate.of(2021, 12, 31),
                      Kind.APPLICATION,
                      true,
                      new Clause(
                          null,
                          null,
                          List.of(article("2")),
                          List.of(new ActIdentifier("32020R0910")),
                          "MA/PART V ART 2 ET 32020R0910"))),
              new ActIdentifier("32026R0449"),
              List.of(
                  date(2026, 3, 22, Kind.ENTRY_INTO_FORCE, false, Anchor.PUBLICATION, 20, "2"),
                  date(2026, 10, 1, Kind.APPLICATION, true, null, null, "2"))));

  final List<String> asked = new CopyOnWriteArrayList<>();

  private static Version consolidation(LocalDate date, Instant modified, String... integrates) {
    return new Version(
        Version.Kind.CONSOLIDATED,
        date,
        "02015R1998-" + date.toString().replace("-", ""),
        false,
        Arrays.stream(integrates).map(ActIdentifier::new).toList(),
        Set.of(Language.ENG, Language.FRA),
        modified);
  }

  /** A date set by an article, with its clause as Cellar writes it. */
  private static ActDate date(
      int year,
      int month,
      int day,
      Kind kind,
      boolean partial,
      @Nullable Anchor from,
      @Nullable Integer days,
      String article) {
    var text =
        (partial ? "MA/PART " : "")
            + (from == null ? "" : "DATPUB +" + days + " ")
            + "V ART "
            + article;
    return new ActDate(
        LocalDate.of(year, month, day),
        kind,
        partial,
        new Clause(from, days, List.of(article(article)), List.of(), text));
  }

  private static Provision article(String number) {
    return new Provision(Provision.Type.ARTICLE, number);
  }

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
