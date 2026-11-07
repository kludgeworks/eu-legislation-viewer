// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;
import org.springframework.modulith.events.Externalized;

/**
 * The state of one of an act's texts in a language changed. The text was queued again, is being
 * built, was built or failed.
 *
 * <p>It is broadcast to every instance on the {@code preparation} stream. The pages that wait on
 * the act then read its state anew from {@link Texts#preparation}.
 */
@DomainEvent
@Externalized("preparation")
public record PreparationChanged(ActIdentifier act, Language language) {}
