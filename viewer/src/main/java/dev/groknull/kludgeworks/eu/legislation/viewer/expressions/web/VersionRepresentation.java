// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import java.time.LocalDate;
import java.util.List;

/**
 * A version's own properties in its resource, as the act embeds them; its links are added.
 *
 * @param date the day it applies from
 * @param kind the original text, or a consolidated version
 * @param celex the document holding its text
 * @param interim a consolidated version published without a PDF, which may still be revised
 * @param integrates the amending acts and corrigenda Cellar lists as consolidated at this step
 */
record VersionRepresentation(
    LocalDate date,
    Version.Kind kind,
    String celex,
    boolean interim,
    List<ActIdentifier> integrates) {

  static VersionRepresentation of(Version version) {
    return new VersionRepresentation(
        version.date(), version.kind(), version.celex(), version.interim(), version.integrates());
  }
}
