// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.broadcast;

import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

/**
 * An externalized event as every instance receives it. Spring Modulith externalizes the events of a
 * type marked {@code @Externalized}. Every instance reads the event back from Valkey after the
 * transaction that published it committed. This includes the instance that published it.
 *
 * <p>A listener for a {@code Broadcast} of a type receives each such event once, after commit,
 * wherever it happened. An example is an {@code @EventListener} of {@code
 * Broadcast<PreparationChanged>}. A listener for the type itself receives the event only on the
 * instance that published it, before commit.
 *
 * <p>A broadcast is not stored. An instance that was not reading when it was sent misses it, for
 * example while the instance starts or while Valkey is unreachable. Thus a listener reads anew the
 * state that the event is about. It never builds the state from a series of events.
 *
 * @param event the event, as it was published
 */
public record Broadcast<T>(T event) implements ResolvableTypeProvider {

  /**
   * Returns {@code Broadcast} of the event's own type, so that a listener can ask for a {@code
   * Broadcast} of one type.
   */
  @Override
  public ResolvableType getResolvableType() {
    return ResolvableType.forClassWithGenerics(Broadcast.class, ResolvableType.forInstance(event));
  }
}
