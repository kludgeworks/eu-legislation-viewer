// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * The text of a version in a language is asked for: ingest is to convert its Formex. Another module
 * publishes this, and ingest answers with its own events.
 *
 * <ul>
 *   <li>If ingest queues the conversion, a worker runs it. {@link ConversionStarted} follows, then
 *       {@link TextConverted} or {@link ConversionFailed}.
 *   <li>If the conversion is running or finished already, and the reason does not ask for it again,
 *       ingest tells where it stands, with the same events. Thus whoever asked comes to know the
 *       outcome, whenever it asks.
 * </ul>
 *
 * <p>Ingest defines the event, though others publish it: a module that listens to ingest's events
 * cannot be listened to by ingest in turn.
 *
 * @param celex the CELEX of the act for its original text, or the CELEX of a consolidation for a
 *     consolidated version
 */
@DomainEvent
public record ConversionRequested(String celex, Language language, Reason reason) {

  /** Why the text is asked for. It decides what happens to a conversion that is finished. */
  public enum Reason {
    /** The text is needed. A finished conversion stays as it is. */
    NEEDED,
    /**
     * A reader asked to try again. A conversion that failed is run again, from its first attempt,
     * if another attempt may help ({@link Failure#retryable}).
     */
    RETRIED,
    /** Cellar republished the Formex. A finished conversion is run again, built or failed. */
    REPUBLISHED
  }
}
