// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Divergence.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedSet;
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
 * @param amendingDates the own dates of each act that its versions integrate, by act. An act that
 *     Cellar has no dates for is left out.
 * @param amendments what other acts did to its provisions, as Cellar annotates them. Each amendment
 *     has one location.
 * @param publications how this act and each act that its versions integrate were published. Each
 *     publication has the ELI, the titles and the Official Journal reference. An act that Cellar
 *     has nothing for is left out.
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
   * Returns the own dates of {@code act}, if it is this act or an act that its versions integrate.
   * For any other act, returns an empty list.
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
   * Returns where the text that a reader in {@code language} gets on {@code day} is not the law on
   * that day, as {@link #divergences(Language)} finds it.
   */
  public List<Divergence> divergences(LocalDate day, Language language) {
    return divergences(language).stream().filter(each -> each.on(day)).toList();
  }

  /**
   * Returns each period in which the text of the versions in {@code language} is not the law.
   *
   * <ul>
   *   <li>The text is early while the act is in force but does not apply yet. For example, 2016/679
   *       is in force from 24 May 2016 and applies from 25 May 2018.
   *   <li>The text is late while an amending act applies but no version includes it. For example,
   *       2017/815 applies from 1 June 2017, and the version of 2015/1998 of 7 June 2017 is the
   *       first to include it.
   * </ul>
   *
   * <p>An amending act that does not change the text in {@code language} makes no text late. For
   * example, 2017/837 corrects only the Swedish and the Polish text of 2015/1998. An act whose date
   * Cellar does not know is left out. The early text is first, and then the late texts in the order
   * in which the versions include the acts.
   */
  public List<Divergence> divergences(Language language) {
    var versions = timeline(language).map(Timeline::versions).orElseGet(List::of);
    var divergences = new ArrayList<Divergence>();
    if (!versions.isEmpty()) {
      var first = versions.getFirst();
      appliesFrom(id)
          .filter(from -> Objects.requireNonNull(from.date()).isAfter(first.date()))
          .ifPresent(from -> divergences.add(new Divergence(Kind.EARLY, id, from, first)));
    }
    var included = new HashSet<ActIdentifier>();
    for (var each : versions) {
      for (var act : each.integrates()) {
        if (included.add(act) && changes(act, language)) {
          appliesFrom(act)
              .filter(from -> Objects.requireNonNull(from.date()).isBefore(each.date()))
              .ifPresent(from -> divergences.add(new Divergence(Kind.LATE, act, from, each)));
        }
      }
    }
    return divergences;
  }

  /**
   * Returns the date from which the whole of {@code act} applies. {@code act} is this act or an act
   * that its versions integrate.
   *
   * <ul>
   *   <li>It is the act's date of application or of taking effect that is not for a part.
   *   <li>An act that has no such date applies from its entry into force.
   *   <li>A date for a part is an exception for that part. It does not move the date of the whole
   *       act. For example, 2021/255 enters into force on 11 March 2021, and only four of its
   *       points apply from later dates. So it applies from 11 March 2021.
   * </ul>
   *
   * <p>Returns empty if Cellar does not know the date. For example, 2019/413 applies from a date
   * that its Article 3 sets by an event.
   */
  public Optional<ActDate> appliesFrom(ActIdentifier act) {
    var whole = datesOf(act).stream().filter(date -> !date.partial()).toList();
    var applying =
        whole.stream().filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE).toList();
    var dates = applying.isEmpty() ? whole : applying;
    if (dates.stream().anyMatch(date -> date.date() == null)) {
      return Optional.empty();
    }
    return dates.stream().min(Comparator.comparing(date -> Objects.requireNonNull(date.date())));
  }

  /**
   * Returns whether {@code amending} changes the text in {@code language}. An act for which Cellar
   * lists no amended location is taken to change every language.
   */
  private boolean changes(ActIdentifier amending, Language language) {
    var own = amendments.stream().filter(each -> each.act().equals(amending)).toList();
    return own.isEmpty() || own.stream().anyMatch(each -> each.changes(language));
  }
}
