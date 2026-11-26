// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Lookups;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NamedAct;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NamedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Finds the act that a reader names with its CELEX number or its ELI. It reads the words, asks the
 * catalogue, and starts or joins the lookup of an act that the catalogue does not have yet ({@link
 * Lookups}). It logs a search and a lookup when they start and when they end.
 */
@Service
public class NamedActService implements NamedActs {
  private static final Logger LOG = LoggerFactory.getLogger(NamedActService.class);

  /**
   * How long {@link #lookedUp} waits for the lookup, for a page without JavaScript. After that, the
   * answer is that the Publications Office did not answer.
   */
  private static final Duration WAIT = Duration.ofSeconds(60);

  /**
   * The CELEX number of an act, as a reader may type it. For example, {@code 32015R1998} or {@code
   * celex:32015r1998}.
   */
  private static final Pattern CELEX = Pattern.compile("(?:CELEX:)?(3\\d{4}[A-Z]\\d{4})");

  /**
   * The ELI of an act, as a reader may type or paste it. For example:
   *
   * <ul>
   *   <li>{@code http://data.europa.eu/eli/reg_impl/2015/1998/oj}
   *   <li>{@code eli/reg_impl/2015/1998}
   *   <li>{@code reg_impl/2015/1998}
   * </ul>
   */
  private static final Pattern ELI =
      Pattern.compile(
          "(?:https?://data\\.europa\\.eu/)?(?:eli/)?([a-z_]+)/(\\d{4})/(\\d+)(?:/oj)?(?:/[a-z]{3})?/?");

  private final Acts acts;
  private final Lookups lookups;
  private final Search search;

  public NamedActService(Acts acts, Lookups lookups, Search search) {
    this.acts = acts;
    this.lookups = lookups;
    this.search = search;
  }

  @Override
  public Optional<NamedAct> searched(String words, Search.Kind kind) {
    var started = System.nanoTime();
    return named(words, kind)
        .map(named -> {
          var act = named.catalogued(acts);
          if (act.isEmpty()) {
            LOG.info(
                "Searched for “{}” by {}: not in the catalogue yet, so the page looks it up",
                typed(words),
                named.name().as());
            return new NamedAct.LookingUp(named.name());
          }
          return ended("Searched for", words, named, act.orElse(null), started);
        });
  }

  @Override
  public Optional<NamedAct> lookedUp(String words) {
    var started = System.nanoTime();
    return named(words, Search.Kind.ALL)
        .map(named -> {
          var act = named.catalogued(acts);
          if (act.isEmpty()) {
            LOG.info("Waiting for the lookup of “{}” by {}", typed(words), named.name().as());
            try {
              act = named.lookUp(lookups).get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (ExecutionException | TimeoutException | InterruptedException e) {
              if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
              }
              // The lookup logs its own failure, with its cause.
              LOG.info(
                  "Looked up “{}” by {}: the Publications Office did not answer, in {} ms",
                  typed(words),
                  named.name().as(),
                  millis(started));
              return new NamedAct.Unanswered(named.name());
            }
          }
          return ended("Looked up", words, named, act.orElse(null), started);
        });
  }

  @Override
  public Optional<Running> lookingUp(String words) {
    return named(words, Search.Kind.ALL)
        .map(named ->
            new Running(named.name(), named.lookUp(lookups).thenApply(Optional::isPresent)));
  }

  /**
   * Logs how a search or a lookup ended, and returns the answer.
   *
   * @param act the act, or null if no act has the name
   */
  private static NamedAct ended(
      String did, String words, Named named, @Nullable Act act, long started) {
    var eli = act == null ? null : act.eli();
    LOG.info(
        "{} “{}” by {}: {}, in {} ms",
        did,
        typed(words),
        named.name().as(),
        act == null
            ? "no act has it"
            : act.id().celex() + (eli == null ? ", which has no ELI" : ""),
        millis(started));
    // An act without an ELI has no page here.
    return eli == null ? new NamedAct.NoAct(named.name()) : new NamedAct.Found(named.name(), eli);
  }

  /** Returns what names one act in {@code words}, or empty if they are title words. */
  private Optional<Named> named(String words, Search.Kind kind) {
    var named = Named.of(words);
    if (named.isEmpty() && kind == Search.Kind.ALL) {
      named = search.identified(words).map(id -> Named.of("indexed identifier", id));
    }
    return named;
  }

  /** What a reader typed that names one act: its CELEX number or its ELI. */
  private record Named(NamedAct.Name name, @Nullable ActIdentifier celex, @Nullable Eli eli) {

    /** Returns the act that {@code words} name, or empty if they are title words. */
    static Optional<Named> of(String words) {
      var celex = CELEX.matcher(words.toUpperCase(Locale.ROOT));
      if (celex.matches()) {
        return Optional.of(of("CELEX number", new ActIdentifier(celex.group(1))));
      }
      var path = ELI.matcher(words.toLowerCase(Locale.ROOT));
      if (path.matches()) {
        var eli = new Eli(path.group(1), Integer.parseInt(path.group(2)), path.group(3));
        return Optional.of(
            new Named(new NamedAct.Name("ELI", eli.path(), eli.eurLex(Language.ENG)), null, eli));
      }
      return Optional.empty();
    }

    static Named of(String as, ActIdentifier id) {
      return new Named(new NamedAct.Name(as, id.cited(), id.eurLex(Language.ENG)), id, null);
    }

    Optional<Act> catalogued(Acts acts) {
      return celex != null ? acts.catalogued(celex) : acts.catalogued(Objects.requireNonNull(eli));
    }

    CompletableFuture<Optional<Act>> lookUp(Lookups lookups) {
      return celex != null ? lookups.lookUp(celex) : lookups.lookUp(Objects.requireNonNull(eli));
    }
  }

  /**
   * Returns what the reader typed, on one line. Thus a line break in it cannot start a log line of
   * its own.
   */
  private static String typed(String words) {
    return words.replaceAll("[\\r\\n]+", " ");
  }

  private static long millis(long started) {
    return Duration.ofNanos(System.nanoTime() - started).toMillis();
  }
}
