// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;

/** An act couldn't be prepared in a language: none of its versions' texts could be built. */
@DomainEvent
public record PreparationFailed(ActIdentifier act, Language language) {}
