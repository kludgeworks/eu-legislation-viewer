// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * An act is prepared in a language. The text of every version is settled, and at least one of them
 * is built.
 *
 * @param ready how many of its versions' texts are built
 * @param failed how many of its versions' texts failed
 */
@DomainEvent
public record PreparationCompleted(ActIdentifier act, Language language, int ready, int failed) {}
