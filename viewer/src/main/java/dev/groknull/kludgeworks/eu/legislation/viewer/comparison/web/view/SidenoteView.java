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
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Tag;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The sidenote of a difference. It shows beside the note of the difference and says who made the
 * difference.
 *
 * <p>The sidenote names the acts between the two versions that made the difference. The amendment
 * tags on the occurrence in the later text name these acts, or the amendments of the occurrence in
 * Cellar name them. The tags name only the last act that changed an occurrence. For a deleted
 * occurrence, the tags name one of these acts:
 *
 * <ul>
 *   <li>the act of the deletion that keeps the occurrence struck
 *   <li>the act that replaced, as a whole, the occurrence that the deleted occurrence was in
 * </ul>
 *
 * <p>The sidenote shows the acts as a chain, in the order that the versions integrate them. For
 * example, "Inserted by 2019/103, then replaced by 2021/255". For each act, the sidenote says when
 * the act applies. This date comes from the dates of the act, never from the date of the version.
 *
 * <p>Acts of one version leave no text between their changes. Thus:
 *
 * <ul>
 *   <li>Where the tags name an act of a version, the sidenote follows the tags. It leaves out the
 *       other acts of that version that Cellar names.
 *   <li>The acts that the tags name together changed the occurrence together, with one wording. The
 *       sidenote names them together.
 *   <li>If only Cellar names an act, the sidenote names it as if a tag did.
 *   <li>If neither the tags nor Cellar name an act between the two versions, the sidenote does not
 *       guess. If the acts between the versions are all of one version, it says that one of them
 *       made the difference, but not which. If not, it says that no act is named.
 * </ul>
 *
 * <p>An act can have several dates. The texts do not say from which of these dates a provision
 * applies. The amendment of the occurrence in Cellar chooses the date, if its day is one of the
 * dates of the act and is after the earlier version. The earlier version does not have the change.
 * Otherwise the sidenote says that the act applies in parts, and that the clause says which part
 * applies from when.
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
 * @param acts the acts that made the difference, in the order that the versions integrate them. The
 *     difference counts in the changes of each of these acts.
 * @param candidates the acts of which one made the difference, where it is not known which act.
 *     Empty where the act is known.
 * @param dates when each of these acts applies, with what sets the date
 * @param published how the acts that made the difference, or may have made it, were published, one
 *     line for each act. The hover of {@code made} lists them the same way. Null where no act is
 *     named.
 * @param pending what the sidenote says where a change of the difference does not apply on the
 *     later day of the comparison yet. For example, "2022/1190 did not apply yet on 1 August 2022".
 *     The difference then has an amber rule. Null where each change applies.
 */
