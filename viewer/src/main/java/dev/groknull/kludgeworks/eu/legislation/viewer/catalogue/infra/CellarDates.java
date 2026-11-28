// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Anchor;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Clause;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Provision;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.WhenAnActApplies;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.SparqlResult;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the dates of acts from the answer to {@code dates.rq}. These are Cellar's typed dates of
 * entry into force, and the dates of transposition of directives. Cellar describes a clause for
 * each date in the codes of its comment:
 *
 * <ul>
 *   <li>A code from the authority table {@code fd_335} gives the type.
 *   <li>{@code DATPUB}, {@code DATNOT} and {@code DATDOC} give the day that the date counts from.
 *   <li>{@code V ART 2} means "see Article 2".
 *   <li>{@code ET} and a CELEX number give a later act: one that amended the date, if it amends the
 *       act of the date, else one that set a day that the clause left to it. The answer gives the
 *       acts that amend the act of the date ({@code ?amending}).
 *   <li>For a date of transposition, a code from the authority table {@code fd_361} tells what the
 *       Member States do: {@code APPLICATION} or {@code ADOPTION}.
 * </ul>
 *
 * <p>It reads the codes that it knows. It keeps the comment as it is.
 */
final class CellarDates {
  private static final Logger LOG = LoggerFactory.getLogger(CellarDates.class);

  /**
   * An authority code as Cellar writes it in an annotation. For example, {@code
   * {EV|http://…/fd_335/EV}}.
   */
  private static final Pattern CODE = Pattern.compile("\\{([^|}]*)\\|[^}]*}");

  private static final Pattern PLUS_DAYS = Pattern.compile("\\+(\\d+)");
  private static final Pattern PLAIN_DAYS = Pattern.compile("\\d{1,3}");
  private static final Pattern DIGITS = Pattern.compile("\\d+");

  /**
   * The first year of a real date. Cellar writes a date that it does not know as 1001-01-01 or
   * 1002-02-02.
   */
  private static final int FIRST_YEAR = 1900;

  /** The end of the name of Cellar's property for a date of transposition of a directive. */
  private static final String TRANSPOSITION = "directive_date_transposition";

  /**
   * The code that starts the comment on a date of transposition from which the Member States apply
   * their measures.
   */
  private static final String APPLY_FROM = "APPLICATION";

  private CellarDates() {}

  /**
   * Returns the dates of each act in the answer, by act, in date order.
   *
   * <ul>
   *   <li>It leaves out consolidations, because their dates are not typed.
   *   <li>It leaves out dates of entry into force of a type that it does not know, and logs a
   *       warning for them.
   *   <li>Of the dates of transposition, it keeps the dates from which the Member States apply
   *       their measures. It leaves out the others, for example the day by which they adopt them.
   * </ul>
   */
  static Map<ActIdentifier, List<ActDate>> byAct(ActIdentifier base, SparqlResult answer) {
    var dates = new LinkedHashMap<ActIdentifier, List<ActDate>>();
    var unknown = new TreeSet<String>();
    for (var row : answer.rows()) {
      var subject = row.get("subject");
      if (!ActIdentifier.isCelex(subject)) {
        continue;
      }
      var comment = row.find("comment").orElse("");
      var amending = amending(row.find("amending").orElse(""));
      if (row.get("property").endsWith(TRANSPOSITION)) {
        transposition(row.get("date"), comment, amending)
            .ifPresent(d ->
                dates.computeIfAbsent(new ActIdentifier(subject), a -> new ArrayList<>()).add(d));
        continue;
      }
      var type = row.find("type").map(CellarDates::code).orElse("");
      var date = date(row.get("date"), type, comment, amending);
      date.ifPresentOrElse(
          d -> dates.computeIfAbsent(new ActIdentifier(subject), a -> new ArrayList<>()).add(d),
          () -> unknown.add(subject + " " + row.get("date") + " " + type));
    }
    if (!unknown.isEmpty()) {
      LOG.warn(
          "{}: {} date(s) of a type Cellar doesn't type as known, left out: {}",
          base,
          unknown.size(),
          unknown);
    }
    return dates;
  }

