// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * What the ▼ amendment marks of a text in Read say and where they lead, one entry for each act that
 * amended the act up to the version read. The marks are in the text, which the content module
 * renders without knowing the act; js/markers.js looks each mark's act up here.
 *
 * <p>A mark's hover says what happened, which act did it and when that act applies: "Replaced by
 * 2026/449 · applies from 1 October 2026 · Article 2 of 2026/449". A click opens the step that made
 * the change in Compare: from the version before the one that first includes the act, to that
 * version.
 *
 * @param marks one entry for each amending act, in the order the versions include them
 */
public record MarksView(List<Mark> marks) {

  public MarksView {
    marks = List.copyOf(marks);
  }

  /**
   * Returns the marks of the text of {@code reading}, a version of {@code act}, in {@code
   * language}.
   */
  public static MarksView of(Act act, Version reading, Language language) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var versions =
        act.timeline()
            .in(language)
            .map(Timeline::versions)
            .filter(those -> those.contains(reading))
            .orElseGet(() -> act.timeline().versions());
    var seen = new HashSet<ActIdentifier>();
    var marks = new ArrayList<Mark>();
    for (var i = 1; i < versions.size() && !versions.get(i).date().isAfter(reading.date()); i++) {
      var version = versions.get(i);
      var before = versions.get(i - 1);
      for (var each : version.integrates()) {
        if (seen.add(each)) {
          marks.add(
              new Mark(
                  each.celex(),
                  applies(act, each),
                  Links.compare(eli, before.date(), version.date(), language)));
        }
      }
    }
    return new MarksView(marks);
  }

  /**
   * Returns when {@code amending} applies, with the clause that says so. For example, "applies from
   * 1 April 2023 · Article 2 of 2023/566", or "applies in stages from 22 March 2026 · Article 2 of
   * 2026/449". It is the act's date, not the unit's: an act that applies in stages says when the
   * first stage starts. Returns null if Cellar has no day for it.
   */
  static @Nullable String applies(Act act, ActIdentifier amending) {
    var dated = act.datesOf(amending).stream().filter(date -> date.date() != null).toList();
    var applying =
        dated.stream().filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE).toList();
    // If every date of application is in part, the entry into force is when the rest applies.
    var used = applying.stream().allMatch(ActDate::partial) ? dated : applying;
    var days =
        used.stream().map(ActDate::date).filter(Objects::nonNull).distinct().sorted().toList();
    if (days.isEmpty()) {
      return null;
    }
    var provisions = new LinkedHashSet<ActDate.Provision>();
    used.forEach(date -> provisions.addAll(date.clause().provisions()));
    return (days.size() == 1 ? "applies from " : "applies in stages from ")
        + Dates.inWords(days.getFirst())
        + (provisions.isEmpty()
            ? ""
            : " · "
                + provisions.stream()
                    .map(ActDate.Provision::cited)
                    .collect(Collectors.joining(", "))
                + " of "
                + amending.cited());
  }

  /**
   * What the marks of one amending act say and where they lead.
   *
   * @param act the CELEX of the amending act, as the marks name it
   * @param applies when the act applies, with the clause that says so. Null if not known.
   * @param compare the address of the step that made the act's changes, in Compare
   */
  public record Mark(String act, @Nullable String applies, String compare) {}
}