public record SidenoteView(
    @Nullable String made,
    List<ActIdentifier> acts,
    List<ActIdentifier> candidates,
    List<Applies> dates,
    @Nullable String published,
    @Nullable String pending) {

  public SidenoteView {
    acts = List.copyOf(acts);
    candidates = List.copyOf(candidates);
    dates = List.copyOf(dates);
  }

  /**
   * Returns this sidenote for a difference that has a change of {@code amending} that does not
   * apply on {@code day} yet, the later day of the comparison ({@code Act.pending}). {@code today}
   * gives the tense.
   */
  public SidenoteView notApplying(Act act, ActIdentifier amending, LocalDate day, LocalDate today) {
    return new SidenoteView(
        made,
        acts,
        candidates,
        dates,
        published,
        act.cited(amending)
            + (day.isBefore(today) ? " did not apply yet on " : " does not apply yet on ")
            + Dates.inWords(day));
  }

  /**
   * Returns whether only corrigenda made the difference. If so, the difference is a correction, not
   * an amendment, and its edits show it differently.
   */
  public boolean corrected() {
    return !acts.isEmpty() && acts.stream().allMatch(ActIdentifier::corrigendum);
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
    var between = included.between(from, to);
    var parts = parts(act, included, from, to, difference, between);
    var tagged = acts(difference.tags(), each -> between.contains(each) || parts.containsKey(each));
    var markedVersions =
        tagged.keySet().stream()
            .map(
                each ->
                    Optional.ofNullable(parts.get(each))
                        .or(() -> version(included, from, to, each)))
            .flatMap(Optional::stream)
            .collect(Collectors.toSet());
    var made = new LinkedHashMap<>(tagged);
    for (var amendment : amendments) {
      var each = amendment.act();
      // Leave out the act if the tags name an act of the same version. The tags take
      // priority.
      if (between.contains(each)
          && !made.containsKey(each)
          && version(included, from, to, each).filter(markedVersions::contains).isEmpty()) {
        made.put(each, action(amendment.role()));
      }
    }
    if (made.isEmpty()) {
      return unknown(act, included, from, to, difference, language);
    }
    var steps =
        steps(
            included,
            from,
            to,
            made.keySet(),
            tagged.keySet(),
            parts,
            together(difference, between));
    var dates = new ArrayList<Applies>();
    for (var each : steps.stream().flatMap(List::stream).toList()) {
      if (each.corrigendum()) {
        dates.add(corrects(act, included, from, to, each));
      } else {
        var days =
            parts.containsKey(each)
                ? partDays(act, each, from, to)
                : amendments.stream()
                    .filter(amendment -> amendment.act().equals(each))
                    .map(Amendment::from)
                    .filter(Objects::nonNull)
                    // A day that is not after the earlier text is stale, because the earlier
                    // text does not have the change. The points of 2019/103 are an example.
                    .filter(day -> day.isAfter(from.date()))
                    .collect(Collectors.toSet());
        applies(act, each, days, language).ifPresent(dates::add);
      }
    }
    var chain = new ArrayList<String>();
    for (var i = 0; i < steps.size(); i++) {
      var step = steps.get(i);
      var first = step.getFirst();
      // An inserted occurrence was inserted in the first step. A deleted occurrence was deleted in
      // the last
      // step. If words were deleted from an occurrence that is still there, the occurrence was
      // amended.
      var kind =
          i == 0 && difference.kind() == Kind.INSERTED
              ? Kind.INSERTED
              : i == steps.size() - 1 && difference.kind() == Kind.DELETED
                  ? Kind.DELETED
                  : Kind.CHANGED;
      var verb = verb(kind, first, Objects.requireNonNull(made.get(first)));
      verb = i == 0 ? verb : verb.toLowerCase(Locale.ROOT);
      if (step.size() == 1) {
        chain.add(verb + " by " + names(act, step));
      } else if (only != null && step.contains(only)) {
        var others = step.stream().filter(each -> !each.equals(only)).toList();
        chain.add(verb + " by " + act.cited(only) + ", together with " + names(act, others));
      } else {
        chain.add(verb + " by " + names(act, step) + " together");
      }
    }
    return new SidenoteView(
        String.join(", then ", chain),
        steps.stream().flatMap(List::stream).toList(),
        List.of(),
        dates,
        act.citations(steps.stream().flatMap(List::stream).toList(), language),
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
    var candidates =
        included.between(from, to).stream().filter(each -> !each.corrigendum()).toList();
    var versions =
        candidates.stream()
            .map(each -> version(included, from, to, each))
            .flatMap(Optional::stream)
            .distinct()
            .count();
    if (candidates.size() < 2 || versions != 1) {
      return new SidenoteView(unsaid(difference), List.of(), List.of(), List.of(), null, null);
    }
    var ordered =
        included.of(version(included, from, to, candidates.getFirst()).orElseThrow()).stream()
            .filter(candidates::contains)
            .toList();
    var names = ordered.stream().map(act::cited).toList();
    var dates = new ArrayList<Applies>();
    for (var each : ordered) {
      applies(act, each, Set.of(), language).ifPresent(dates::add);
    }
    return new SidenoteView(
        unsaid(difference)
            + " It was "
            + String.join(", ", names.subList(0, names.size() - 1))
            + " or "
            + names.getLast()
            + ".",
        List.of(),
        ordered,
        dates,
        act.citations(ordered, language),
        null);
  }

  /**
   * Returns whether the sidenote names no act: neither the act that made the difference, nor acts
   * of which one made it.
   */
  public boolean unattributed() {
    return acts.isEmpty() && candidates.isEmpty();
  }

  /**
   * Returns the sidenote of {@code difference}, which no tag and no amendment in Cellar attributes,
   * in {@code version}, the version where the change first appears. It names the version and what
   * the version consolidates, and no act as the one that made the change. For example, "Deleted in
   * the version of 22 March 2026, which consolidates 2026/449 and Corrigendum 1 to 2026/449". If
   * the version consolidates no act, it names the version only.
   */
  public static SidenoteView appeared(
      Act act, Inclusions included, Version version, Difference difference) {
    var did =
        switch (difference.kind()) {
          case INSERTED -> "Inserted";
          case DELETED -> "Deleted";
          case CHANGED -> "Changed";
        };
    var consolidates = included.of(version);
    return new SidenoteView(
        did
            + " in the version of "
            + Dates.inWords(version.date())
            + (consolidates.isEmpty() ? "" : ", which consolidates " + names(act, consolidates)),
        List.of(),
        List.of(),
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
   * Returns the date line of {@code corrigendum}. A corrigendum corrects its act from the start:
   * from the day the act applies, else from its entry into force. For example, "Corrigendum 4 to
   * 2017/815 corrects 2017/815 from 1 June 2017 · first in the text of 1 January 2026". The day is
   * left out if the act has none.
   */
  private static Applies corrects(
      Act act, Inclusions included, Version from, Version to, ActIdentifier corrigendum) {
    var corrected = corrigendum.corrected().orElseThrow();
    var dated = act.datesOf(corrected).stream().filter(date -> date.date() != null).toList();
    var applying =
        dated.stream().filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE).toList();
    var start =
        (applying.isEmpty() ? dated : applying)
            .stream().map(ActDate::date).filter(Objects::nonNull).min(Comparator.naturalOrder());
    var when =
        corrigendum.cited()
            + " corrects "
            + act.cited(corrected)
            + start.map(day -> " from " + Dates.inWords(day)).orElse("")
            + version(included, from, to, corrigendum)
                .map(version -> " · first in the text of " + Dates.inWords(version.date()))
                .orElse("");
    return new Applies(when, "", null, List.of(), "");
  }

  /**
   * Returns the first version after {@code from}, up to {@code to}, that includes {@code amending}.
   */
  private static Optional<Version> version(
      Inclusions included, Version from, Version to, ActIdentifier amending) {
    return included.timeline().versions().stream()
        .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
        .filter(v -> included.includes(v, amending))
        .findFirst();
  }

  /**
   * Returns the acts that changed the occurrence of {@code difference} in a part that applies
   * between the two versions, by the version that takes the part in. Such an act is not between the
   * two versions: an earlier version included it already. Its tag on the occurrence is new in this
   * step: the earlier text does not have it. And one of its dates falls after {@code from}, up to
   * {@code to}. For example, 2024/1619 on 2013/36 from 17 January 2025 to 11 January 2026: the act
   * is included from July 2024, and a part of it applies from 11 January 2026.
   *
   * <p>A tag can also be new only because the earlier text has no tags. The date tells these apart.
   */
  private static Map<ActIdentifier, Version> parts(
      Act act,
      Inclusions included,
      Version from,
      Version to,
      Difference difference,
      Collection<ActIdentifier> between) {
    var earlier = difference.earlierTags().stream().map(Tag::act).collect(Collectors.toSet());
    var parts = new LinkedHashMap<ActIdentifier, Version>();
    for (var tag : difference.tags()) {
      var each = new ActIdentifier(tag.act());
      if (between.contains(each) || earlier.contains(tag.act()) || parts.containsKey(each)) {
        continue;
      }
      partDays(act, each, from, to).stream()
          .min(Comparator.naturalOrder())
          .flatMap(
              day ->
                  included.timeline().versions().stream()
                      .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
                      .filter(v -> !v.date().isBefore(day))
                      .findFirst())
          .ifPresent(version -> parts.put(each, version));
    }
    return parts;
  }

  /** Returns the dates of {@code amending} after {@code from}, up to {@code to}. */
  private static Set<LocalDate> partDays(
      Act act, ActIdentifier amending, Version from, Version to) {
    return act.datesOf(amending).stream()
        .map(ActDate::date)
        .filter(Objects::nonNull)
        .filter(day -> day.isAfter(from.date()) && !day.isAfter(to.date()))
        .collect(Collectors.toSet());
  }

  /**
   * Returns the acts that {@code tags} name and that {@code kept} accepts, in order. Each act comes
   * with the action of its first tag.
   */
  private static Map<ActIdentifier, String> acts(List<Tag> tags, Predicate<ActIdentifier> kept) {
    var acts = new LinkedHashMap<ActIdentifier, String>();
    for (var tag : tags) {
      var act = new ActIdentifier(tag.act());
      if (kept.test(act)) {
        acts.putIfAbsent(act, tag.action());
      }
    }
    return acts;
  }

  /**
   * Returns the acts in {@code acts} in the order that the versions after {@code from}, up to
   * {@code to}, first integrate them. There is one step for each version, which holds the acts of
   * that version.
   */
  private static List<List<ActIdentifier>> steps(
      Inclusions included,
      Version from,
      Version to,
      Collection<ActIdentifier> acts,
      Collection<ActIdentifier> tagged,
      Map<ActIdentifier, Version> parts,
      List<Set<ActIdentifier>> together) {
    var between =
        included.timeline().versions().stream()
            .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
            .toList();
    // The version of each act is the first version that has it. An act that applies in parts is
    // in several versions.
    var at = new HashMap<ActIdentifier, Version>();
    for (var each : acts) {
      if (!tagged.contains(each)) {
        between.stream()
            .filter(v -> included.includes(v, each))
            .findFirst()
            .ifPresent(version -> at.put(each, version));
      }
    }
    // An act that the tags name was the last act to change the occurrence. Thus it is in no
    // version
    // before the versions of the other acts. If it changed the occurrence together with other acts,
    // it is
    // in the first version that has all of them.
    var floor = at.values().stream().map(Version::date).max(Comparator.naturalOrder()).orElse(null);
    for (var each : tagged) {
      var part = parts.get(each);
      if (part != null) {
        at.put(each, part);
        continue;
      }
      var with =
          together.stream()
              .filter(group -> group.contains(each))
              .findFirst()
              .orElseGet(() -> Set.of(each));
      var after = between.stream().filter(v -> floor == null || !v.date().isBefore(floor)).toList();
      after.stream()
          .filter(v -> included.of(v).containsAll(with))
          .findFirst()
          .or(() -> after.stream().filter(v -> included.includes(v, each)).findFirst())
          .or(() -> between.stream().filter(v -> included.includes(v, each)).findFirst())
          .ifPresent(version -> at.put(each, version));
    }
    var steps = new ArrayList<List<ActIdentifier>>();
    for (var version : between) {
      // Keep the order that the version gives the acts.
      var step = new ArrayList<ActIdentifier>();
      included.of(version).stream()
          .filter(each -> acts.contains(each) && version.equals(at.get(each)))
          .forEach(step::add);
      parts.entrySet().stream()
          .filter(
              part ->
                  acts.contains(part.getKey())
                      && version.equals(part.getValue())
                      && !step.contains(part.getKey()))
          .map(Map.Entry::getKey)
          .forEach(step::add);
      if (!step.isEmpty()) {
        steps.add(step);
      }
    }
    return steps;
  }

  /**
   * Returns the acts between the two versions that replaced the occurrence together, by its tags.
   * Two acts that replaced the same occurrence as a whole did so at the same time. The occurrence
   * can be the occurrence itself or the occurrence it is in. If one act replaced the occurrence
   * after the other, only its own tag would be there. Each set has two or more acts.
   */
  private static List<Set<ActIdentifier>> together(
      Difference difference, Collection<ActIdentifier> between) {
    var byOccurrence = new LinkedHashMap<Optional<String>, Set<ActIdentifier>>();
    for (var tag : difference.tags()) {
      var each = new ActIdentifier(tag.act());
      if (tag.action().equals("replaced") && between.contains(each)) {
        byOccurrence
            .computeIfAbsent(Optional.ofNullable(tag.in()), in -> new LinkedHashSet<>())
            .add(each);
      }
    }
    return byOccurrence.values().stream().filter(acts -> acts.size() > 1).toList();
  }

  /**
   * Returns what the role of an amendment did, as the action of a tag says it: "replaced",
   * "inserted", "amended" or "deleted".
   */
  private static String action(Amendment.Role role) {
    return switch (role) {
      case REPLACEMENT -> "replaced";
      case DELETION, REPEAL -> "deleted";
      case AMENDMENT -> "amended";
      default -> "inserted";
    };
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
   * Returns what {@code act} did to the occurrence, as a difference of {@code kind} shows it.
   *
   * <ul>
   *   <li>"Inserted", "Deleted", "Replaced" or "Renumbered"
   *   <li>"Amended", for words that the act inserted or deleted in an occurrence that is still
   *       there
   *   <li>"Corrected", for a corrigendum
   * </ul>
   */
  private static String verb(Kind kind, ActIdentifier act, String action) {
    if (act.corrigendum()) {
      return "Corrected";
    }
    return switch (kind) {
      case INSERTED -> "Inserted";
      case DELETED -> "Deleted";
      case CHANGED ->
          switch (action) {
            case "replaced" -> "Replaced";
            case "renumbered" -> "Renumbered";
            default -> "Amended";
          };
    };
  }

  /**
   * Returns when {@code amending} applies, in words, from {@link Act#applying}. {@code days} holds
   * the days that the amendments of the occurrence in Cellar have. Returns empty if Cellar has no
   * dates of the act, or if it has no day and no clause that sets one.
   */
  private static Optional<Applies> applies(
      Act act, ActIdentifier amending, Set<LocalDate> days, Language language) {
    var name = named(act, amending);
    return act.applying(amending, days)
        .flatMap(
            applying ->
                switch (applying) {
                  case Applying.SetBy set -> {
                    var words =
                        applies(
                            act,
                            name + " applies from a date set in",
                            set.dates(),
                            amending,
                            language,
                            "");
                    // There is nothing to say if no clause sets the date.
                    yield words.clause() == null ? Optional.empty() : Optional.of(words);
                  }
                  case Applying.From from ->
                      Optional.of(
                          applies(
                              act,
                              name
                                  + " applies from "
                                  + Dates.inWords(from.day())
                                  + (from.entryIntoForce() ? ", its entry into force" : ""),
                              from.dates(),
                              amending,
                              language,
                              ""));
                  case Applying.InForce inForce ->
                      Optional.of(
                          applies(
                              act,
                              name + " in force from " + Dates.inWords(inForce.day()),
                              inForce.dates(),
                              amending,
                              language,
                              ""));
                  case Applying.InParts parts ->
                      Optional.of(
                          applies(
                              act,
                              name
                                  + " applies in parts: "
                                  + parts.starts().stream()
                                      .map(SidenoteView::part)
                                      .collect(Collectors.joining("; ")),
                              parts.dates(),
                              amending,
                              language,
                              " says which part from when"));
                });
  }

  /** Returns a part in words. For example, "in part from 1 October 2026". */
  private static String part(Applying.Start part) {
    var from = "from " + Dates.inWords(part.day());
    return part.entryIntoForce()
        ? from + ", its entry into force"
        : part.inPart() ? "in part " + from : from;
  }

  private static Applies applies(
      Act act,
      String when,
      List<ActDate> dates,
      ActIdentifier amending,
      Language language,
      String after) {
    var provisions = new LinkedHashSet<ActDate.Provision>();
    var amendedBy = new LinkedHashSet<ActIdentifier>();
    for (var date : dates) {
      provisions.addAll(date.clause().provisions());
      amendedBy.addAll(date.clause().amendedBy());
    }
    var clause =
        provisions.isEmpty()
            ? null
            : new Link(
                provisions.stream().map(ActDate.Provision::cited).collect(Collectors.joining(", "))
                    + " of "
                    + act.cited(amending),
                act.eurLex(amending, language).toString());
    return new Applies(
        when,
        when.endsWith(" in") ? " " : " · ",
        clause,
        amendedBy.stream()
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
          applies(act, act.id(), Set.of(), language)
              .map(applies -> unnamed(applies, act, act.id()))
              .orElseGet(
                  () ->
                      new Applies(
                          "applies from " + Dates.inWords(first.date()), "", null, List.of(), "")));
    }
    var each = new ActIdentifier(last.act());
    return new History(
        "Last changed by " + act.cited(each) + " (" + last.action() + ")",
        applies(act, each, Set.of(), language)
            .map(applies -> unnamed(applies, act, each))
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
