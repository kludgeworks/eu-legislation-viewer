// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import org.jspecify.annotations.Nullable;

/**
 * Which amending act put a unit or block in its current form, from the consolidation's amendment
 * markers (Formex CLG.MDFO and CLG.MDFC processing instructions).
 *
 * @param amendingAct The CELEX number of the amending act.
 * @param action What the amending act did.
 * @param location Where in the amending act, as Formex writes it: "AR:1 .AND. AX:0;PT:27".
 * @param marker The Formex marker id; pieces of one marker split at element boundaries share it.
 */
public record Provenance(
    String amendingAct, Action action, @Nullable String location, @Nullable String marker) {

  /**
   * What an amending act did (Formex ACTION). They match the EU authority table "modification
   * type": replaced is REPLACEMENT, inserted is INSERTION, deleted is DELETION.
   */
  public enum Action {
    /** Replaced: new text instead of the old. */
    REPLACED,
    /** Inserted: new text added. */
    INSERTED,
    /** Deleted: the text is gone. */
    DELETED
  }
}
