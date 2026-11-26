// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.CataloguedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.FirstPage;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Histories;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Laws;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Lookups;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ProvisionOnADay;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Reading;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Readings;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Today;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Gives what the Read page shows. It gets what the rules need and that a request does not have: the
 * act, today, and the texts that are built. It decides nothing about the law.
 */
@Service
public class ReadService implements Readings {
  private static final Logger LOG = LoggerFactory.getLogger(ReadService.class);

  private final Lookups lookups;
  private final Texts texts;
  private final Laws laws;
  private final Histories histories;
  private final Content content;
  private final Today today;

  public ReadService(
      Lookups lookups, Texts texts, Laws laws, Histories histories, Content content, Today today) {
    this.lookups = lookups;
    this.texts = texts;
    this.laws = laws;
    this.histories = histories;
    this.content = content;
    this.today = today;
  }

  @Override
  public Optional<Reading> of(Eli eli, Language language, LocalDate day) {
    var act = CataloguedActs.act(lookups, eli);
    var overTime = histories.overTime(act, language);
    if (act.timeline().startsAfter(day)) {
      return Optional.of(new Reading.BeforeTheAct(overTime, act.timeline().first()));
    }
    var applies = laws.versionOn(act, day, language);
    if (applies.isEmpty()) {
      return Optional.empty();
    }
    var version = applies.get();
    if (act.timeline().in(language).isEmpty()) {
      return Optional.of(
          new Reading.NotInTheLanguage(
              overTime, version, laws.of(act, language, Map.of()).on(day), today.today()));
    }
    var preparation = texts.prepare(act, language);
    if (preparation.preparing()) {
      return Optional.of(new Reading.Preparing(overTime, version, preparation, today.now()));
    }
    var tags = texts.tags(preparation);
    return Optional.of(
        new Reading.Prepared(
            overTime,
            version,
            preparation,
            laws.of(act, language, tags).on(day),
            named(act, version, language, preparation),
            tags.get(version),
            preparation.texts().get(version) instanceof Text.Ready ready
                ? content.outline(ready.text()).orElseGet(Outline::empty)
                : null,
            histories.provisionsWithAHistory(overTime, version, tags),
            today.today()));
  }

  @Override
  public FirstPage first(Eli eli, Language language, @Nullable LocalDate on) {
    var amended =
        !histories.overTime(CataloguedActs.act(lookups, eli), language).amendingActs().isEmpty();
    if (on != null) {
      return new FirstPage.Read(on);
    }
    return amended ? new FirstPage.History() : new FirstPage.Read(today.today());
  }

  @Override
  public Optional<ProvisionOnADay> provision(
      Eli eli, Language language, LocalDate day, String occurrence) {
    var act = CataloguedActs.act(lookups, eli);
    if (act.timeline().startsAfter(day)) {
      return Optional.empty();
    }
    var tags = texts.tags(texts.preparation(act, language));
    return laws.versionOn(act, day, language)
        .flatMap(version ->
            histories.provision(histories.overTime(act, language), version, occurrence, day, tags));
  }

  @Override
  public void retry(Eli eli, Language language) {
    texts.retry(CataloguedActs.act(lookups, eli), language);
  }

  /**
   * Returns the acts that the text of {@code version} names, but the text of the version before it
   * does not name. The text names these acts in the list of integrated acts in its Formex. That
   * list is kept in the lifecycle of the text.
   *
   * <p>Returns none in these cases:
   *
   * <ul>
   *   <li>The version is the original version.
   *   <li>The Publications Office lists acts that the version integrates.
   *   <li>There is no version before it.
   *   <li>Either text is not built.
   * </ul>
   */
  private List<ActIdentifier> named(
      Act act, Version version, Language language, Preparation preparation) {
    if (version.kind() == Version.Kind.ORIGINAL || !version.integrates().isEmpty()) {
      return List.of();
    }
    var before = act.timeline().in(language).flatMap(t -> t.predecessor(version));
    if (before.isEmpty()
        || !(preparation.texts().get(version) instanceof Text.Ready text)
        || !(preparation.texts().get(before.get()) instanceof Text.Ready earlier)) {
      return List.of();
    }
    var named = new ArrayList<>(listed(text.text()));
    named.removeAll(listed(earlier.text()));
    LOG.info(
        "{} lists no amending act; its text names {} more than {}'s",
        version.celex(),
        named,
        before.get().celex());
    return named;
  }

  /**
   * Returns the acts that {@code text} lists as integrated. These are the amendments in its
   * lifecycle.
   */
  private List<ActIdentifier> listed(TextId text) {
    return content.read(text).stream()
        .flatMap(element -> element.children().stream())
        .filter(child -> child.name().equals("lifecycle"))
        .flatMap(lifecycle -> lifecycle.children().stream())
        .filter(event -> "amendment".equals(event.attribute("type")))
        .map(event -> event.attribute("act"))
        .filter(Objects::nonNull)
        .filter(ActIdentifier::isCelex)
        .map(ActIdentifier::new)
        .distinct()
        .toList();
  }
}