  /**
   * Returns the acts that set a day that a clause of an act in the answer left to them ({@link
   * Clause#setBy}), by their CELEX numbers, in order. For example, Decision 2023/201 sets the day
   * from which 2018/1862 applies. Only the comment on the date names them, so {@code
   * publications.rq} does not find them, and {@code publications-of.rq} gets their publications.
   */
  static Set<ActIdentifier> settingActs(SparqlResult answer) {
    var acts = new TreeSet<ActIdentifier>(Comparator.comparing(ActIdentifier::celex));
    for (var row : answer.rows()) {
      if (!ActIdentifier.isCelex(row.get("subject"))) {
        continue;
      }
      var text = CODE.matcher(row.find("comment").orElse("")).replaceAll("$1").strip();
      acts.addAll(clause(text, amending(row.find("amending").orElse(""))).setBy());
    }
    return acts;
  }

  /** Reads the enacted articles of an act when Cellar contradicts its publication date. */
  @FunctionalInterface
  interface EnactedDateLookup {
    List<EnactedDates.Article> read(ActIdentifier act);
  }

  /**
   * Checked dates together with each act's raw-to-corrected date mapping ({@code Optional.empty()}
   * marks a raw day that resolved ambiguously across multiple dates of the same act).
   */
  record CheckedDates(
      Map<ActIdentifier, List<ActDate>> dates,
      Map<ActIdentifier, Map<LocalDate, Optional<LocalDate>>> corrections) {}

  /**
   * Verifies each date whose clause is anchored on publication against the act's publication date
   * in the Official Journal, if known, consulting {@code enactedLookup} once per act only on a
   * mismatch and classifying each contradicted date from the act's parsed articles.
   */
  static CheckedDates checkAgainstPublication(
      Map<ActIdentifier, List<ActDate>> dates,
      Map<ActIdentifier, Publication> publications,
      EnactedDateLookup enactedLookup) {
    var cache = new HashMap<ActIdentifier, List<EnactedDates.Article>>();
    EnactedDateLookup memoized = act -> cache.computeIfAbsent(act, enactedLookup::read);
    var checked = new LinkedHashMap<ActIdentifier, List<ActDate>>();
    var corrections = new LinkedHashMap<ActIdentifier, Map<LocalDate, Optional<LocalDate>>>();
    dates.forEach(
        (act, actDates) -> checkAct(act, actDates, publications, memoized, checked, corrections));
    return new CheckedDates(checked, corrections);
  }

  private static void checkAct(
      ActIdentifier act,
      List<ActDate> actDates,
      Map<ActIdentifier, Publication> publications,
      EnactedDateLookup enactedLookup,
      Map<ActIdentifier, List<ActDate>> checked,
      Map<ActIdentifier, Map<LocalDate, Optional<LocalDate>>> corrections) {
    var publication = publications.get(act);
    var published =
        publication != null && publication.journal() != null
            ? publication.journal().published()
            : null;
    var list = new ArrayList<ActDate>(actDates.size());
    var targetsByRaw = new HashMap<LocalDate, Set<LocalDate>>();
    for (var actDate : actDates) {
      var resolved = resolveDate(act, actDate, published, enactedLookup);
      list.add(resolved);
      if (actDate.date() != null && resolved.date() != null) {
        targetsByRaw
            .computeIfAbsent(actDate.date(), _ -> new LinkedHashSet<>())
            .add(resolved.date());
      }
    }
    checked.put(act, list);
    var actCorrections = new LinkedHashMap<LocalDate, Optional<LocalDate>>();
    targetsByRaw.forEach((rawDay, resolvedDays) -> {
      if (resolvedDays.size() > 1) {
        LOG.info(
            "{}: raw date {} resolved to several days ({}); leaving matching amendments alone",
            act,
            rawDay,
            resolvedDays);
        actCorrections.put(rawDay, Optional.empty());
      } else if (resolvedDays.size() == 1) {
        var target = resolvedDays.iterator().next();
        if (!target.equals(rawDay)) {
          actCorrections.put(rawDay, Optional.of(target));
        }
      }
    });
    if (!actCorrections.isEmpty()) {
      corrections.put(act, actCorrections);
    }
  }

