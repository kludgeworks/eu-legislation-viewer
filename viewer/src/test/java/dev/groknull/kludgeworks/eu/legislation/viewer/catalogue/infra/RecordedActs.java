// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.SparqlResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Acts as Cellar has them, from its recorded answers (viewer/src/test/resources/acts, see SOURCE):
 * 2015/1998 (32015R1998) and 185/2010 (32010R0185), read as CellarActs reads Cellar's.
 */
public final class RecordedActs {
  private RecordedActs() {}

  /** The act {@code id}, from its recorded answers to CellarActs's queries. */
  public static Act act(ActIdentifier id) {
    return CellarActs.act(
            id,
            answer("act-" + id.celex() + ".json"),
            answer("versions-" + id.celex() + ".json"),
            answer("languages-" + id.celex() + ".json"),
            answer("dates-" + id.celex() + ".json"),
            answer("titles-" + id.celex() + ".json"),
            answer("amendments-" + id.celex() + ".json"))
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
