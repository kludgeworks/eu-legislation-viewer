// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;

/**
 * What the resources of an act in the API share: their paths, the links between them, the key of a
 * version, and the models that more than one of them shows. An act is at its CELEX number, because
 * not every act has an ELI. A version is at {@code oj}, for the original text, or at the date of
 * its consolidation, as an ELI names them.
 */
final class ApiActResources {
  /** The path of the acts. */
  static final String ACTS = ApiRootController.ROOT + "/acts";

  /** The path of an act. */
  static final String ACT = ACTS + "/{celex}";

  /** The path of the versions of an act. */
  static final String VERSIONS = ACT + "/versions";

  /** The path of a version of an act. */
  static final String VERSION = VERSIONS + "/{key}";

  /** The key of the original text among the versions, as in an ELI. */
  private static final String ORIGINAL = "oj";

  private ApiActResources() {}

  /** Returns a link to the version. */
  static Link to(ActIdentifier celex, Version version, LinkRelation relation) {
    return linkTo(methodOn(ApiVersionController.class).version(celex, key(version)))
        .withRel(relation);
  }

  /** Returns a link to the act, {@code up}. */
  static Link up(ActIdentifier celex) {
    return linkTo(methodOn(ApiActController.class).act(celex)).withRel(IanaLinkRelations.UP);
  }

  /** Returns a link to the versions of the act, {@code version-history}. */
  static Link versionHistory(ActIdentifier celex) {
    return linkTo(methodOn(ApiVersionController.class).versions(celex))
        .withRel(IanaLinkRelations.VERSION_HISTORY);
  }

  /** Returns the link to the version that applies today, if one does. */
  static Optional<Link> latest(Act act, LocalDate today) {
    var applying = act.timeline().allApplicableOn(today);
    return applying.isEmpty()
        ? Optional.empty()
        : Optional.of(to(act.id(), applying.getLast(), IanaLinkRelations.LATEST_VERSION));
  }

  /** Returns a link to the preparation of the act in the language, {@code av:preparation}. */
  static Link preparation(ActIdentifier celex, Language language) {
    return linkTo(
            methodOn(ApiPreparationController.class).preparation(celex, code(language.code())))
        .withRel(Relation.PREPARATION.rel());
  }

  /** Returns the version as a resource with its own address. */
  static EntityModel<VersionModel> summary(ActIdentifier celex, Version version) {
    return EntityModel.of(VersionModel.of(version)).add(to(celex, version, IanaLinkRelations.SELF));
  }

  /** Returns where a version is among the versions of its act: {@code oj}, or its date. */
  static String key(Version version) {
    return version.kind() == Version.Kind.ORIGINAL ? ORIGINAL : version.date().toString();
  }

  /** Returns the version at {@code key}, or throws not found. */
  static Version at(List<Version> versions, String key) {
    return versions.stream()
        .filter(each -> key(each).equals(key))
        .findFirst()
        .orElseThrow(Requests::notFound);
  }

  /** Returns a code as the API writes it, in lower case. For example, {@code eng}. */
  static String code(String code) {
    return code.toLowerCase(Locale.ROOT);
  }

  /**
   * A version of an act, as the API shows it.
   *
   * @param version where it is among the versions: {@code oj}, or its date
   * @param kind {@code original} or {@code consolidated}
   * @param date the day it applies from
   * @param celex the CELEX number of the document that holds its text
   * @param interim whether it may still be revised
   * @param integrates the CELEX numbers of the acts that the Publications Office lists as
   *     integrated in this version
   * @param languages the languages that the viewer can show its text in, by the EU's codes
   */
  record VersionModel(
      String version,
      String kind,
      LocalDate date,
      String celex,
      boolean interim,
      List<String> integrates,
      List<String> languages) {

    static VersionModel of(Version version) {
      return new VersionModel(
          key(version),
          code(version.kind().name()),
          version.date(),
          version.celex(),
          version.interim(),
          version.integrates().stream().map(ActIdentifier::celex).toList(),
          version.languages().stream().map(language -> code(language.code())).toList());
    }
  }

  /**
   * One of an act's own dates, as the API shows it.
   *
   * @param date the day, or null where Cellar has a placeholder or an impossible date
   * @param kind {@code entry_into_force}, {@code application} or {@code taking_effect}
   * @param forPart whether it is for a part of the act only
   * @param clause what sets it, as Cellar writes it. For example, {@code DATPUB +20 V ART 2}.
   * @param source which source settled {@code date}: {@code cellar}, {@code clause_and_journal},
   *     {@code text} or {@code not_confirmed}
   */
  record DateModel(
      @Nullable LocalDate date, String kind, boolean forPart, String clause, String source) {

    static DateModel of(ActDate date) {
      return new DateModel(
          date.date(),
          code(date.kind().name()),
          date.forPart(),
          date.clause().text(),
          code(date.source().name()));
    }
  }
}