  private static ActDate resolveDate(
      ActIdentifier act,
      ActDate actDate,
      @Nullable LocalDate published,
      EnactedDateLookup enactedLookup) {
    if (!WhenAnActApplies.contradictsPublication(actDate, published)) {
      return actDate;
    }
    int days = actDate.clause().days() == null ? 0 : actDate.clause().days();
    var articles = enactedLookup.read(act);
    var enacted =
        EnactedDates.classifyArticles(
            articles, actDate.kind(), actDate.clause().provisions(), actDate.forPart());
    var resolved = WhenAnActApplies.resolveAgainstEnactedText(actDate, published, enacted);
    if (resolved.source() == ActDate.Source.NOT_CONFIRMED) {
      LOG.info(
          "{}: Cellar's date {} does not match publication date {} + {} day(s) ({}), and"
              + " enacted text did not settle it; marking NOT_CONFIRMED",
          act,
          actDate.date(),
          published,
          days,
          actDate.clause().text());
    } else if (resolved.source() == ActDate.Source.CELLAR) {
      LOG.info(
          "{}: Cellar's date {} confirmed by enacted text over publication comment ({} + {}"
              + " day(s): {})",
          act,
          actDate.date(),
          published,
          days,
          actDate.clause().text());
    } else {
      LOG.info(
          "{}: Cellar's date {} disagrees with publication date {} + {} day(s) ({}); using {}"
              + " ({})",
          act,
          actDate.date(),
          published,
          days,
          actDate.clause().text(),
          resolved.date(),
          resolved.source());
    }
    return resolved;
  }

  /**
   * Reads one date. The known types are {@code EV}, {@code MA} and {@code PE}, each also with
   * {@code /PART}. For any other type, it returns none. {@code amending} are the acts that amend
   * the act of the date ({@link #clause}).
   */
  static Optional<ActDate> date(
      String value, String type, String comment, Set<ActIdentifier> amending) {
    var split = type.split("/", 2);
    var kind =
        switch (split[0]) {
          case "EV" -> Kind.ENTRY_INTO_FORCE;
          case "MA" -> Kind.APPLICATION;
          case "PE" -> Kind.TAKING_EFFECT;
          default -> null;
        };
    if (kind == null) {
      return Optional.empty();
    }
    var text = CODE.matcher(comment).replaceAll("$1").strip();
    var forPart =
        split.length > 1 || text.startsWith(split[0] + "/PART") || text.startsWith("/PART");
    return Optional.of(new ActDate(day(value), kind, forPart, clause(text, amending)));
  }

  /**
   * Reads one date of transposition. It returns the date if the Member States apply their measures
   * from it: its comment starts with {@code APPLICATION}. For any other date of transposition, it
   * returns none. The date is for the whole directive. {@code amending} are the acts that amend the
   * directive ({@link #clause}).
   */
  static Optional<ActDate> transposition(
      String value, String comment, Set<ActIdentifier> amending) {
    var text = CODE.matcher(comment).replaceAll("$1").strip();
    if (!text.equals(APPLY_FROM) && !text.startsWith(APPLY_FROM + " ")) {
      return Optional.empty();
    }
    return Optional.of(new ActDate(day(value), Kind.TRANSPOSITION, false, clause(text, amending)));
  }

  /**
   * Reads the day. It returns none for a placeholder, or for a day that does not exist, for example
   * 2015-02-29.
   */
  static @Nullable LocalDate day(String value) {
    try {
      var day = LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
      return day.getYear() < FIRST_YEAR ? null : day;
    } catch (DateTimeException e) {
      return null;
    }
  }

