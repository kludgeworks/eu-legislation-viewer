// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.eu.legislation.cellar.SparqlResult;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Gives acts as Cellar has them, from its recorded answers. The answers are in {@code
 * viewer/src/test/resources/acts}, and the {@code SOURCE} file there lists them. The acts are
 * 2015/1998 (32015R1998) and 185/2010 (32010R0185). This class reads them as {@link CellarActs}
 * reads Cellar's live answers.
 */
public final class RecordedActs {
  private RecordedActs() {}

  /** The act {@code id}, from its recorded answers to the queries of {@link CellarActs}. */
  public static Act act(ActIdentifier id) {
    return CellarActs.act(
            id,
            answer("act-" + id.celex() + ".json"),
            answer("versions-" + id.celex() + ".json"),
            answer("languages-" + id.celex() + ".json"),
            answer("dates-" + id.celex() + ".json"),
            answer("titles-" + id.celex() + ".json"),
            answer("amendments-" + id.celex() + ".json"),
            answer("publications-" + id.celex() + ".json"))
        .orElseThrow();
  }

  /** The recorded answer {@code name}. */
  public static SparqlResult answer(String name) {
    var resource = "/acts/" + name;
    try (var in =
        Objects.requireNonNull(RecordedActs.class.getResourceAsStream(resource), resource)) {
      return SparqlResult.parse(in.readAllBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
