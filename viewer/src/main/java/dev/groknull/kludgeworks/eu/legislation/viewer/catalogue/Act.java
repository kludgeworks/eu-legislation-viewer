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
import java.util.SortedMap;
import java.util.TreeMap;
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
   * Returns Cellar's amendments of this act's provisions, placed at the occurrences of two texts.
   *
   * <p>An amendment is included if its act is integrated between {@code from} and {@code to}, and
   * if it changes the text in {@code language}. An amendment that applies only after {@code to} is
   * left out. An amendment that applies before {@code from} is kept. A version can lag the law by
   * weeks, so its date does not tell what the version already contains.
   *
   * <ul>
   *   <li>An addition or a replacement is placed at an occurrence of the later text. {@code later}
   *       holds the eIds of its occurrences, in document order.
   *   <li>A deletion or a repeal is placed at an occurrence of the earlier text. {@code earlier}
   *       holds the eIds of its occurrences, in document order.
   *   <li>An amendment whose location is in neither text is counted as unplaced.
   * </ul>
   */
  public Amended amended(
      Version from, Version to, List<String> earlier, List<String> later, Language language) {
    var between = integratedBetween(from, to);
    var before = Location.occurrences(earlier);
    var after = Location.occurrences(later);
    var byOccurrence = new LinkedHashMap<String, List<Amendment>>();
    var unplaced = 0;
    for (var amendment : amendments) {
      var applies = amendment.from();
      // An amendment that applies only after the later version is not in its text. It is a later
      // part of the act.
      if (!between.contains(amendment.act())
          || !amendment.changes(language)
          || (applies != null && applies.isAfter(to.date()))) {
        continue;
      }
      var found = amendment.location().in(amendment.role().removes() ? before : after);
      if (found.isEmpty()) {
        unplaced++;
        continue;
      }
      for (var eId : found.get().eIds()) {
        byOccurrence.computeIfAbsent(eId, occurrence -> new ArrayList<>()).add(amendment);
      }
    }
    return new Amended(byOccurrence, unplaced);
  }

  /**
   * Returns the timeline in {@code language}. In any one language, there is at most one version a
   * day. Returns empty if no version has that language.
   */
  public Optional<Timeline> timeline(Language language) {
    return timeline.in(language);
  }

  /**
   * Returns the acts that the versions of this act in {@code language} include: Cellar's lists, and
   * the acts that their texts tag first ({@link Inclusions}). {@code texts} are the tags of the
   * texts in {@code language} that are built.
   */
  public Inclusions inclusions(Language language, Map<Version, TextTags> texts) {
    return Inclusions.of(timeline(language).orElse(timeline), texts);
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
      LocalDate day, Language language, Map<Version, TextTags> texts) {
    return divergences(language, texts).stream().filter(each -> each.on(day)).toList();
  }

  /**
   * Returns each period in which the text of the versions in {@code language} is not the law.
   *
   * <ul>
   *   <li>A provision of an amending act that applies, but that the text does not include yet, is a
   *       pending amendment. For example, 2017/815 applies from 1 June 2017, and the version of
   *       2015/1998 of 7 June 2017 is the first to include it.
   *   <li>A provision of an amending act that the text includes before the provision applies is a
   *       prospective amendment. For example, the version of 767/2008 of 12 January 2018 includes
   *       provisions of 2017/2226 that apply later.
   * </ul>
   *
   * <p>The act itself is not among them. While it is in force and does not apply in full, its
   * application is deferred: a state of the act, {@link #deferredApplicationOn}.
   *
   * <p>{@code texts} gives the tags of the texts in {@code language} that are built. They tell
   * which version first includes each provision. {@link Divergences} tells how. An amending act
   * that does not change the text in {@code language} is no pending amendment of that text. For
   * example, 2017/837 corrects only the Swedish and the Polish text of 2015/1998.
   */
  public List<Divergence> divergences(Language language, Map<Version, TextTags> texts) {
    return Divergences.of(this, language, texts);
  }

  /**
   * Returns the version whose text a comparison shows for {@code day}, in {@code language}.
   *
   * <ul>
   *   <li>It is the version that applies on the day.
   *   <li>If an amending act applies on the day and that version does not include it, it is the
   *       first version that includes the act. If there are two or more such acts, it is the latest
   *       of their versions. That text can include a change that does not apply on the day yet: a
   *       prospective amendment of the day says where.
   *   <li>An act that no version includes does not move the version.
   * </ul>
   *
   * <p>For example, 2017/815 applies from 1 June 2017, and the version of 2015/1998 of 7 June 2017
   * is the first to include it. The text for 3 June 2017 is the version of 7 June 2017, not the
   * version of 29 February 2016 that applies on that day.
   *
   * <p>Returns empty before the first version of the act.
   *
   * @param texts the tags of the texts in {@code language} that are built, as {@link
   *     #divergences(Language, Map)} takes them
   */
  public Optional<Version> textOn(LocalDate day, Language language, Map<Version, TextTags> texts) {
    var applies = timeline(language).flatMap(each -> each.applicableOn(day));
    if (applies.isEmpty()) {
      return applies;
    }
    return divergences(day, language, texts).stream()
        .filter(each -> each.kind() == Divergence.Kind.PENDING)
        .map(Divergence::included)
        .filter(Objects::nonNull)
        .filter(included -> included.date().isAfter(applies.get().date()))
        .max(Comparator.comparing(Version::date))
        .or(() -> applies);
  }

  /**
   * Returns the occurrences of the text of {@code version} that have a change that does not apply
   * on {@code day} yet. They are the occurrences of each prospective amendment of the day, {@link
   * Divergence#in}. A comparison gives the difference at such an occurrence an amber bar. Where the
   * act itself does not apply yet, no occurrence is given: the whole text is prospective.
   *
   * <p>Empty if the text of {@code version} is not built.
   *
   * @param texts the tags of the texts in {@code language} that are built, as {@link
   *     #divergences(Language, Map)} takes them
   */
  public Prospective prospective(
      Version version, LocalDate day, Language language, Map<Version, TextTags> texts) {
    var byOccurrence = new LinkedHashMap<String, ActIdentifier>();
    var text = texts.get(version);
    if (text != null) {
      for (var divergence : divergences(day, language, texts)) {
        if (divergence.kind() == Divergence.Kind.PROSPECTIVE) {
          divergence
              .in(text.eIds())
              .forEach(occurrence -> byOccurrence.putIfAbsent(occurrence.eId(), divergence.act()));
        }
      }
    }
    return new Prospective(day, byOccurrence);
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
   *   <li>Otherwise it applies in parts, one on each day of its dates.
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
    var parts =
        days(dated).stream()
            .map(
                day -> {
                  var on = on(dated, day);
                  return new Applying.Start(
                      day, onlyEntryIntoForce(on), on.stream().allMatch(ActDate::partial));
                })
            .toList();
    var first =
        parts.stream()
            .filter(part -> !part.entryIntoForce() || whole.filter(part.day()::equals).isPresent())
            .findFirst()
            .orElseGet(parts::getFirst)
            .day();
    return Optional.of(new Applying.InParts(parts, first, dated));
  }

  /**
   * Returns the acts that amend this act and change its text in {@code language}, newest first.
   * These are the acts that its versions in {@code language} integrate, and the acts whose
   * amendments Cellar annotates. An act can be in no version yet.
   *
   * <ul>
   *   <li>This act and the corrigenda are left out. A corrigendum is not an amending act.
   *   <li>An act that corrects only other languages is left out. For example, 2017/837 corrects
   *       only the Swedish and the Polish text of 2015/1998.
   *   <li>An act is newer if its first dated date is later. An act without a dated date has the
   *       first day of the first version that integrates it. An act without either is last.
   * </ul>
   */
  public List<ActIdentifier> amendingActs(Language language) {
    var found = new LinkedHashSet<ActIdentifier>();
    timeline(language).orElse(timeline).versions().forEach(each -> found.addAll(each.integrates()));
    amendments.forEach(each -> found.add(each.act()));
    Comparator<ActIdentifier> newest =
        Comparator.comparing(
            (ActIdentifier each) -> firstDay(each, language).orElse(null),
            Comparator.nullsFirst(Comparator.naturalOrder()));
    return found.stream()
        .filter(each -> !each.equals(id) && !each.corrigendum() && inLanguage(each, language))
        .sorted(newest.thenComparing(ActIdentifier::celex).reversed())
        .toList();
  }

  /**
   * Returns the amending acts between two days, newest first. They are the acts of {@link
   * #amendingActs(Language)} that the text of {@code to} includes and the text of {@code from} does
   * not: {@link #textOn} gives the two texts, and {@link #integratedBetween} the acts. A comparison
   * of the two days shows the changes of these acts, so the history of two days and the comparison
   * name the same acts.
   *
   * <p>Returns empty if one of the days is before the first version of the act.
   *
   * @param texts the tags of the texts in {@code language} that are built, as {@link #textOn} takes
   *     them
   */
  public Optional<List<ActIdentifier>> amendingActsBetween(
      LocalDate from, LocalDate to, Language language, Map<Version, TextTags> texts) {
    var earlier = textOn(from, language, texts);
    var later = textOn(to, language, texts);
    if (earlier.isEmpty() || later.isEmpty()) {
      return Optional.empty();
    }
    var between = integratedBetween(earlier.get(), later.get());
    return Optional.of(amendingActs(language).stream().filter(between::contains).toList());
  }

  /**
   * Returns the days on which an amending act changes this act, in order, each with its acts. A
   * calendar marks these days.
   *
   * <ul>
   *   <li>An act that applies from one day ({@link #applying}) changes the act on that day.
   *   <li>An act that applies in parts changes it on each day from which a part applies. Its entry
   *       into force is such a day only if it is the first day from which a part applies.
   *   <li>An act that is in force, where Cellar does not tell from when it applies, has the day of
   *       its entry into force.
   *   <li>An act that applies from a day that Cellar does not know has no day. An act without dates
   *       has none.
   * </ul>
   */
  public SortedMap<LocalDate, List<ActIdentifier>> changeDays(Language language) {
    var days = new TreeMap<LocalDate, List<ActIdentifier>>();
    for (var each : amendingActs(language).reversed()) {
      var own =
          applying(each, Set.of())
              .map(
                  applying ->
                      switch (applying) {
                        case Applying.From from -> List.of(from.day());
                        case Applying.InParts parts ->
                            parts.starts().stream()
                                .filter(
                                    start ->
                                        !start.entryIntoForce()
                                            || start.day().equals(parts.first()))
                                .map(Applying.Start::day)
                                .toList();
                        case Applying.InForce inForce -> List.of(inForce.day());
                        case Applying.SetBy _ -> List.<LocalDate>of();
                      })
              .orElseGet(List::of);
      own.forEach(day -> days.computeIfAbsent(day, _ -> new ArrayList<>()).add(each));
    }
    return days;
  }

  /**
   * Returns the first version in {@code language} that integrates {@code amending}. The text of
   * this version is the first that has the changes of the act. The text of the version before it is
   * the text before the act. Returns empty if no version in the language integrates the act.
   */
  public Optional<Version> integrating(ActIdentifier amending, Language language) {
    return timeline(language).map(Timeline::versions).orElseGet(List::of).stream()
        .filter(version -> version.integrates().contains(amending))
        .findFirst();
  }

  /**
   * Returns the other acts that the first version with {@code amending} integrates, in {@code
   * language}, without corrigenda. The text tags most changes with the act that made them. For a
   * change with no tag, the data does not say which of these acts or {@code amending} made it.
   * Returns empty if no version integrates {@code amending}, or if it is the only act of its
   * version.
   */
  public List<ActIdentifier> integratedWith(ActIdentifier amending, Language language) {
    return integrating(amending, language).map(Version::integrates).orElseGet(List::of).stream()
        .filter(each -> !each.corrigendum() && !each.equals(amending))
        .toList();
  }

  /**
   * Returns the occurrences of {@code change} in the comparison of two texts, by their eIds. {@code
   * later} are the occurrences of the first text with the changes of the act, and {@code earlier}
   * are the occurrences of the text before it, each in document order.
   *
   * <p>The occurrences are those of the later text, if it has the place itself. If only the earlier
   * text has the place itself, the act deleted it, and the occurrences are those of the earlier
   * text. If neither has the place itself, the occurrences are those that the place is in: for
   * example, the article of a sentence. Returns empty if neither text has the place.
   */
  public static List<String> occurrences(
      Amendment change, List<String> later, List<String> earlier) {
    var after = change.location().in(later);
    var before = change.location().in(earlier);
    return after
        .filter(found -> !found.within())
        .or(() -> before.filter(found -> !found.within()))
        .or(() -> after)
        .or(() -> before)
        .map(Location.Found::eIds)
        .orElseGet(List::of);
  }

  /**
   * Returns the occurrences of a text that have a change of their own, by their eIds, each with its
   * changes in {@code language}. {@code eIds} are the occurrences of the text, in document order. A
   * change is at the occurrence that is its place. If its place is finer than the occurrences, for
   * example a sentence, it is at the occurrence that the place is in. A change whose place the text
   * does not have is at no occurrence.
   */
  public Map<String, List<Amendment>> changedOccurrences(List<String> eIds, Language language) {
    var byOccurrence = new LinkedHashMap<String, List<Amendment>>();
    var occurrences = Location.occurrences(eIds);
    for (var amendment : amendments) {
      if (!amendment.changes(language)) {
        continue;
      }
      amendment
          .location()
          .in(occurrences)
          .ifPresent(
              found ->
                  found
                      .eIds()
                      .forEach(
                          eId ->
                              byOccurrence
                                  .computeIfAbsent(eId, occurrence -> new ArrayList<>())
                                  .add(amendment)));
    }
    return byOccurrence;
  }

  /**
   * Returns the history of the provision that is the occurrence {@code occurrence} of a text: the
   * acts that changed it. {@code text} are the tags of the text.
   *
   * <ul>
   *   <li>A change at the occurrence is a change of the provision. So is a change at a part of it
   *       that the text does not number, for example a sentence.
   *   <li>A change at an occurrence that holds the provision is a change of the provision. For
   *       example, an act that replaces Article 6 changes Article 6(2).
   *   <li>A change at an occurrence in the provision is not in its history. That occurrence has its
   *       own history.
   *   <li>An act that the text tags the occurrence with changed the provision, also if Cellar
   *       annotates no change of it there.
   * </ul>
   *
   * <p>The newest act is first: the act with the latest first day, {@link #firstDay}.
   */
  public ProvisionHistory historyOf(String occurrence, TextTags text, Language language) {
    Comparator<ActIdentifier> newest =
        Comparator.comparing(
                (ActIdentifier each) -> firstDay(each, language).orElse(null),
                Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
            .thenComparing(ActIdentifier::celex)
            .reversed();
    var occurrences = Location.occurrences(text.eIds());
    var changes =
        amendments.stream()
            .filter(amendment -> amendment.changes(language))
            .filter(
                amendment ->
                    amendment
                        .location()
                        .in(occurrences)
                        .filter(
                            found ->
                                found.eIds().contains(occurrence)
                                    || (!found.within()
                                        && found.eIds().stream()
                                            .anyMatch(holder -> Amended.in(occurrence, holder))))
                        .isPresent())
            .sorted(Comparator.comparing(Amendment::act, newest))
            .toList();
    var named = changes.stream().map(Amendment::act).collect(Collectors.toSet());
    var tagging =
        text.byAct().entrySet().stream()
            .filter(each -> each.getValue().contains(occurrence))
            .map(Map.Entry::getKey)
            .filter(each -> !each.equals(id) && !named.contains(each))
            .sorted(newest)
            .toList();
    return new ProvisionHistory(occurrence, changes, tagging);
  }

  /**
   * Returns the occurrences of the text of {@code reading} that are not in the act as published,
   * where the data does not say which act inserted them. {@code texts} are the tags of the built
   * texts in {@code language}.
   *
   * <p>The data says nothing of an occurrence if the text tags no act at it, at an occurrence that
   * holds it or at an occurrence in it, and Cellar annotates no change at these occurrences. Of
   * such occurrences, only the outer occurrence is returned: an occurrence in it is not. Returns
   * none if the text of the act as published or the text of {@code reading} is not built.
   */
  public List<String> unattributed(
      Version reading, Language language, Map<Version, TextTags> texts) {
    var first = timeline(language).map(Timeline::versions).orElseGet(List::of).stream().findFirst();
    var published = first.map(texts::get).orElse(null);
    var text = texts.get(reading);
    if (published == null
        || text == null
        || first.orElseThrow().kind() != Version.Kind.ORIGINAL
        || first.orElseThrow().equals(reading)) {
      return List.of();
    }
    var original = Set.copyOf(published.eIds());
    var said = new LinkedHashSet<>(changedOccurrences(text.eIds(), language).keySet());
    text.byAct().values().forEach(said::addAll);
    var holders = new LinkedHashSet<String>();
    said.forEach(each -> holders.addAll(holding(each)));
    return text.eIds().stream()
        .filter(eId -> !original.contains(eId))
        .filter(eId -> holding(eId).stream().allMatch(original::contains))
        .filter(eId -> !said.contains(eId) && !holders.contains(eId))
        .filter(eId -> holding(eId).stream().noneMatch(said::contains))
        .toList();
  }

  /**
   * Returns the first version in {@code language} whose text has the occurrence {@code occurrence}.
   * {@code texts} are the tags of the built texts. Returns empty if no built text has the
   * occurrence.
   */
  public Optional<Version> firstWith(
      String occurrence, Language language, Map<Version, TextTags> texts) {
    return timeline(language).map(Timeline::versions).orElseGet(List::of).stream()
        .filter(
            version ->
                Optional.ofNullable(texts.get(version)).stream()
                    .anyMatch(tags -> tags.eIds().contains(occurrence)))
        .findFirst();
  }

  /**
   * Returns the eIds of the occurrences that hold the occurrence {@code eId}, as its eId names
   * them.
   */
  private static List<String> holding(String eId) {
    var holders = new ArrayList<String>();
    for (var at = eId.lastIndexOf('/'); at > 0; at = eId.lastIndexOf('/', at - 1)) {
      holders.add(eId.substring(0, at));
    }
    return holders;
  }

  /**
   * Returns the first day of {@code amending}: its first dated date, which is usually its entry
   * into force. If Cellar has no dated date of it, it is the first day of the first version in
   * {@code language} that integrates it. Returns empty if there is neither.
   */
  public Optional<LocalDate> firstDay(ActIdentifier amending, Language language) {
    return datesOf(amending).stream()
        .map(ActDate::date)
        .filter(Objects::nonNull)
        .min(Comparator.naturalOrder())
        .or(
            () ->
                timeline(language).orElse(timeline).versions().stream()
                    .filter(version -> version.integrates().contains(amending))
                    .map(Version::date)
                    .findFirst());
  }

  /**
   * Returns {@code changes} in the order of a text. {@code eIds} are the occurrences of the text,
   * in document order. A change has the position of its place. A place that the text does not have,
   * for example a deleted provision, has the position of the occurrence that holds it. A change
   * with no position is last.
   */
  public static List<Amendment> inTextOrder(List<Amendment> changes, List<String> eIds) {
    var occurrences = Location.occurrences(eIds);
    return changes.stream()
        .sorted(
            Comparator.comparing(
                change ->
                    change
                        .location()
                        .in(occurrences)
                        .or(() -> change.location().around(occurrences))
                        .map(found -> eIds.indexOf(found.eIds().getFirst()))
                        .orElse(Integer.MAX_VALUE)))
        .toList();
  }

  /**
   * Returns the corrigenda of {@code corrected} that change the text of this act in {@code
   * language}, in the order of their numbers. {@code corrected} is this act or an act that amends
   * it. A corrigendum is published only in the languages that it corrects. So a corrigendum whose
   * publication has no title in {@code language} is left out.
   */
  public List<ActIdentifier> corrigendaOf(ActIdentifier corrected, Language language) {
    var found = new LinkedHashSet<ActIdentifier>();
    timeline(language).orElse(timeline).versions().forEach(each -> found.addAll(each.integrates()));
    amendments.forEach(each -> found.add(each.act()));
    return found.stream()
        .filter(each -> each.corrected().filter(corrected::equals).isPresent())
        .filter(each -> inLanguage(each, language))
        .filter(
            each ->
                publication(each)
                    .map(publication -> publication.title(language).isPresent())
                    .orElse(true))
        .sorted(Comparator.comparing(ActIdentifier::celex))
        .toList();
  }

  /**
   * Returns the acts and corrigenda that Cellar lists for {@code version} and that change its text
   * in {@code language}, in Cellar's order. An act changes the text in a language unless each of
   * its amendments is for other languages only ({@link #inLanguage}). A corrigendum is published
   * only in the languages that it corrects, so one without a title in {@code language} is left out.
   * For example, the version of 2015/1998 of 12 April 2022 lists 2022/463, which changes only the
   * German text: in English it lists none.
   */
  public List<ActIdentifier> changingIn(Version version, Language language) {
    return version.integrates().stream()
        .filter(
            each ->
                each.corrigendum()
                    ? publication(each).flatMap(p -> p.title(language)).isPresent()
                    : inLanguage(each, language))
        .toList();
  }

  /**
   * Returns whether {@code amending} can change the text in {@code language}. It cannot if each of
   * its amendments is for other languages only.
   */
  private boolean inLanguage(ActIdentifier amending, Language language) {
    var own = amendments.stream().filter(each -> each.act().equals(amending)).toList();
    return own.isEmpty()
        || own.stream()
            .anyMatch(each -> each.languages().isEmpty() || each.languages().contains(language));
  }

  /**
   * Returns the changes that {@code amending} makes to the text of this act in {@code language}, as
   * Cellar annotates them. Each change has one place.
   */
  public List<Amendment> changesBy(ActIdentifier amending, Language language) {
    return amendments.stream()
        .filter(each -> each.act().equals(amending) && each.changes(language))
        .toList();
  }

  /**
   * Returns the day on which {@code act} enters into force, or empty if Cellar does not give it.
   * {@code act} is this act, an act that its versions integrate or an act that amends it.
   */
  public Optional<LocalDate> entryIntoForce(ActIdentifier act) {
    return datesOf(act).stream()
        .filter(date -> date.kind() == ActDate.Kind.ENTRY_INTO_FORCE)
        .map(ActDate::date)
        .filter(Objects::nonNull)
        .min(Comparator.naturalOrder());
  }

  /**
   * Returns the day from which a change of {@code amending} applies, if the data gives one day for
   * it: {@link #applying} gives a day, and not parts. {@code placeDays} are the days that Cellar
   * gives for the place of the change. Returns empty if the data does not tell the part of the
   * change.
   */
  public Optional<LocalDate> changeAppliesFrom(ActIdentifier amending, Set<LocalDate> placeDays) {
    return applying(amending, placeDays)
        .flatMap(
            applying ->
                switch (applying) {
                  case Applying.From from -> Optional.of(from.day());
                  case Applying.InParts _, Applying.SetBy _, Applying.InForce _ -> Optional.empty();
                });
  }

  /**
   * Returns the state on {@code day} of {@code act}, or of one change that it makes. {@code act} is
   * this act, an act that its versions integrate or an act that amends it. {@code placeDays} are
   * the days that Cellar gives for the place of the change. They are empty for the act as a whole.
   *
   * <ol>
   *   <li>Before the entry into force of the act, it is not in force.
   *   <li>If it applies from one day ({@link #applying}), it applies from that day, and does not
   *       apply yet before it.
   *   <li>If it applies in parts, it does not apply yet before the first part. It applies from the
   *       last part, unless a clause sets one more day that Cellar does not know. Between them,
   *       Cellar does not tell which part the change is in, so the state is not known.
   *   <li>In each other case the state is not known. For example, a clause sets the day by an
   *       event, or Cellar has no dates of the act.
   * </ol>
   */
  public State stateOn(ActIdentifier act, Set<LocalDate> placeDays, LocalDate day) {
    if (entryIntoForce(act).filter(day::isBefore).isPresent()) {
      return State.NOT_IN_FORCE;
    }
    var openDay =
        datesOf(act).stream()
            .anyMatch(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE && date.date() == null);
    return applying(act, placeDays)
        .map(
            applying ->
                switch (applying) {
                  case Applying.From from ->
                      day.isBefore(from.day()) ? State.NOT_APPLYING : State.APPLIES;
                  case Applying.InParts parts -> {
                    if (day.isBefore(parts.first())) {
                      yield State.NOT_APPLYING;
                    }
                    yield !openDay && !day.isBefore(parts.starts().getLast().day())
                        ? State.APPLIES
                        : State.NOT_KNOWN;
                  }
                  case Applying.SetBy _, Applying.InForce _ -> State.NOT_KNOWN;
                })
        .orElse(State.NOT_KNOWN);
  }

  /**
   * Returns how {@code amending} stands on {@code day}: the number of its changes to the text in
   * {@code language} in each state, {@link #stateOn}. If Cellar lists no changed place for it, the
   * act counts as one change, with the state of the whole act.
   */
  public Standing standingOn(ActIdentifier amending, Language language, LocalDate day) {
    var changes = changesBy(amending, language);
    var states =
        changes.isEmpty()
            ? List.of(stateOn(amending, Set.of(), day))
            : changes.stream().map(each -> stateOn(amending, days(each), day)).toList();
    return new Standing(
        states.contains(State.NOT_IN_FORCE),
        count(states, State.APPLIES),
        count(states, State.NOT_APPLYING),
        count(states, State.NOT_KNOWN));
  }

  /** Returns the days that Cellar gives for the place of {@code change}: one day, or none. */
  public static Set<LocalDate> days(Amendment change) {
    return change.from() == null ? Set.of() : Set.of(change.from());
  }

  private static int count(List<State> states, State state) {
    return (int) states.stream().filter(state::equals).count();
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
   * Returns the deferred application of this act on {@code day}, in {@code language}: the act is in
   * force, but it applies in full only from a later day. The reader gets a text on such a day: it
   * is on or after the first version of the act in {@code language}, and the act applies in full
   * after that version starts. For example, 2016/679 on 1 March 2017, and 2018/1862 on 1 August
   * 2022, which applies in part on that day.
   *
   * <p>Returns empty on a day without a text, on or after the day from which the act applies in
   * full, and where Cellar does not give that day.
   */
  public Optional<DeferredApplication> deferredApplicationOn(LocalDate day, Language language) {
    var first = timeline(language).map(Timeline::versions).orElseGet(List::of).stream().findFirst();
    return appliesFrom(id)
        .filter(inFull -> inFull.date() != null)
        .filter(
            inFull ->
                first.isPresent()
                    && Objects.requireNonNull(inFull.date()).isAfter(first.get().date())
                    && !day.isBefore(first.get().date())
                    && day.isBefore(inFull.date()))
        .map(inFull -> new DeferredApplication(inFull, appliesInPartOn(id, day)));
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
