// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import dev.groknull.kludgeworks.eu.legislation.viewer.broadcast.Broadcast;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.PreparationChanged;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The preparation of an act in a language, in the API: the progress of the text of each of its
 * versions in the language, its changes as server-sent events, and trying again.
 *
 * <p>Reading a preparation asks for nothing. A request for a text starts the preparation ({@link
 * ApiTextController}).
 */
@RestController
class ApiPreparationController {
  private static final Logger LOG = LoggerFactory.getLogger(ApiPreparationController.class);

  private static final String PREPARATION = ApiActResources.ACT + "/preparations/{language}";

  private final PreparationStreams streams = new PreparationStreams(this::send);
  private final Acts acts;
  private final Catalogued catalogued;
  private final Texts texts;

  ApiPreparationController(Acts acts, Catalogued catalogued, Texts texts) {
    this.acts = acts;
    this.catalogued = catalogued;
    this.texts = texts;
  }

  /**
   * Returns the preparation of the act in the language {@code language}, for example {@code eng}:
   *
   * <ul>
   *   <li>{@code item}: the progress of the text of each version in the language, embedded, in date
   *       order. A text that is ready has {@code av:text}.
   *   <li>{@code up}: the act.
   *   <li>{@code av:events}: its changes, as server-sent events.
   *   <li>{@code av:retry}: tries again the texts that failed in a way that trying again may help.
   *       It is there only while such a text exists.
   * </ul>
   */
  @GetMapping(
      path = PREPARATION,
      produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
  RepresentationModel<?> preparation(
      @PathVariable ActIdentifier celex, @PathVariable String language) {
    var act = found(celex);
    return model(act, texts.preparation(act, in(act, language)));
  }

  /**
   * Tries again the texts that failed in a way that trying again may help, and answers 202 with the
   * preparation. Answers 409 if no such text exists.
   */
  @PostMapping(
      path = PREPARATION,
      produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
  ResponseEntity<RepresentationModel<?>> retry(
      @PathVariable ActIdentifier celex, @PathVariable String language) {
    var act = found(celex);
    var in = in(act, language);
    if (!retryable(texts.preparation(act, in))) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "No text failed in a way that trying again may help.");
    }
    texts.retry(act, in);
    LOG.info("Trying again the texts of {} in {} that failed, for the API", act.id(), in.code());
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(model(act, texts.preparation(act, in)));
  }

  /**
   * Returns the changes of the preparation, as server-sent events. Each event {@code preparation}
   * has the progress of the preparation, as JSON without links: when it started and settled, and
   * how many texts are ready, failed, being built and ahead in the queue. The client reads the
   * preparation for the links. When every text is built or failed, the event {@code settled} has
   * the progress, and the stream ends. The stream sends the preparation when the client connects,
   * and again each time it changes. It also ends after a while, and then the client connects again.
   */
  @GetMapping(path = PREPARATION + "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter events(@PathVariable ActIdentifier celex, @PathVariable String language) {
    var act = found(celex);
    return streams.open(act.id(), in(act, language));
  }

  /** Sends the preparation that changed to each client that follows it. */
  @EventListener
  void on(Broadcast<PreparationChanged> broadcast) {
    streams.changed(broadcast.event());
  }

  /** Returns a link to the preparation of the act in the language, {@code av:preparation}. */
  static Link to(ActIdentifier celex, Language language) {
    return linkTo(
            methodOn(ApiPreparationController.class)
                .preparation(celex, ApiActResources.code(language.code())))
        .withRel(Relation.PREPARATION.rel());
  }

  /** Returns the preparation as the API shows it. */
  static RepresentationModel<?> model(Act act, Preparation preparation) {
    var celex = act.id();
    var language = ApiActResources.code(preparation.language().code());
    var model =
        HalModelBuilder.halModelOf(PreparationModel.of(preparation))
            .embed(
                preparation.texts().entrySet().stream()
                    .map(each ->
                        progress(celex, preparation.language(), each.getKey(), each.getValue()))
                    .toList(),
                IanaLinkRelations.ITEM)
            .link(to(celex, preparation.language()).withSelfRel())
            .link(ApiActResources.up(celex))
            .link(
                linkTo(methodOn(ApiPreparationController.class).events(celex, language))
                    .withRel(Relation.EVENTS.rel()));
    if (retryable(preparation)) {
      model.link(to(celex, preparation.language()).withRel(Relation.RETRY.rel()));
    }
    return model.build();
  }

