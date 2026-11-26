// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.ACT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.ACTS;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActResources.code;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.jackson.JacksonMixin;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * An act in the API, and the lookup of an act by its CELEX number or its ELI. Its versions are
 * {@link ApiVersionController}, the law on a day {@link ApiLawController}, and its amendments
 * {@link ApiAmendmentsController}. What they share is {@link ApiActResources}.
 */
@RestController
@RequestMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class ApiActController {
  /**
   * The CELEX number of a consolidated version: sector 0, which no act has. For example, {@code
   * 02015R1998-20260322}.
   */
  private static final Pattern CONSOLIDATED = Pattern.compile("0\\S+");

  /**
   * How long a client may keep the answer of a lookup: the act of an identifier does not change.
   */
  private static final CacheControl LOOKED_UP = CacheControl.maxAge(Duration.ofHours(1));

  private final Acts acts;
  private final Catalogued catalogued;
  private final ActModelAssembler assembler;

  ApiActController(Acts acts, Catalogued catalogued, ActModelAssembler assembler) {
    this.acts = acts;
    this.catalogued = catalogued;
    this.assembler = assembler;
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
    return lookedUp(ApiActResources.to(act.id(), version, IanaLinkRelations.SELF));
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
    return assembler.toModel(catalogued.api(celex));
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
}
