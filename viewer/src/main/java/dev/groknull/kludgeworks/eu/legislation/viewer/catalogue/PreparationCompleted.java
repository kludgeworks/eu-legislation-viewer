// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * An act is prepared in a language: every version's text settled, at least one of them built.
 *
 * @param ready how many of its versions' texts are built
 * @param failed how many failed
 */
@DomainEvent
public record PreparationCompleted(ActIdentifier act, Language language, int ready, int failed) {}
