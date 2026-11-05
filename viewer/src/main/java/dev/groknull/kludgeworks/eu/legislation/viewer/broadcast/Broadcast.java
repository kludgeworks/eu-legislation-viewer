// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.broadcast;

import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

/**
 * An externalized event (Spring Modulith's @Externalized) as every instance receives it: read back
 * from Valkey once the transaction that published it committed, on the instance that published it
 * as on every other. A listener for Broadcast of a type (an @EventListener of Broadcast of
 * ExpressionChanged) hears each such event once, after commit, wherever it happened; one for the
 * type itself hears it only on the instance that published it, before commit.
 *
 * <p>Not kept: an instance that wasn't reading when it was sent (starting, or Valkey unreachable)
 * misses it, so a listener reads the state it is about anew, never from a series of them.
 *
 * @param event the event, as it was published
 */
public record Broadcast<T>(T event) implements ResolvableTypeProvider {

  /** Broadcast of the event's own type, so a listener can ask for a Broadcast of one type. */
  @Override
  public ResolvableType getResolvableType() {
    return ResolvableType.forClassWithGenerics(Broadcast.class, ResolvableType.forInstance(event));
  }
}
