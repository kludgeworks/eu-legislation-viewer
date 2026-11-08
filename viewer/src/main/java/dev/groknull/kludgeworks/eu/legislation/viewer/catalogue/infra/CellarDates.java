// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Anchor;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Clause;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Provision;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.SparqlResult;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the dates of acts from the answer to {@code dates.rq}. These are Cellar's typed dates of
 * entry into force. Cellar describes a clause for each date in the codes of its comment:
 *
 * <ul>
 *   <li>A code from the authority table {@code fd_335} gives the type.
 *   <li>{@code DATPUB}, {@code DATNOT} and {@code DATDOC} give the day that the date counts from.
 *   <li>{@code V ART 2} means "see Article 2".
 *   <li>{@code ET} and a CELEX number give an act that changed the date.
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

  private static final Pattern DAYS = Pattern.compile("\\+?(\\d+)");

  /**
   * The first year of a real date. Cellar writes a date that it does not know as 1001-01-01 or
   * 1002-02-02.
   */
  private static final int FIRST_YEAR = 1900;

  private CellarDates() {}

  /**
   * Returns the dates of each act in the answer, by act, in date order. It leaves out
   * consolidations, because their dates are not typed. It also leaves out dates of a type that it
   * does not know, and logs a warning for them.
   */
  static Map<ActIdentifier, List<ActDate>> byAct(ActIdentifier base, SparqlResult answer) {
    var dates = new LinkedHashMap<ActIdentifier, List<ActDate>>();
    var unknown = new TreeSet<String>();
    for (var row : answer.rows()) {
      var subject = row.get("subject");
      if (!ActIdentifier.isCelex(subject)) {
        continue;
      }
      var type = row.find("type").map(CellarDates::code).orElse("");
      var date = date(row.get("date"), type, row.find("comment").orElse(""));
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
   * Verifies each date whose clause is anchored on publication against the act's publication date
   * in the Official Journal, if known. Where Cellar's recorded date does not match {@code
   * publication + days}, it logs at debug and adopts the date given by the clause.
   */
  static Map<ActIdentifier, List<ActDate>> checkAgainstPublication(
      Map<ActIdentifier, List<ActDate>> dates, Map<ActIdentifier, Publication> publications) {
    var checked = new LinkedHashMap<ActIdentifier, List<ActDate>>();
    for (var entry : dates.entrySet()) {
      var act = entry.getKey();
      var actDates = entry.getValue();
      var publication = publications.get(act);
      var published =
          publication != null && publication.journal() != null
              ? publication.journal().published()
              : null;
      var list = new ArrayList<ActDate>(actDates.size());
      for (var actDate : actDates) {
        if (published != null && actDate.clause().from() == Anchor.PUBLICATION) {
          int days = actDate.clause().days() == null ? 0 : actDate.clause().days();
          var fromClause = published.plusDays(days);
          if (actDate.date() != null && !actDate.date().equals(fromClause)) {
            LOG.debug(
                "{}: Cellar's date {} doesn't match publication date {} + {} day(s) ({}); using {}",
                act,
                actDate.date(),
                published,
                days,
                actDate.clause().text(),
                fromClause);
            list.add(new ActDate(fromClause, actDate.kind(), actDate.partial(), actDate.clause()));
            continue;
          }
        }
        list.add(actDate);
      }
      checked.put(act, list);
    }
    return checked;
  }

  /**
   * Reads one date. The known types are {@code EV}, {@code MA} and {@code PE}, each also with
   * {@code /PART}. For any other type, it returns none.
   */
  static Optional<ActDate> date(String value, String type, String comment) {
    var parts = type.split("/", 2);
    var kind =
        switch (parts[0]) {
          case "EV" -> Kind.ENTRY_INTO_FORCE;
          case "MA" -> Kind.APPLICATION;
          case "PE" -> Kind.TAKING_EFFECT;
          default -> null;
        };
    if (kind == null) {
      return Optional.empty();
    }
    var text = CODE.matcher(comment).replaceAll("$1").strip();
    var partial =
        parts.length > 1 || text.startsWith(parts[0] + "/PART") || text.startsWith("/PART");
    return Optional.of(new ActDate(day(value), kind, partial, clause(text)));
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
   * <p>{@code ET} followed by a CELEX number adds an act to the acts that changed the date. {@code
   * ET} followed by a number adds a provision. It skips {@code V} and {@code VOIR}, which mean
   * "see", and a repeat of the type.
   */
  static Clause clause(String text) {
    Anchor from = null;
    Integer days = null;
    var provisions = new ArrayList<Provision>();
    var amendedBy = new ArrayList<ActIdentifier>();
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
          // The days are one token "+20", or two tokens "+" and "20".
          if (next != null && next.startsWith("+")) {
            var number = next.length() > 1 ? next : i + 2 < tokens.length ? tokens[i + 2] : "";
            var matcher = DAYS.matcher(number);
            if (matcher.matches()) {
              days = Integer.valueOf(matcher.group(1));
              i += next.length() > 1 ? 1 : 2;
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
            amendedBy.add(new ActIdentifier(next));
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
    return new Clause(from, days, provisions, amendedBy, text);
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
