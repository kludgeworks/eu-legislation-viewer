// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Applicability;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attribution;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Tag;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Modification;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The sidenote of a difference. It shows beside the difference and says who made the difference.
 *
 * <p>The rules are the domain's: {@link Attribution#of} finds the acts that made the difference and
 * puts them in steps, and {@link Applicability} says from when each act's change applies. This
 * class puts them into words. For example, "Inserted by 2019/103, then replaced by 2021/255", with
 * a date line for each act. Where no act is named, the sidenote does not guess: if the acts between
 * the two versions are all of one version, it says that one of them made the difference, but not
 * which. If not, it says that no act is named.
 *
 * @param made who made the difference. For example:
 *     <ul>
 *       <li>"Replaced by 2026/449"
 *       <li>"Inserted by 2019/103, then replaced by 2021/255"
 *       <li>"Replaced by 2019/103 and 2019/1583 together"
 *       <li>"The data does not say which act changed this. It was 2019/103 or 2019/1583."
 *       <li>"The data does not say which act deleted this.", where nothing names an act between the
 *           two versions
 *     </ul>
 *
 * @param attribution who made the difference: the acts that made it, or the acts of which one made
 *     it
 * @param dates when each of these acts applies, with what sets the date
 * @param published how the acts that made the difference, or may have made it, were published, one
 *     line for each act. The hover of {@code made} lists them the same way. Null where no act is
 *     named.
 * @param prospective what the sidenote says where a change of the difference does not apply on the
 *     later day of the comparison yet, a prospective amendment. For example, "2022/1190 did not
 *     apply yet on 1 August 2022". The difference then has an amber bar. Null where each change
 *     applies.
 */
