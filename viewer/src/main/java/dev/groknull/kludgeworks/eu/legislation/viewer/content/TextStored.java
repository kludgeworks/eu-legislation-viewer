// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import org.jmolecules.event.annotation.DomainEvent;

/**
 * A version's text in a language was stored for the first time.
 *
 * @param text the identity of the text
 */
@DomainEvent
public record TextStored(TextId text) {}
