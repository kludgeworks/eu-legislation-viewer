// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The two texts of the changes of one act: the first text that includes the act, and the text just
 * before it. A comparison limited to one act shows the changes between these two texts. So the
 * changes of two acts that one text first includes are the same changes. The sidenotes tell them
 * apart where they can.
 *
 * @param before the text just before {@code first}. It is {@code first} itself if no text is before
 *     it.
 * @param first the first text that includes the act, after the earlier day and up to the later day
 */
@ValueObject
public record Inclusion(Version before, Version first) {}
