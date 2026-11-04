// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Anchor;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Clause;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Provision;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
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
 * Acts' own dates from the answer to dates.rq: Cellar's typed dates of entry into force, with the
 * clause its comment on each describes in its codes (fd_335 for the type; DATPUB, DATNOT and DATDOC
 * for the day a date counts from; "V ART 2" for see Article 2; "ET" and a CELEX for an act that
 * changed it). The codes are read as far as they are known; the comment is kept as it is.
 */
final class CellarDates {
  private static final Logger LOG = LoggerFactory.getLogger(CellarDates.class);

  /** An authority code as Cellar writes it in an annotation: {EV|http://…/fd_335/EV}. */
  private static final Pattern CODE = Pattern.compile("\\{([^|}]*)\\|[^}]*}");

  private static final Pattern DAYS = Pattern.compile("\\+?(\\d+)");

  /**
   * The first year of a real date: Cellar writes one it doesn't know as 1001-01-01 or 1002-02-02.
   */
  private static final int FIRST_YEAR = 1900;

  private CellarDates() {}

  /**
   * The dates of each act in the answer, by the act, in date order; consolidations (whose dates
   * aren't typed) and dates of a type this doesn't know are left out, the latter with a warning.
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

  /** The date; none for a type this doesn't know (EV, MA, PE, each also /PART). */
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

  /** The day; none for a placeholder or a day that doesn't exist (2015-02-29). */
  static @Nullable LocalDate day(String value) {
    try {
      var day = LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
      return day.getYear() < FIRST_YEAR ? null : day;
    } catch (DateTimeException e) {
      return null;
    }
  }

  /**
   * The clause in Cellar's comment: "DATPUB +20 V ART 2", "MA/PART V ART 2 ET 32020R0910",
   * "DATNOT", "V ART 2.2(f) ET 2.2(g)". ET joins an act's CELEX to the acts that changed the date,
   * or a further number to the provisions; V and VOIR (see) and the type repeated are skipped.
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
          // "+20", or "+" and "20"
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
          // V, VOIR (see), the type repeated (MA/PART, PE), or a code not known: kept in the text
        }
      }
    }
    return new Clause(from, days, provisions, amendedBy, text);
  }

  /** Whether a token is a provision's number: 2, 2.2(f). Not V, which is "see". */
  private static boolean isNumber(String token) {
    return Character.isDigit(token.charAt(0));
  }

  /** The code of an authority value ({EV|…} is EV), or the value if it isn't one. */
  private static String code(String value) {
    var matcher = CODE.matcher(value);
    return matcher.find() ? matcher.group(1) : value;
  }
}
