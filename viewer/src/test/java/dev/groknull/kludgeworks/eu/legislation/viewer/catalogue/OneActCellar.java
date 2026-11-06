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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Cellar, as far as the module's tests need it: one act, 2015/1998, with its original text and four
 * of its consolidations (in English and French; the latest, of 1 October 2026, without a PDF yet),
 * its own dates and some of those of the acts they integrate, as Cellar has them, and what it was
 * asked for, from any thread. What it answers can be changed ({@link #act}): to the same act with
 * its consolidation of 1 March 2022 too (REFRESHED), as if Cellar listed a version more. Searched
 * for words with security among them, it finds 2015/1998 and 2026/449, which amends it; nothing for
 * others.
 */
@TestConfiguration(proxyBeanMethods = false)
public class OneActCellar {
  public static final ActIdentifier CELEX = new ActIdentifier("32015R1998");
  public static final Eli ELI = new Eli("reg_impl/2015/1998");

  public static final Act ACT =
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
                      false,
                      Instant.parse("2021-03-24T08:00:00Z"),
                      "32019R0413",
                      "32021R0255"),
                  consolidation(
                      LocalDate.of(2021, 12, 31),
                      false,
                      Instant.parse("2022-01-14T08:00:00Z"),
                      "32019R0103",
                      "32019R1583",
                      "32020R0910"),
                  consolidation(
                      LocalDate.of(2026, 3, 22),
                      false,
                      Instant.parse("2026-03-30T08:00:00Z"),
                      "32026R0449"),
                  consolidation(
                      LocalDate.of(2026, 10, 1),
                      true,
                      Instant.parse("2026-10-02T08:00:00Z"),
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
                  date(2026, 10, 1, Kind.APPLICATION, true, null, null, "2"))),
          List.of());

  /** The act as Cellar lists it with its consolidation of 1 March 2022 too. */
  static final Act REFRESHED =
      new Act(
          ACT.id(),
          ACT.eli(),
          ACT.type(),
          ACT.titles(),
          ACT.dateOfDocument(),
          ACT.inForce(),
          ACT.endOfValidity(),
          new Timeline(
              Stream.concat(
                      ACT.timeline().versions().stream(),
                      Stream.of(
                          consolidation(
                              LocalDate.of(2022, 3, 1),
                              false,
                              Instant.parse("2022-03-10T08:00:00Z"),
                              "32021R0255")))
                  .sorted(Comparator.comparing(Version::date))
                  .toList()),
          ACT.dates(),
          ACT.amendingDates(),
          ACT.amendments());

  final List<String> asked = new CopyOnWriteArrayList<>();

  /** What Cellar answers for 2015/1998. */
  volatile Act act = ACT;

  private static Version consolidation(
      LocalDate date, boolean interim, Instant modified, String... integrates) {
    return new Version(
        Version.Kind.CONSOLIDATED,
        date,
        "02015R1998-" + date.toString().replace("-", ""),
        interim,
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

  /** 2026/449, as a search finds it. */
  public static final Search.Match AMENDING =
      new Search.Match(
          new ActIdentifier("32026R0449"),
          new Eli("reg_impl/2026/449"),
          new ResourceType("REG_IMPL"),
          "Commission Implementing Regulation (EU) 2026/449 of 27 February 2026 amending"
              + " Implementing Regulation (EU) 2015/1998 as regards certain detailed measures for"
              + " the implementation of the common basic standards on aviation security",
          true,
          List.of(new Search.Amends(CELEX, ELI)));

  @Bean
  @Primary
  Search search() {
    return query -> {
      asked.add("SEARCH " + String.join(" ", query.words()) + " " + query.kind().code());
      return found(query);
    };
  }

  /** What Cellar finds for {@code query}: 2015/1998 and 2026/449 for security, else nothing. */
  public static Search.Found found(Search.Query query) {
    if (!query.words().contains("security") || query.offset() > 0) {
      return new Search.Found(List.of(), false);
    }
    var avsec =
        new Search.Match(
            CELEX, ELI, ACT.type(), ACT.title(Language.ENG).orElseThrow(), true, List.of());
    return new Search.Found(List.of(avsec, AMENDING), false);
  }

  @Bean
  @Primary
  ActSource cellar() {
    return new ActSource() {
      @Override
      public Optional<Act> find(ActIdentifier id) {
        asked.add(id.celex());
        return id.equals(CELEX) ? Optional.of(act) : Optional.empty();
      }

      @Override
      public Optional<Act> find(Eli eli) {
        asked.add("ELI " + eli);
        return eli.equals(ELI) ? Optional.of(act) : Optional.empty();
      }
    };
  }
}