  /**
   * Reads the clause in Cellar's comment. Examples:
   *
   * <ul>
   *   <li>{@code DATPUB +20 V ART 2}
   *   <li>{@code MA/PART V ART 2 ET 32020R0910}
   *   <li>{@code DATNOT}
   *   <li>{@code V ART 2.2(f) ET 2.2(g)}
   * </ul>
   *
   * <p>{@code ET} followed by a CELEX number names a later act. Cellar's comment is the same for an
   * act that amended the date and for an act that set a day that the clause left to it: {@link
   * WhenAnActApplies#aLaterActAmendedOrSetTheDate} tells them apart, with {@code amending}, the
   * acts that amend the act of the date. {@code ET} followed by a number adds a provision. It skips
   * {@code V} and {@code VOIR}, which mean "see", and a repeat of the type.
   */
  static Clause clause(String text, Set<ActIdentifier> amending) {
    Anchor from = null;
    Integer days = null;
    var provisions = new ArrayList<Provision>();
    var named = new ArrayList<ActIdentifier>();
    Provision.Type last = null;
    var tokens = text.isEmpty() ? new String[0] : text.split("\\s+");
    for (var i = 0; i < tokens.length; i++) {
      var token = tokens[i];
      var next = i + 1 < tokens.length ? tokens[i + 1] : null;
      switch (token) {
        case "DATPUB", "DATNOT", "DATDOC" -> {
          from =
              switch (token) {
                case "DATPUB" -> Anchor.PUBLICATION;
                case "DATNOT" -> Anchor.NOTIFICATION;
                default -> Anchor.DOCUMENT;
              };
          // The days are one token "+20", two tokens "+" and "20", or digits only up to 3 digits.
          if (next != null) {
            if (next.equals("+")
                && i + 2 < tokens.length
                && DIGITS.matcher(tokens[i + 2]).matches()) {
              days = Integer.valueOf(tokens[i + 2]);
              i += 2;
            } else {
              var plus = PLUS_DAYS.matcher(next);
              if (plus.matches()) {
                days = Integer.valueOf(plus.group(1));
                i++;
              } else if (PLAIN_DAYS.matcher(next).matches()) {
                days = Integer.valueOf(next);
                i++;
              }
            }
          }
        }
        case "ART", "ANN" -> {
          last = token.equals("ART") ? Provision.Type.ARTICLE : Provision.Type.ANNEX;
          if (next != null && isNumber(next)) {
            provisions.add(new Provision(last, next));
            i++;
          } else {
            provisions.add(new Provision(last, null));
          }
        }
        case "ET" -> {
          if (next != null && ActIdentifier.isCelex(next) && next.matches("\\d{5}[A-Z].*")) {
            named.add(new ActIdentifier(next));
            i++;
          } else if (next != null && last != null && isNumber(next)) {
            provisions.add(new Provision(last, next));
            i++;
          }
        }
        default -> {
          // Skip V and VOIR ("see"), a repeat of the type such as MA/PART or PE, and unknown
          // codes. The clause keeps them in its text.
        }
      }
    }
    var later = WhenAnActApplies.aLaterActAmendedOrSetTheDate(named, amending);
    return new Clause(from, days, provisions, later.amendedBy(), later.setBy(), text);
  }

  /**
   * Reads the acts that a date's comment names and that amend the act of the date: the answer's
   * {@code ?amending}, CELEX numbers with a space between them.
   */
  private static Set<ActIdentifier> amending(String value) {
    var acts = new LinkedHashSet<ActIdentifier>();
    for (var celex : value.strip().split("\\s+")) {
      if (ActIdentifier.isCelex(celex)) {
        acts.add(new ActIdentifier(celex));
      }
    }
    return acts;
  }

  /**
   * Returns whether a token is the number of a provision, for example 2 or 2.2(f). {@code V} is not
   * a number. It means "see".
   */
  private static boolean isNumber(String token) {
    return Character.isDigit(token.charAt(0));
  }

  /**
   * Returns the code of an authority value, for example {@code EV} for {@code {EV|…}}. If the value
   * is not an authority value, it returns the value.
   */
  private static String code(String value) {
    var matcher = CODE.matcher(value);
    return matcher.find() ? matcher.group(1) : value;
  }
}
