// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import org.jspecify.annotations.Nullable;

/**
 * An act. It is a work in Cellar, identified by its CELEX and its ELI, and it has a timeline.
 *
 * @param id the act's CELEX
 * @param eli its ELI. Older acts may have none.
 * @param type its resource type, for example REG_IMPL, DIR or DEC
 * @param titles its title in each official language that Cellar has it in, as the Official Journal
 *     printed it
 * @param dateOfDocument the date it was adopted or signed
 * @param inForce whether it is in force, as Cellar says
 * @param endOfValidity the day it ceases to apply, or null while it is open-ended
 * @param timeline its versions
 * @param dates its own dates. These are the dates when it enters into force, applies or takes
 *     effect.
 * @param amendingDates the own dates of each act that its versions integrate or that amends it, by
 *     act. An act that amends it can be in no version yet. An act that Cellar has no dates for is
 *     left out.
 * @param amendments what other acts did to its provisions, as Cellar annotates them. Each amendment
 *     has one location.
 * @param publications how this act, each act that its versions integrate and each act that amends
 *     it were published. Each publication has the ELI, the titles and the Official Journal
 *     reference. An act that Cellar has nothing for is left out.
 */
@AggregateRoot
public record Act(
    @Identity ActIdentifier id,
    @Nullable Eli eli,
    ResourceType type,
    Map<Language, String> titles,
    @Nullable LocalDate dateOfDocument,
    boolean inForce,
    @Nullable LocalDate endOfValidity,
    Timeline timeline,
    List<ActDate> dates,
    Map<ActIdentifier, List<ActDate>> amendingDates,
    List<Amendment> amendments,
    Map<ActIdentifier, Publication> publications) {

  /**
   * The kinds of date that tell from when a whole act applies, in the order in which {@link
   * #appliesFrom} looks for them.
   */
  private static final List<Set<ActDate.Kind>> WHOLE_ACT =
      List.of(
          EnumSet.of(ActDate.Kind.APPLICATION, ActDate.Kind.TAKING_EFFECT),
          EnumSet.of(ActDate.Kind.TRANSPOSITION),
          EnumSet.of(ActDate.Kind.ENTRY_INTO_FORCE));

  public Act {
    publications = Map.copyOf(publications);
    titles = Map.copyOf(titles);
    dates = List.copyOf(dates);
    amendingDates =
        amendingDates.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    amendments = List.copyOf(amendments);
  }

  /** Returns its title in {@code language}, or empty if Cellar has no title in that language. */
  public Optional<String> title(Language language) {
    return Optional.ofNullable(titles.get(language));
  }

  /**
   * Returns the own dates of {@code act}, if it is this act, an act that its versions integrate or
   * an act that amends it. For any other act, returns an empty list.
   */
  public List<ActDate> datesOf(ActIdentifier act) {
    return act.equals(id) ? dates : amendingDates.getOrDefault(act, List.of());
  }

  /**
   * Returns how {@code act} was published. The act is this act or an act that its versions
   * integrate. Returns empty if Cellar does not say.
   */
  public Optional<Publication> publication(ActIdentifier act) {
    return Optional.ofNullable(publications.get(act));
  }

  /**
   * Returns how {@code each}, this act or an act that its versions integrate, is cited, as {@link
   * ActIdentifier#cited}. Since 2015 EU acts share one numbering, so the number alone is clear. An
   * older act also needs its kind, in short form, which its English title gives. For example, "Reg.
   * (EC) 272/2009" for Regulation (EC) No 272/2009, or "Dir. 2010/18/EU". Cellar's titles may have
   * a no-break space after "No". If the catalogue has no such title, it is the number alone.
   */
  public String cited(ActIdentifier each) {
    var plain = each.cited();
    if (each.corrigendum() || each.numberedAlone()) {
      return plain;
    }
    var title = publication(each).flatMap(publication -> publication.title(Language.ENG));
    if (title.isEmpty()) {
      return plain;
    }
    var regulation =
        Pattern.compile(
                "Regulation\\s+\\(([^)]+)\\)\\s+No\\s+" + Pattern.quote(plain) + "\\b",
                Pattern.UNICODE_CHARACTER_CLASS)
            .matcher(title.get());
    if (regulation.find()) {
      return "Reg. (" + regulation.group(1) + ") " + plain;
    }
    var other =
        Pattern.compile(
                "(Directive|Decision)\\s+(?:No\\s+)?"
                    + Pattern.quote(plain)
                    + "/([A-Za-z]+(?:,\\s?[A-Za-z]+)?)",
                Pattern.UNICODE_CHARACTER_CLASS)
            .matcher(title.get());
    if (other.find()) {
      return (other.group(1).equals("Directive") ? "Dir. " : "Dec. ")
          + plain
          + "/"
          + other.group(2);
    }
    return plain;
  }

  /**
   * Returns how {@code each} is cited with its publication: its number, its title and its Official
   * Journal reference. {@code each} is this act or an act that its versions integrate. For example,
   * "2026/449: Commission Implementing Regulation (EU) 2026/449 of 27 February 2026 amending … · OJ
   * L, 2026/449, 2.3.2026". If the act has no title in {@code language}, the title is in English.
   * If Cellar says nothing of its publication, returns only how it is cited, {@link
   * ActIdentifier#cited}.
   */
  public String citation(ActIdentifier each, Language language) {
    var name = cited(each);
    return publication(each)
        .map(
            publication -> {
              var title = publication.title(language).or(() -> publication.title(Language.ENG));
              var journal = publication.journal();
              return name
                  + title.map(t -> ": " + t).orElse("")
                  + (journal == null ? "" : " · " + journal.reference());
            })
        .orElse(name);
  }

  /** Returns the {@link #citation} of each of {@code acts}, one line for each act. */
  public String citations(List<ActIdentifier> acts, Language language) {
    return acts.stream().map(each -> citation(each, language)).collect(Collectors.joining("\n"));
  }

  /**
   * Returns the address on EUR-Lex of {@code each}, in {@code language}. {@code each} is this act
   * or an act that its versions integrate. The address uses the ELI of {@code each} if Cellar has
   * one. If not, it uses its CELEX.
   */
  public URI eurLex(ActIdentifier each, Language language) {
    return publication(each)
        .map(Publication::eli)
        .map(eli -> eli.eurLex(language))
        .orElseGet(() -> each.eurLex(language));
  }

  /**
   * Returns the acts that the versions after {@code from}, up to and including {@code to},
   * integrate, in the order of the versions. The acts include their corrigenda.
   */
  public SequencedSet<ActIdentifier> integratedBetween(Version from, Version to) {
    var between = new LinkedHashSet<ActIdentifier>();
    for (var version : timeline.versions()) {
      if (version.date().isAfter(from.date()) && !version.date().isAfter(to.date())) {
        between.addAll(version.integrates());
      }
    }
    return between;
  }

  /**
   * Returns Cellar's amendments of this act's provisions, placed at the units of two texts.
   *
   * <p>An amendment is included if its act is integrated between {@code from} and {@code to}, and
   * if it changes the text in {@code language}. An amendment that applies only after {@code to} is
   * left out. An amendment that applies before {@code from} is kept. A version can lag the law by
   * weeks, so its date does not tell what the version already contains.
   *
   * <ul>
   *   <li>An addition or a replacement is placed at a unit of the later text. {@code later} holds
   *       the eIds of its units, in document order.
   *   <li>A deletion or a repeal is placed at a unit of the earlier text. {@code earlier} holds the
   *       eIds of its units, in document order.
   *   <li>An amendment whose location is in neither text is counted as unplaced.
   * </ul>
   */
  public Amended amended(
      Version from, Version to, List<String> earlier, List<String> later, Language language) {
    var between = integratedBetween(from, to);
    var byUnit = new LinkedHashMap<String, List<Amendment>>();
    var unplaced = 0;
    for (var amendment : amendments) {
      var applies = amendment.from();
      // An amendment that applies only after the later version is not in its text. It is a later
      // stage of the act.
      if (!between.contains(amendment.act())
          || !amendment.changes(language)
          || (applies != null && applies.isAfter(to.date()))) {
        continue;
      }
      var found = amendment.location().in(amendment.role().removes() ? earlier : later);
      if (found.isEmpty()) {
        unplaced++;
        continue;
      }
      for (var eId : found.get().eIds()) {
        byUnit.computeIfAbsent(eId, unit -> new ArrayList<>()).add(amendment);
      }
    }
    return new Amended(byUnit, unplaced);
  }

  /**
   * Returns the timeline in {@code language}. In any one language, there is at most one version a
   * day. Returns empty if no version has that language.
   */
  public Optional<Timeline> timeline(Language language) {
    return timeline.in(language);
  }

  /**
   * Returns the version that applies on {@code date} for a reader in {@code language}. Returns
   * empty before the text of the act first applies. The returned version applies on {@code date}
   * across the act, but may lack {@code language}. When multiple versions share a day, it picks the
   * one in {@code language}, falling back to the other.
   */
  public Optional<Version> versionOn(LocalDate date, Language language) {
    return timeline.applicableOn(date, language);
  }

  /**
   * Returns where the text that a reader gets on {@code day} is not the law on that day, as {@link
   * #divergences(Language, Map)} finds it.
   */
  public List<Divergence> divergences(
      LocalDate day, Language language, Map<Version, TextMarks> texts) {
    return divergences(language, texts).stream().filter(each -> each.on(day)).toList();
  }

  /**
   * Returns each period in which the text of the versions in {@code language} is not the law.
   *
   * <ul>
   *   <li>The text is early while the act is in force but does not apply yet. For example, 2016/679
   *       is in force from 24 May 2016 and applies from 25 May 2018.
   *   <li>The text is late where a provision of an amending act applies, but the text does not
   *       include it yet. For example, 2017/815 applies from 1 June 2017, and the version of
   *       2015/1998 of 7 June 2017 is the first to include it.
   *   <li>The text is early where it includes a provision of an amending act before the provision
   *       applies. For example, the version of 767/2008 of 12 January 2018 includes provisions of
   *       2017/2226 that apply later.
   * </ul>
   *
   * <p>{@code texts} gives the marks of the texts in {@code language} that are built. They tell
   * which version first includes each provision. {@link Divergences} tells how. An amending act
   * that does not change the text in {@code language} makes no text late. For example, 2017/837
   * corrects only the Swedish and the Polish text of 2015/1998.
   */
  public List<Divergence> divergences(Language language, Map<Version, TextMarks> texts) {
    return Divergences.of(this, language, texts);
  }

  /**
   * Returns the date from which the whole of {@code act} applies. {@code act} is this act, an act
   * that its versions integrate or an act that amends it.
   *
   * <ul>
   *   <li>It is the act's date of application or of taking effect that is not for a part.
   *   <li>A directive that has no such date applies from the day from which the Member States apply
   *       it. For example, 2019/878 enters into force on 27 June 2019, and the Member States apply
   *       it from 29 December 2020.
   *   <li>An act that has neither date applies from its entry into force. A directive does not: the
   *       Member States apply it through their own measures, so its entry into force does not tell
   *       from when it applies. For example, 2015/2366 enters into force on 12 January 2016, and
   *       the Member States apply it from 13 January 2018. Cellar has that day only as the deadline
   *       for transposition, and that deadline is not always the day from which they apply it.
   *   <li>A date for a part is an exception for that part. It does not move the date of the whole
   *       act. For example, 2021/255 enters into force on 11 March 2021, and only four of its
   *       points apply from later dates. So it applies from 11 March 2021.
   * </ul>
   *
   * <p>Returns empty if Cellar does not know the date. For example, 2019/413 applies from a date
   * that its Article 3 sets by an event. It also returns empty for a directive that the Member
   * States apply from more than one day, because Cellar does not tell which day is for the whole
   * directive. For example, they apply one point of 2019/2034 from 26 March 2020, and the rest from
   * 26 June 2021.
   */
  public Optional<ActDate> appliesFrom(ActIdentifier act) {
    var whole = datesOf(act).stream().filter(date -> !date.partial()).toList();
    for (var kinds : WHOLE_ACT) {
      if (act.directive() && kinds.contains(ActDate.Kind.ENTRY_INTO_FORCE)) {
        return Optional.empty();
      }
      var dates = whole.stream().filter(date -> kinds.contains(date.kind())).toList();
      if (dates.stream().anyMatch(date -> date.date() == null)
          || (kinds.contains(ActDate.Kind.TRANSPOSITION) && dates.size() > 1)) {
        return Optional.empty();
      }
      if (!dates.isEmpty()) {
        return dates.stream()
            .min(Comparator.comparing(date -> Objects.requireNonNull(date.date())));
      }
    }
    return Optional.empty();
  }

  /**
   * Returns whether a provision of {@code act} can apply from {@code day}. It can if {@code day} is
   * one of the dates of the act. It cannot if {@code day} is only its entry into force, and the
   * whole act applies from another day or from a day that Cellar does not know. {@code act} is this
   * act, an act that its versions integrate or an act that amends it.
   */
  public boolean appliesFromDay(ActIdentifier act, LocalDate day) {
    var on = datesOf(act).stream().filter(date -> day.equals(date.date())).toList();
    if (on.stream().anyMatch(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE)) {
      return true;
    }
    return !on.isEmpty() && appliesFrom(act).map(ActDate::date).filter(day::equals).isPresent();
  }

  /**
   * Returns when {@code act} applies, as its dates tell. {@code act} is this act, an act that its
   * versions integrate or an act that amends it.
   *
   * <ol>
   *   <li>If Cellar has no day for any date of application, the act applies from a day that its
   *       clauses set.
   *   <li>If Cellar gives one day for the provision, in {@code placeDays}, and the provision can
   *       apply from it ({@link #appliesFromDay}), the act applies from that day.
   *   <li>If it applies from one day, and not in part, it applies from {@link #appliesFrom}. A
   *       directive that has only an entry into force is in force, and Cellar does not tell from
   *       when it applies.
   *   <li>Otherwise it applies in stages, one on each day of its dates.
   * </ol>
   *
   * <p>Returns empty if Cellar has no dates of the act.
   *
   * @param placeDays the days that Cellar gives for the provision, from its amendments
   */
  public Optional<Applying> applying(ActIdentifier act, Set<LocalDate> placeDays) {
    var all = datesOf(act);
    if (all.isEmpty()) {
      return Optional.empty();
    }
    var dated = all.stream().filter(date -> date.date() != null).toList();
    var applying =
        dated.stream().filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE).toList();
    if (dated.isEmpty()
        || (applying.isEmpty()
            && all.stream().anyMatch(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE))) {
      return Optional.of(
          new Applying.SetBy(all.stream().filter(date -> date.date() == null).toList()));
    }
    if (placeDays.size() == 1) {
      var day = placeDays.iterator().next();
      if (appliesFromDay(act, day)) {
        var on = on(dated, day);
        return Optional.of(new Applying.From(day, onlyEntryIntoForce(on), on));
      }
    }
    var used = applying.isEmpty() ? dated : applying;
    if (days(used).size() == 1 && applying.stream().noneMatch(ActDate::partial)) {
      var whole = appliesFrom(act).map(ActDate::date);
      if (whole.isPresent()) {
        return Optional.of(new Applying.From(whole.get(), applying.isEmpty(), used));
      }
      if (applying.isEmpty()) {
        return Optional.of(new Applying.InForce(days(dated).getFirst(), dated));
      }
    }
    var whole = appliesFrom(act).map(ActDate::date);
    var stages =
        days(dated).stream()
            .map(
                day -> {
                  var on = on(dated, day);
                  return new Applying.Stage(
                      day, onlyEntryIntoForce(on), on.stream().allMatch(ActDate::partial));
                })
            .toList();
    var first =
        stages.stream()
            .filter(
                stage -> !stage.entryIntoForce() || whole.filter(stage.day()::equals).isPresent())
            .findFirst()
            .orElseGet(stages::getFirst)
            .day();
    return Optional.of(new Applying.InStages(stages, first, dated));
  }

  /** Returns the dates among {@code dates} that fall on {@code day}. */
  private static List<ActDate> on(List<ActDate> dates, LocalDate day) {
    return dates.stream().filter(date -> day.equals(date.date())).toList();
  }

  /** Returns whether each of {@code dates} is an entry into force. */
  private static boolean onlyEntryIntoForce(List<ActDate> dates) {
    return dates.stream().allMatch(date -> date.kind() == ActDate.Kind.ENTRY_INTO_FORCE);
  }

  /** Returns the days of {@code dates}, without repeats, in order. */
  private static List<LocalDate> days(List<ActDate> dates) {
    return dates.stream().map(ActDate::date).filter(Objects::nonNull).distinct().sorted().toList();
  }

  /**
   * Returns the day from which {@code version} first applies, if that is after the day on which it
   * starts. Only the act as published can apply later: it starts on its entry into force, and can
   * apply from a later day. That day is its earliest date after the version starts that is not its
   * entry into force. For example, 2016/679 enters into force on 24 May 2016 and applies from 25
   * May 2018. Returns empty for a consolidated version, and for an act that applies from its entry
   * into force.
   */
  public Optional<LocalDate> appliesLater(Version version) {
    if (version.kind() != Version.Kind.ORIGINAL) {
      return Optional.empty();
    }
    return dates.stream()
        .filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE)
        .map(ActDate::date)
        .filter(Objects::nonNull)
        .filter(day -> day.isAfter(version.date()))
        .min(Comparator.naturalOrder());
  }

  /**
   * Returns whether this act applies only in part from {@code day}: each of its dates on that day
   * is for a part. For example, 2024/1689 applies in part from 2 February 2025.
   */
  public boolean inPartFrom(LocalDate day) {
    return dates.stream().filter(date -> day.equals(date.date())).allMatch(ActDate::partial);
  }

  /**
   * Returns the day from which the whole of this act applies, {@link #appliesFrom}, if that is
   * after {@code day}. For example, 2024/1689 applies in part from 2 February 2025, and in full
   * from 2 August 2026.
   */
  public Optional<LocalDate> inFullAfter(LocalDate day) {
    return appliesFrom(id).map(ActDate::date).filter(from -> from.isAfter(day));
  }

  /**
   * Returns whether {@code act} applies in part on {@code day}: a date for a part of it falls on or
   * before that day. {@code act} is this act, an act that its versions integrate or an act that
   * amends it.
   */
  public boolean appliesInPartOn(ActIdentifier act, LocalDate day) {
    return datesOf(act).stream()
        .anyMatch(date -> date.partial() && date.date() != null && !date.date().isAfter(day));
  }

  /**
   * Returns the provisions that set a day from which this act applies, where Cellar does not know
   * the day. For example, a day that an article sets by an event.
   */
  public List<ActDate.Provision> unknownDays() {
    return dates.stream()
        .filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE && date.date() == null)
        .flatMap(date -> date.clause().provisions().stream())
        .distinct()
        .toList();
  }
}
