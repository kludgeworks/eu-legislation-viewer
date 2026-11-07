// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;

/** An act could not be prepared in a language. None of the texts of its versions could be built. */
@DomainEvent
public record PreparationFailed(ActIdentifier act, Language language) {}
