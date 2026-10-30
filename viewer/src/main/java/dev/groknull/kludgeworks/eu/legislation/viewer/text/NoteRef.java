// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

/**
 * Where a footnote is referred to; the note itself is in notes.
 *
 * @param marker The marker as printed.
 */
public record NoteRef(EId note, String marker) implements Inline {}
