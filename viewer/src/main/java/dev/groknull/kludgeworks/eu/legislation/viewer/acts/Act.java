// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts;

import java.time.LocalDate;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import org.jspecify.annotations.Nullable;

/**
 * An act: a work in Cellar, identified by its CELEX and its ELI, with its timeline.
 *
 * @param id the act's CELEX
 * @param eli its ELI; older acts may have none
 * @param type its resource type, the code of the EU authority table (REG_IMPL, DIR, DEC)
 * @param dateOfDocument the date it was adopted or signed
 * @param inForce whether it is in force, as Cellar says
 * @param endOfValidity the day it ceases to apply; none while it is open-ended
 * @param timeline its versions
 */
@AggregateRoot
public record Act(
    @Identity ActIdentifier id,
    @Nullable Eli eli,
    String type,
    @Nullable LocalDate dateOfDocument,
    boolean inForce,
    @Nullable LocalDate endOfValidity,
    Timeline timeline) {}
