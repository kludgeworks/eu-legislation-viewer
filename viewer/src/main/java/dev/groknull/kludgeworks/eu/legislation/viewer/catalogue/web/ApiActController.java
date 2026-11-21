// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Divergence;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.jackson.JacksonMixin;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.util.NameTransformer;

/**
 * The resources of an act in the API: the act, its versions, and the law on a date. An act is at
 * its CELEX number, because not every act has an ELI. A version is at {@code oj}, for the original
 * text, or at the date of its consolidation, as an ELI names them.
 */
@RestController
@RequestMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class ApiActController {
  private static final String ACTS = ApiRootController.ROOT + "/acts";

  /** The path of an act. */
  static final String ACT = ACTS + "/{celex}";

  /** The path of a version of an act. */
  static final String VERSION = ACT + "/versions/{key}";

  /**
   * The CELEX number of a consolidated version: sector 0, which no act has. For example, {@code
   * 02015R1998-20260322}.
   */
  private static final Pattern CONSOLIDATED = Pattern.compile("0\\S+");

  /** The key of the original text among the versions, as in an ELI. */
  private static final String ORIGINAL = "oj";

  /**
   * RFC 5829's relation of the version after a version. Spring HATEOAS lists it under another name.
   */
  private static final LinkRelation SUCCESSOR_VERSION = LinkRelation.of("successor-version");

  /**
   * How long a client may keep the answer of a lookup: the act of an identifier does not change.
   */
  private static final CacheControl LOOKED_UP = CacheControl.maxAge(Duration.ofHours(1));

  private final Acts acts;
  private final Catalogued catalogued;
  private final Texts texts;
  private final Clock clock;

  ApiActController(Acts acts, Catalogued catalogued, Texts texts, Clock clock) {
    this.acts = acts;
    this.catalogued = catalogued;
    this.texts = texts;
    this.clock = clock;
  }

  /**
   * Redirects to what the CELEX number {@code celex} names:
   *
   * <ul>
   *   <li>the act, for the number of an act. For example, {@code 32015R1998}.
   *   <li>the version, for the number of a consolidated version. For example, {@code
   *       02015R1998-20260322}.
   * </ul>
   *
   * <p>Returns 404 if nothing has the number, and 400 if it cannot be a CELEX number.
   */
  @GetMapping(path = ACTS, params = "celex")
  ResponseEntity<Void> byCelex(@RequestParam String celex) {
    if (ActIdentifier.isCelex(celex)) {
      return lookedUp(catalogued.api(new ActIdentifier(celex)));
    }
    if (!CONSOLIDATED.matcher(celex).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a CELEX number: " + celex);
    }
    var act = acts.findByVersion(celex).orElseThrow(Requests::notFound);
    var version =
        act.timeline().versions().stream()
            .filter(each -> each.celex().equals(celex))
            .findFirst()
            .orElseThrow(Requests::notFound);
    return lookedUp(to(act.id(), version, IanaLinkRelations.SELF));
  }

  /**
   * Redirects to the act with the ELI {@code eli}. The ELI is its path, {@code reg_impl/2015/1998},
   * or its whole address, {@code http://data.europa.eu/eli/reg_impl/2015/1998/oj}. Returns 404 if
   * no act has it, and 400 if it cannot be an ELI.
   */
  @GetMapping(path = ACTS, params = "eli")
  ResponseEntity<Void> byEli(@RequestParam String eli) {
    Eli named;
    try {
      named = Eli.parse(eli).orElseGet(() -> new Eli(eli));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not an ELI: " + eli, e);
    }
    return lookedUp(catalogued.api(named));
  }

  private static ResponseEntity<Void> lookedUp(Act act) {
    return lookedUp(linkTo(methodOn(ApiActController.class).act(act.id())).withSelfRel());
  }

  private static ResponseEntity<Void> lookedUp(Link found) {
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(found.toUri())
        .cacheControl(LOOKED_UP)
        .build();
  }

  /**
   * Returns the act:
   *
   * <ul>
   *   <li>{@code version-history}: its versions.
   *   <li>{@code latest-version}: the version that applies today in Brussels. A version that
   *       applies only in the future is reached from it by {@code successor-version}. The link is
   *       absent before the first version applies.
   *   <li>{@code av:on}: the law on a date.
   *   <li>{@code av:amendments}: what other acts did to its provisions.
   * </ul>
   */
  @GetMapping(ACT)
  EntityModel<Act> act(@PathVariable ActIdentifier celex) {
    var act = found(celex);
    var self = linkTo(methodOn(ApiActController.class).act(celex)).withSelfRel();
    var model =
        EntityModel.of(act)
            .add(self)
            .add(
                linkTo(methodOn(ApiActController.class).versions(celex))
                    .withRel(IanaLinkRelations.VERSION_HISTORY));
    latest(act).ifPresent(model::add);
    return model
        .add(ApiRootController.template(self, "/on/{date}", Relation.ON))
        .add(
            linkTo(methodOn(ApiActController.class).amendments(celex))
                .withRel(Relation.AMENDMENTS.rel()));
  }

  /**
   * Returns the law on {@code date}: the day asked, the versions that apply on it, the day they
   * apply until, and where their text is not the law on the day, because it is late or early. More
   * than one version applies when the original text and a consolidation share a day, in different
   * languages.
   *
   * <p>A day before the first version returns 404, as a problem of the type {@link
   * ProblemType#BEFORE_FIRST_VERSION}. It has the day the act was adopted, the day its text first
   * applies, and the address of its first version. The day asked is never moved.
   */
  @GetMapping(ACT + "/on/{date}")
  RepresentationModel<?> on(
      @PathVariable ActIdentifier celex,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    var act = found(celex);
    var timeline = act.timeline();
    var applying = timeline.allApplicableOn(date);
    if (applying.isEmpty()) {
      throw beforeTheFirstVersion(act, date);
    }
    var divergences = divergences(act, applying, date);
    return HalModelBuilder.halModelOf(
            new LawModel(date, timeline.nextStartAfter(date).orElse(null), divergences))
        .embed(
            applying.stream().map(version -> summary(celex, version)).toList(),
            Relation.VERSION.rel())
        .link(linkTo(methodOn(ApiActController.class).on(celex, date)).withSelfRel())
        .link(up(celex))
        .build();
  }

  /**
   * Returns the divergences of {@code act} on {@code date}, each with the languages of the versions
   * {@code applying} whose text differs. An amending act can change the text of some languages
   * only. The marks of the texts that are built in a language tell where its text differs. A
   * language without built texts is compared act by act.
   */
  private List<DivergenceModel> divergences(Act act, List<Version> applying, LocalDate date) {
    var languages = new LinkedHashMap<Divergence, List<String>>();
    applying.stream()
        .flatMap(version -> version.languages().stream())
        .distinct()
        .sorted()
        .forEach(
            language ->
                act.divergences(date, language, texts.marks(texts.preparation(act, language)))
                    .forEach(
                        each ->
                            languages
                                .computeIfAbsent(each, key -> new ArrayList<>())
                                .add(code(language.code()))));
    return languages.entrySet().stream()
        .map(
            each -> {
              var divergence = each.getKey();
              var included = divergence.included();
              return new DivergenceModel(
                  code(divergence.kind().name()),
                  divergence.act().celex(),
                  act.cited(divergence.act()),
                  divergence.kind() == Divergence.Kind.UNCONFIRMED
                      ? null
                      : divergence.appliesFrom(),
                  included == null ? null : key(included),
                  divergence.places().stream()
                      .map(
                          place -> new PlaceModel(place.location().code(), place.location().name()))
                      .distinct()
                      .toList(),
                  each.getValue());
            })
        .toList();
  }

  /**
   * Returns the versions of the act, in date order:
   *
   * <ul>
   *   <li>{@code item}: each version, embedded.
   *   <li>{@code first}: the original text. The versions are a series, and they are not paged.
   *   <li>{@code latest-version}: as on the act.
   * </ul>
   */
  @GetMapping(ACT + "/versions")
  RepresentationModel<?> versions(@PathVariable ActIdentifier celex) {
    var act = found(celex);
    var model =
        HalModelBuilder.halModel()
            .embed(
                act.timeline().versions().stream().map(version -> summary(celex, version)).toList(),
                IanaLinkRelations.ITEM)
            .link(linkTo(methodOn(ApiActController.class).versions(celex)).withSelfRel())
            .link(up(celex))
            .link(to(celex, act.timeline().first(), IanaLinkRelations.FIRST));
    latest(act).ifPresent(model::link);
    return model.build();
  }

  /**
   * Returns the version at {@code key}: {@code oj} for the original text, or the date of a
   * consolidation. Returns 404 for any other key. Its neighbours are the versions before and after
   * it among all the versions of the act, in any language. It links to its text in each of its
   * languages, {@code av:text}, with the language in {@code hreflang}.
   */
  @GetMapping(VERSION)
  EntityModel<VersionModel> version(@PathVariable ActIdentifier celex, @PathVariable String key) {
    var versions = found(celex).timeline().versions();
    var version = at(versions, key);
    var at = versions.indexOf(version);
    var model =
        summary(celex, version)
            .add(up(celex))
            .add(
                linkTo(methodOn(ApiActController.class).versions(celex))
                    .withRel(IanaLinkRelations.VERSION_HISTORY));
    if (at > 0) {
      model.add(to(celex, versions.get(at - 1), IanaLinkRelations.PREDECESSOR_VERSION));
    }
    if (at < versions.size() - 1) {
      model.add(to(celex, versions.get(at + 1), SUCCESSOR_VERSION));
    }
    for (var language : version.languages()) {
      model.add(ApiTextController.to(celex, version, language, Relation.TEXT.rel()));
    }
    return model;
  }

  /** Returns the version at {@code key}, or throws not found. */
  static Version at(List<Version> versions, String key) {
    return versions.stream()
        .filter(each -> key(each).equals(key))
        .findFirst()
        .orElseThrow(Requests::notFound);
  }

  /**
   * Returns what other acts did to the provisions of the act, as Cellar annotates them: each
   * amendment with its amending act, where it acts, what it did, and Cellar's day for it. Cellar's
   * day can be stale where a later act moved a date of the amending act. Thus the representation
   * also has the own dates of each amending act, which Cellar keeps up to date.
   */
  @GetMapping(ACT + "/amendments")
  RepresentationModel<?> amendments(@PathVariable ActIdentifier celex) {
    var act = found(celex);
    var dates = new TreeMap<String, List<DateModel>>();
    act.amendingDates()
        .forEach(
            (amending, own) ->
                dates.put(amending.celex(), own.stream().map(DateModel::of).toList()));
    return HalModelBuilder.halModelOf(
            new AmendmentsModel(
                act.amendments().stream().map(each -> AmendmentModel.of(act, each)).toList(),
                dates))
        .link(linkTo(methodOn(ApiActController.class).amendments(celex)).withSelfRel())
        .link(up(celex))
        .build();
  }

  private Act found(ActIdentifier celex) {
    return catalogued.api(celex);
  }

  /** Returns the link to the version that applies today, if one does. */
  private Optional<Link> latest(Act act) {
    var applying = act.timeline().allApplicableOn(Timeline.today(clock));
    return applying.isEmpty()
        ? Optional.empty()
        : Optional.of(to(act.id(), applying.getLast(), IanaLinkRelations.LATEST_VERSION));
  }

  private static EntityModel<VersionModel> summary(ActIdentifier celex, Version version) {
    return EntityModel.of(VersionModel.of(version)).add(to(celex, version, IanaLinkRelations.SELF));
  }

  /** Returns a link to the version. */
  static Link to(ActIdentifier celex, Version version, LinkRelation relation) {
    return linkTo(methodOn(ApiActController.class).version(celex, key(version))).withRel(relation);
  }

  /** Returns a link to the act, {@code up}. */
  static Link up(ActIdentifier celex) {
    return linkTo(methodOn(ApiActController.class).act(celex)).withRel(IanaLinkRelations.UP);
  }

  /** Returns where a version is among the versions of its act: {@code oj}, or its date. */
  static String key(Version version) {
    return version.kind() == Version.Kind.ORIGINAL ? ORIGINAL : version.date().toString();
  }

  private static ErrorResponseException beforeTheFirstVersion(Act act, LocalDate date) {
    var first = act.timeline().first();
    var problem =
        ProblemType.BEFORE_FIRST_VERSION.problem(
            "The text of " + act.id() + " first applies on " + first.date() + ".");
    problem.setProperty("date", date);
    problem.setProperty("dateOfDocument", act.dateOfDocument());
    problem.setProperty("firstApplies", first.date());
    problem.setProperty("firstVersion", to(act.id(), first, IanaLinkRelations.FIRST).getHref());
    return new ErrorResponseException(HttpStatus.NOT_FOUND, problem, null);
  }

  /**
   * Has Jackson write an act as its model, {@link ActModel}. The act resource holds the act itself,
   * so that other modules can add their links to it with a {@code RepresentationModelProcessor} of
   * {@code EntityModel<Act>}.
   */
  @JacksonMixin(Act.class)
  @JsonSerialize(using = ActModel.Written.class)
  interface ActWrittenAsModel {}

  /**
   * An act, as the API shows it.
   *
   * @param celex its CELEX number
   * @param eli the path of its ELI, for example {@code reg_impl/2015/1998}. Older acts have none.
   * @param type its resource type, as Cellar's code. For example, {@code REG_IMPL}.
   * @param titles its title in each language that Cellar has it in, by the EU's code of the
   *     language. For example, {@code eng}.
   * @param dateOfDocument the date it was adopted or signed
   * @param inForce whether it is in force, as Cellar says
   * @param endOfValidity the day it ceases to apply, or null while it is open-ended
   */
  record ActModel(
      String celex,
      @Nullable String eli,
      String type,
      Map<String, String> titles,
      @Nullable LocalDate dateOfDocument,
      boolean inForce,
      @Nullable LocalDate endOfValidity) {

    /**
     * Writes an act as its model, with the model's own serializer. A resource unwraps the act into
     * its own object, so the serializer unwraps the model when it is asked to.
     */
    static final class Written extends ValueSerializer<Act> {
      private final @Nullable NameTransformer unwrapped;

      Written() {
        this(null);
      }

      private Written(@Nullable NameTransformer unwrapped) {
        this.unwrapped = unwrapped;
      }

      @Override
      public void serialize(Act act, JsonGenerator json, SerializationContext context) {
        var model = context.findValueSerializer(ActModel.class);
        if (unwrapped != null) {
          model = model.unwrappingSerializer(unwrapped);
        }
        model.serialize(ActModel.of(act), json, context);
      }

      @Override
      public ValueSerializer<Act> unwrappingSerializer(NameTransformer unwrapper) {
        return new Written(unwrapper);
      }

      @Override
      public boolean isUnwrappingSerializer() {
        return unwrapped != null;
      }
    }

    static ActModel of(Act act) {
      var titles = new TreeMap<String, String>();
      act.titles().forEach((language, title) -> titles.put(code(language.code()), title));
      var eli = act.eli();
      return new ActModel(
          act.id().celex(),
          eli == null ? null : eli.path(),
          act.type().code(),
          titles,
          act.dateOfDocument(),
          act.inForce(),
          act.endOfValidity());
    }
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
   *     consolidated at this step
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
   * The law on a date, as the API shows it.
   *
   * @param date the day asked
   * @param until the day the next version starts, or null if no version starts later
   * @param divergences where the text of the versions is not the law on the day
   */
  record LawModel(LocalDate date, @Nullable LocalDate until, List<DivergenceModel> divergences) {}

  /**
   * Where the text is not the law on a day, as the API shows it.
   *
   * @param kind {@code late}: the act applies, but the text does not include it. {@code early}: the
   *     text is included, but the act does not apply yet. {@code unconfirmed}: the act is in force,
   *     the text does not include it, and Cellar does not give the day from which it applies.
   * @param act the CELEX number of the act
   * @param cited how the Official Journal cites it. For example, {@code 2017/815}.
   * @param appliesFrom the day from which the act applies. Null if Cellar does not give it.
   * @param includedIn the first version that includes its text: {@code oj}, or its date. Null for a
   *     late text that no version includes yet.
   * @param places the provisions where the text differs. Empty where the whole act differs.
   * @param languages the languages whose text differs
   */
  record DivergenceModel(
      String kind,
      String act,
      String cited,
      @Nullable LocalDate appliesFrom,
      @Nullable String includedIn,
      List<PlaceModel> places,
      List<String> languages) {}

  /**
   * A provision where the text is not the law, as the API shows it.
   *
   * @param location where, in Cellar's codes, as in {@code amendments}. For example, {@code AN PO
   *     4.1.3.3}.
   * @param name the provision, as a page cites it. For example, {@code point 4.1.3.3}.
   */
  record PlaceModel(String location, String name) {}

  /**
   * What other acts did to the provisions of an act, as the API shows it.
   *
   * @param amendments each amendment, as Cellar annotates it
   * @param dates the own dates of each amending act, by its CELEX number
   */
  record AmendmentsModel(List<AmendmentModel> amendments, Map<String, List<DateModel>> dates) {}

  /**
   * An amendment of a provision, as the API shows it.
   *
   * @param act the CELEX number of the amending act
   * @param cited how the Official Journal cites the amending act. For example, {@code 2019/103}.
   * @param location where it acts, as Cellar codes it. For example, {@code AN PO 8.1.2.3 PTA (c)}.
   * @param role what it did: replacement, addition, deletion, repeal, amendment, extension,
   *     suspension, deferral or other
   * @param from the day it applies from, as Cellar has it, or null where Cellar has none
   * @param until the day it ends, for an amendment that lapses, or null for the others
   * @param languages the only languages it changes, by the EU's codes, for an act that corrects
   *     only some language versions. Empty if it changes all of them.
   */
  record AmendmentModel(
      String act,
      String cited,
      String location,
      String role,
      @Nullable LocalDate from,
      @Nullable LocalDate until,
      List<String> languages) {

    static AmendmentModel of(Act amended, Amendment amendment) {
      return new AmendmentModel(
          amendment.act().celex(),
          amended.cited(amendment.act()),
          amendment.location().code(),
          code(amendment.role().name()),
          amendment.from(),
          amendment.until(),
          amendment.languages().stream().map(language -> code(language.code())).sorted().toList());
    }
  }

  /**
   * One of an act's own dates, as the API shows it.
   *
   * @param date the day, or null where Cellar has a placeholder or an impossible date
   * @param kind {@code entry_into_force}, {@code application} or {@code taking_effect}
   * @param partial whether it is for part of the act only
   * @param clause what sets it, as Cellar writes it. For example, {@code DATPUB +20 V ART 2}.
   */
  record DateModel(@Nullable LocalDate date, String kind, boolean partial, String clause) {

    static DateModel of(ActDate date) {
      return new DateModel(
          date.date(), code(date.kind().name()), date.partial(), date.clause().text());
    }
  }

  /** Returns a code as the API writes it, in lower case. For example, {@code eng}. */
  static String code(String code) {
    return code.toLowerCase(Locale.ROOT);
  }
}
