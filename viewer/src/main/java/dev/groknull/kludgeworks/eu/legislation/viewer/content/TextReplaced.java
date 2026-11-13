// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;
import org.springframework.modulith.events.Externalized;

/**
 * A version's text in a language was stored again with another content, for example because the
 * Publications Office republished its Formex, or by a new release of the converter. The old text is
 * deleted in the same transaction. A module that keeps something by the old text's identity deletes
 * it.
 *
 * <p>It is broadcast to every instance on the {@code text} stream, so that the pages open on the
 * old text can tell their readers.
 *
 * @param replaced the identity of the old text, which is no longer stored
 * @param text the identity of the new text
 * @param celex the version of the text
 * @param language the language of the text
 */
@DomainEvent
@Externalized("text")
public record TextReplaced(TextId replaced, TextId text, String celex, Language language) {}
