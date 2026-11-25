// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * Says that no act has the ELI, or the CELEX number ({@link CataloguedActs}). A page answers with
 * 404 and the page that says so. The API answers 404.
 */
public final class NoSuchAct extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  /** The path of the ELI, or null if the act was asked for by its CELEX number. */
  private final @Nullable String eli;

  public NoSuchAct(Eli eli) {
    super("No act has the ELI " + eli.path(), null, false, false);
    this.eli = eli.path();
  }

  /** Says that no act has the CELEX number {@code id}. */
  public NoSuchAct(ActIdentifier id) {
    super("No act has the CELEX number " + id.celex(), null, false, false);
    this.eli = null;
  }

  /** Returns the ELI, or null if the act was asked for by its CELEX number. */
  public @Nullable Eli eli() {
    return eli == null ? null : new Eli(eli);
  }
}