public record SidenoteView(
    @Nullable String made,
    Attribution attribution,
    List<Applies> dates,
    @Nullable String published,
    @Nullable String prospective) {

  public SidenoteView {
    dates = List.copyOf(dates);
  }

  /**
   * Returns this sidenote for a difference that has a change of {@code amending} that does not
   * apply on {@code day} yet, the later day of the comparison ({@code Act.prospective}). {@code
   * today} gives the tense.
   */
  public SidenoteView notApplying(Act act, ActIdentifier amending, LocalDate day, LocalDate today) {
    return new SidenoteView(
        made,
        attribution,
        dates,
        published,
        act.cited(amending)
            + (day.isBefore(today) ? " did not apply yet on " : " does not apply yet on ")
            + Dates.inWords(day));
  }

  /**
   * Returns the sidenote of {@code difference}, for a comparison that is not limited to the changes
   * of one act.
   */
  public static SidenoteView of(
      Act act,
      Inclusions included,
      Version from,
      Version to,
      Difference difference,
      List<Amendment> amendments,
      Language language) {
    return of(act, included, from, to, difference, amendments, null, language);
  }

  /**
   * Returns the sidenote of {@code difference}, in the comparison of the version {@code from} of
   * {@code act} with the version {@code to}.
   *
   * @param included the acts that each version includes ({@link Inclusions}): Cellar's lists, and
   *     the acts that the built texts tag first
   * @param amendments the amendments of the occurrence in Cellar, by the acts between the two
   *     versions. {@code Amended.of} finds them.
   * @param only the act whose changes the comparison is limited to, if any
   * @param language the language of the links
   */
  public static SidenoteView of(
      Act act,
      Inclusions included,
      Version from,
      Version to,
      Difference difference,
      List<Amendment> amendments,
      @Nullable ActIdentifier only,
      Language language) {
    var attribution = Attribution.of(act, included, from, to, difference, amendments);
    if (attribution.steps().isEmpty()) {
      return unknown(act, included, from, to, difference, language);
    }
    var dates = new ArrayList<Applies>();
    for (var step : attribution.steps()) {
      for (var each : step.acts()) {
        Applicability.of(act, included, from, to, each, amendments, step.inPart().contains(each))
            .map(applicability -> applies(act, applicability, language))
            .ifPresent(dates::add);
      }
    }
    var chain = new ArrayList<String>();
    var steps = attribution.steps();
    for (var i = 0; i < steps.size(); i++) {
      var step = steps.get(i);
      var verb = i == 0 ? verb(step.modification()) : step.modification().word();
      var acts = step.acts();
      if (acts.size() == 1) {
        chain.add(verb + " by " + names(act, acts));
      } else if (only != null && acts.contains(only)) {
        var others = acts.stream().filter(each -> !each.equals(only)).toList();
        chain.add(verb + " by " + act.cited(only) + ", together with " + names(act, others));
      } else {
        chain.add(verb + " by " + names(act, acts) + " together");
      }
    }
    return new SidenoteView(
        String.join(", then ", chain),
        attribution,
        dates,
        act.citations(attribution.acts(), language),
        null);
  }

  /**
   * Returns the sidenote of a difference for which no act is named. If the acts between the two
   * versions are all of one version, the sidenote says that one of them made it, but not which. If
   * not, the sidenote does not say who made it.
   */
  private static SidenoteView unknown(
      Act act,
      Inclusions included,
      Version from,
      Version to,
      Difference difference,
      Language language) {
    var attribution = Attribution.unnamed(included, from, to);
    var candidates = attribution.candidates();
    if (candidates.isEmpty()) {
      return new SidenoteView(unsaid(difference), Attribution.NONE, List.of(), null, null);
    }
    var names = candidates.stream().map(act::cited).toList();
    var dates = new ArrayList<Applies>();
    for (var each : candidates) {
      Applicability.of(act, each, Set.of())
          .map(applicability -> applies(act, applicability, language))
          .ifPresent(dates::add);
    }
    return new SidenoteView(
        unsaid(difference)
            + " It was "
            + String.join(", ", names.subList(0, names.size() - 1))
            + " or "
            + names.getLast()
            + ".",
        attribution,
        dates,
        act.citations(candidates, language),
        null);
  }

  /**
   * Returns the sidenote of {@code difference}, which no tag and no amendment in Cellar attributes,
   * in {@code version}, the version where the change first appears. It names the text of that
   * version and what the version consolidates, and no act as the one that made the change. The page
   * says "text", not "version". For example, "Deleted in the text of 22 March 2026, which
   * consolidates 2026/449 and Corrigendum 1 to 2026/449". If the version consolidates no act, it
   * names the text only.
   */
  public static SidenoteView appeared(
      Act act, Inclusions included, Version version, Difference difference) {
    var did = verb(Modification.of(difference.kind()));
    var consolidates = included.of(version);
    return new SidenoteView(
        did
            + " in the text of "
            + Dates.inWords(version.date())
            + (consolidates.isEmpty() ? "" : ", which consolidates " + names(act, consolidates)),
        Attribution.NONE,
        List.of(),
        null,
        null);
  }

  /**
   * Returns the words for a difference for which the data names no act: "The data does not say
   * which act deleted this."
   */
  private static String unsaid(Difference difference) {
    var did =
        switch (difference.kind()) {
          case INSERTED -> "inserted";
          case DELETED -> "deleted";
          case CHANGED -> "changed";
        };
    return "The data does not say which act " + did + " this.";
  }

  /**
   * Returns the numbers of the acts as a list in words. For example, "2019/103 and 2019/1583", or
   * "2017/815, 2019/103 and 2019/1583".
   */
  private static String names(Act act, Collection<ActIdentifier> acts) {
    var names = acts.stream().map(act::cited).toList();
    return names.size() == 1
        ? names.getFirst()
        : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
  }

  /**
   * Returns {@code modification} as the first word of a sidenote. For example, "Replaced" or
   * "Corrected".
   */
  static String verb(Modification modification) {
    var word = modification.word();
    return Character.toUpperCase(word.charAt(0)) + word.substring(1);
  }

  /**
   * Returns {@code applicability} as the date line of the sidenote says it. For example, "2017/815
   * · OJ L 122, 13.5.2017 · applies from 1 June 2017 · Article 2 of 2017/815", or "Corrigendum 4 to
   * 2017/815 corrects 2017/815 from 1 June 2017 · first in the text of 1 January 2026".
   */
  private static Applies applies(Act act, Applicability applicability, Language language) {
    return switch (applicability) {
      case Applicability.Corrects corrects ->
          new Applies(
              corrects.act().cited()
                  + " corrects "
                  + act.cited(corrects.corrected())
                  + (corrects.from() == null ? "" : " from " + Dates.inWords(corrects.from()))
                  + (corrects.firstIn() == null
                      ? ""
                      : " · first in the text of " + Dates.inWords(corrects.firstIn())),
              "",
              null,
              List.of(),
              "");
      case Applicability.Applies applies -> {
        var name = named(act, applies.act());
        yield switch (applies.applying()) {
          case Applying.SetBy _ ->
              applies(act, name + " applies from a date set in", applies, language, "");
          case Applying.From from ->
              applies(
                  act,
                  name
                      + " applies from "
                      + Dates.inWords(from.day())
                      + (from.entryIntoForce() ? ", its entry into force" : ""),
                  applies,
                  language,
                  "");
          case Applying.InForce inForce ->
              applies(
                  act,
                  name + " in force from " + Dates.inWords(inForce.day()),
                  applies,
                  language,
                  "");
          case Applying.InParts parts ->
              applies(
                  act,
                  name
                      + " applies in parts: "
                      + parts.starts().stream()
                          .map(SidenoteView::part)
                          .collect(Collectors.joining("; ")),
                  applies,
                  language,
                  " says which part from when");
        };
      }
    };
  }

  /** Returns a part in words. For example, "in part from 1 October 2026". */
  private static String part(Applying.Start part) {
    var from = "from " + Dates.inWords(part.day());
    return part.entryIntoForce()
        ? from + ", its entry into force"
        : part.inPart() ? "in part " + from : from;
  }

  private static Applies applies(
      Act act, String when, Applicability.Applies applies, Language language, String after) {
    var amending = applies.act();
    var clause =
        applies.clause().isEmpty()
            ? null
            : new Link(
                applies.clause().stream()
                        .map(ActDate.Provision::cited)
                        .collect(Collectors.joining(", "))
                    + " of "
                    + act.cited(amending),
                act.eurLex(amending, language).toString());
    return new Applies(
        when,
        when.endsWith(" in") ? " " : " · ",
        clause,
        applies.amendedBy().stream()
            .map(each -> new Link(act.cited(each), act.eurLex(each, language).toString()))
            .toList(),
        clause == null ? "" : after);
  }

  /**
   * Returns how a date line names {@code amending}: its number, then its OJ reference where the
   * catalogue has it. For example, "2026/449 · OJ L, 2026/449, 2.3.2026 ·".
   */
  private static String named(Act act, ActIdentifier amending) {
    return act.cited(amending)
        + act.publication(amending)
            .map(Publication::journal)
            .map(journal -> " · " + journal.reference() + " ·")
            .orElse("");
  }

  /**
   * When an act applies, as the sidenote says it. For example, "applies from 1 October 2026 ·
   * Article 2 of 2026/449, as amended by 2020/910".
   *
   * @param when when the act applies. For example, "applies from 1 October 2026", or "2026/449
   *     applies in parts: from 22 March 2026, its entry into force; in part from 1 October 2026".
   * @param between what goes between {@code when} and the clause. This is a dot, or a space after
   *     "set in".
   * @param clause the clause that sets the date, linked to EUR-Lex. For example, "Article 2 of
   *     2026/449". Null where Cellar does not say.
   * @param amendedBy the acts that changed the clause since, linked to EUR-Lex. For example,
   *     2020/910.
   * @param after what follows the clause. For parts, this is "says which part from when".
   */
  public record Applies(
      String when, String between, @Nullable Link clause, List<Link> amendedBy, String after) {
    public Applies {
      amendedBy = List.copyOf(amendedBy);
    }
  }

  /** A link, with its words and its address. */
  public record Link(String label, String href) {}

  /**
   * Returns the history of a provision that is the same in both versions. The Synopsis shows it
   * beside the provision.
   *
   * <p>The history names the act that the versions of {@code act} integrated last, among the acts
   * that the tags in {@code to} name. The tags are those of the provision, or those of the
   * occurrence that an act put in its form as a whole. {@link Tag} has them. Corrigenda are left
   * out. The history says what the act did and when it applies.
   *
   * <p>If no act changed the provision, the history says "As in the original act". It then applies
   * from the dates of the act, or else from the first version of the act.
   */
  public static History history(
      Act act, Inclusions included, Version to, List<Tag> tags, Language language) {
    @Nullable Tag last = null;
    @Nullable LocalDate integrated = null;
    for (var tag : tags) {
      var each = new ActIdentifier(tag.act());
      if (each.corrigendum()) {
        continue;
      }
      var on =
          included.timeline().versions().stream()
              .filter(version -> !version.date().isAfter(to.date()))
              .filter(version -> included.includes(version, each))
              .map(Version::date)
              .min(LocalDate::compareTo);
      if (on.isPresent() && (integrated == null || on.get().isAfter(integrated))) {
        last = tag;
        integrated = on.get();
      }
    }
    if (last == null) {
      var first = act.timeline().versions().getFirst();
      return new History(
          "As in the original act",
          Applicability.of(act, act.id(), Set.of())
              .map(applicability -> unnamed(applies(act, applicability, language), act, act.id()))
              .orElseGet(
                  () ->
                      new Applies(
                          "applies from " + Dates.inWords(first.date()), "", null, List.of(), "")));
    }
    var each = new ActIdentifier(last.act());
    return new History(
        "Last changed by " + act.cited(each) + " (" + last.modification().word() + ")",
        Applicability.of(act, each, Set.of())
            .map(applicability -> unnamed(applies(act, applicability, language), act, each))
            .orElse(null));
  }

  /**
   * Returns {@code applies} without the act at the start of its date line, because the line before
   * names the act already. For example, "Last changed by 2019/103 (replaced) · applies from 1
   * February 2019".
   */
  private static Applies unnamed(Applies applies, Act act, ActIdentifier named) {
    var name = named(act, named) + " ";
    return applies.when().startsWith(name)
        ? new Applies(
            applies.when().substring(name.length()),
            applies.between(),
            applies.clause(),
            applies.amendedBy(),
            applies.after())
        : applies;
  }

  /**
   * The history of a provision, as the Synopsis shows it beside a provision that is the same in
   * both versions.
   *
   * @param said what last made the provision as it is. For example, "Last changed by 2019/103
   *     (replaced)" or "As in the original act".
   * @param applies when that change applies, with what sets the date. Null where nothing says.
   */
  public record History(String said, @Nullable Applies applies) {}
}
