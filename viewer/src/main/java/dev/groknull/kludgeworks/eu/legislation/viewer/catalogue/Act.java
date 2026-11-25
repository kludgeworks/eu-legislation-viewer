// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * <p>It holds what Cellar says of the act, and tells how it and the acts that amend it are cited,
 * and which of Cellar's amendments a comparison of two versions counts. Its dates are a value of
 * their own, {@link ActDates}, which tells from when an act applies and its state on a day. What
 * the rest of its data tells is in classes of its own, each for one area of the rules:
 *
 * <ul>
 *   <li>{@link TheHistoryOfAnAct}: its amending acts, their changes and its corrigenda.
 *   <li>{@link ProvisionHistory}: the acts that changed one provision.
 *   <li>{@link LawOnADay}: the version of a day, and where its text is not the law.
 * </ul>
 *
 * @param id the act's CELEX
 * @param eli its ELI. Older acts may have none.
 * @param type its resource type, for example REG_IMPL, DIR or DEC
 * @param titles its title in each official language that Cellar has it in, as the Official Journal
 *     printed it
 * @param dateOfDocument the date it was adopted or signed
 * @param inForce whether it is in force, as Cellar says
 * @param endOfValidity the day it ceases to apply, or null while it is open-ended
 * @param repealedBy the acts that repeal it, as Cellar says. Empty if none does.
 * @param timeline its versions
 * @param dates its own dates and the dates of the acts that its versions integrate or that amend
 *     it. They are of this act: their {@code id} is the act's.
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
    List<ActIdentifier> repealedBy,
    Timeline timeline,
    ActDates dates,
    List<Amendment> amendments,
    Map<ActIdentifier, Publication> publications) {

  public Act {
    publications = Map.copyOf(publications);
    titles = Map.copyOf(titles);
    if (!dates.id().equals(id)) {
      throw new IllegalArgumentException("The dates of " + dates.id() + " are not of " + id);
    }
    amendments = List.copyOf(amendments);
    repealedBy = List.copyOf(repealedBy);
  }

  /** Returns its title in {@code language}, or empty if Cellar has no title in that language. */
  public Optional<String> title(Language language) {
    return Optional.ofNullable(titles.get(language));
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
        .map(publication -> {
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
   *   <li>A deletion or a repeal is placed at an occurrence of the earlier text, or else at one of
   *       the later text. {@code earlier} holds the eIds of the earlier text's occurrences, in
   *       document order. The later text has the place of a deletion where it keeps the deleted
   *       occurrence, and the earlier text does not have it where it came before the occurrence.
   *       For example, 2021/255 repeals point 6.8.5.5 of 2015/1998, which came in after the text of
   *       15 November 2015: in the comparison of that text with the text of 15 March 2021, the
   *       repeal is at the later text's kept deletion. The two lists hold kept deletions too.
   *   <li>An amendment whose location is in neither text is counted as unplaced.
   * </ul>
   */
  @BusinessRule(
      group = RuleGroup.WHO_MADE_A_CHANGE,
      name = "cellars-amendments-that-count",
      revision = 2)
  public Amended amended(
      Version from, Version to, List<String> earlier, List<String> later, Language language) {
    var between = integratedBetween(from, to);
    var before = TextOccurrences.of(earlier);
    var after = TextOccurrences.of(later);
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
      var found =
          amendment.role().removes()
              ? amendment.location().in(before).or(() -> amendment.location().in(after))
              : amendment.location().in(after);
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
}
