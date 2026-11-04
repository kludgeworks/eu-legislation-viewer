// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Provision;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/** How the pages write dates, acts and provisions: in English, as the design has them. */
public final class Wording {
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH);

  /** A corrigendum's CELEX: the act's, then R and its number (32019R0103R(01)). */
  private static final Pattern CORRIGENDUM = Pattern.compile("(.+)R\\((\\d+)\\)");

  private Wording() {}

  /** 1 June 2017. */
  public static String date(LocalDate date) {
    return DATE.format(date);
  }

  /**
   * An act by its number, as the Official Journal cites it (2017/815); a corrigendum by its number
   * and the act's (corrigendum 1 to 2019/103); anything else by its CELEX.
   */
  public static String act(ActIdentifier act) {
    var corrigendum = CORRIGENDUM.matcher(act.celex());
    if (corrigendum.matches()) {
      return "corrigendum "
          + Integer.parseInt(corrigendum.group(2))
          + " to "
          + act(new ActIdentifier(corrigendum.group(1)));
    }
    return act.number().orElseGet(act::celex);
  }

  /** Article 2, Annex, Annex II. */
  public static String provision(Provision provision) {
    var type = provision.type().name();
    var name = type.charAt(0) + type.substring(1).toLowerCase(Locale.ROOT);
    return provision.number() == null ? name : name + " " + provision.number();
  }
}
