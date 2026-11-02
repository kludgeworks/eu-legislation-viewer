// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * An expression's legal text is stored: everything a listener needs to point the expression at it.
 *
 * @param root the text's root in the content module
 * @param converter the version of the converter that made it
 */
@DomainEvent
public record ExpressionBuilt(
    String celex, Language language, ContentHash root, String converter) {}
