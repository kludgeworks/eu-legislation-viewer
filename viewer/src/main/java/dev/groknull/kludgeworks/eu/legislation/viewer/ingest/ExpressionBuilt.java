// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * The legal text of an expression is stored. The content module has it, by the CELEX and the
 * language.
 */
@DomainEvent
public record ExpressionBuilt(String celex, Language language) {}