  /** Returns a failure as the API writes it. For example, {@code no-formex}. */
  static String code(Failure failure) {
    return failure.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  private static EntityModel<Progress> progress(
      ActIdentifier celex, Language language, Version version, Text text) {
    var model = EntityModel.of(Progress.of(version, text));
    return switch (text) {
      case Text.Ready _ ->
          model.add(ApiTextController.to(celex, version, language, Relation.TEXT.rel()));
      case Text.Waiting _, Text.Converting _, Text.Failed _ -> model;
    };
  }

  private static boolean retryable(Preparation preparation) {
    return preparation.texts().values().stream()
        .anyMatch(text ->
            switch (text) {
              case Text.Failed failed -> failed.failure().retryable();
              case Text.Waiting _, Text.Converting _, Text.Ready _ -> false;
            });
  }

  private Act found(ActIdentifier celex) {
    return catalogued.api(celex);
  }

  /** Returns the language of the path, or throws not found if no version of the act is in it. */
  private static Language in(Act act, String code) {
    var language = Requests.language(code);
    if (act.timeline().in(language).isEmpty()) {
      throw Requests.notFound();
    }
    return language;
  }

  /**
   * Sends the progress of the preparation, as the event {@code preparation}, or {@code settled} if
   * it is settled.
   *
   * @return whether the preparation is settled
   */
  private boolean send(SseEmitter emitter, ActIdentifier celex, Language language)
      throws IOException {
    var preparation = texts.preparation(acts.find(celex).orElseThrow(), language);
    emitter.send(
        SseEmitter.event()
            .name(preparation.settled() ? "settled" : "preparation")
            .data(PreparationModel.of(preparation), MediaType.APPLICATION_JSON));
    return preparation.settled();
  }

  /**
   * A preparation, as the API shows it.
   *
   * @param language the language, by the EU's code. For example, {@code eng}.
   * @param started when the act was first asked for in the language
   * @param settledAt when its last text was built or failed, or null while it is being prepared
   * @param ready how many texts are built
   * @param failed how many texts failed
   * @param converting how many texts are being built now
   * @param ahead about how many texts of all acts are ahead of its texts in the queue, while none
   *     of its texts is being built
   */
  record PreparationModel(
      String language,
      Instant started,
      @Nullable Instant settledAt,
      int ready,
      int failed,
      int converting,
      int ahead) {

    static PreparationModel of(Preparation preparation) {
      return new PreparationModel(
          ApiActResources.code(preparation.language().code()),
          preparation.started(),
          preparation.settledAt(),
          preparation.ready(),
          preparation.failed(),
          preparation.running(),
          preparation.ahead());
    }
  }

  /**
   * The progress of the text of a version, as the API shows it.
   *
   * @param version where the version is among the versions: {@code oj}, or its date
   * @param celex the CELEX number of the document that holds the text
   * @param progress {@code waiting}, {@code converting}, {@code ready} or {@code failed}
   * @param attempt the attempt it is on, or the last attempt that failed while it waits. It is 0
   *     before the first attempt and once it is settled.
   * @param failure why it failed, as on the problem {@code text-failed}. Null unless it failed.
   * @param retryable whether trying again may help. Null unless it failed.
   */
  record Progress(
      String version,
      String celex,
      String progress,
      int attempt,
      @Nullable String failure,
      @Nullable Boolean retryable) {

    static Progress of(Version version, Text text) {
      var key = ApiActResources.key(version);
      return switch (text) {
        case Text.Waiting(var attempt) ->
            new Progress(key, version.celex(), "waiting", attempt, null, null);
        case Text.Converting(var attempt) ->
            new Progress(key, version.celex(), "converting", attempt, null, null);
        case Text.Ready _ -> new Progress(key, version.celex(), "ready", 0, null, null);
        case Text.Failed failed ->
            new Progress(
                key,
                version.celex(),
                "failed",
                0,
                code(failed.failure()),
                failed.failure().retryable());
      };
    }
  }
}
