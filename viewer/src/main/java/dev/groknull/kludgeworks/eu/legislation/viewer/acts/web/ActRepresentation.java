// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ResourceType;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * An act's own properties in its resource; its versions are embedded beside them, and its links
 * added.
 *
 * @param celex its CELEX
 * @param eli its ELI
 * @param type its resource type
 * @param dateOfDocument the date it was adopted or signed
 * @param inForce whether it is in force, as Cellar says
 * @param endOfValidity the day it ceases to apply; none while it is open-ended
 */
record ActRepresentation(
    ActIdentifier celex,
    @Nullable Eli eli,
    ResourceType type,
    @Nullable LocalDate dateOfDocument,
    boolean inForce,
    @Nullable LocalDate endOfValidity) {

  static ActRepresentation of(Act act) {
    return new ActRepresentation(
        act.id(), act.eli(), act.type(), act.dateOfDocument(), act.inForce(), act.endOfValidity());
  }
}
