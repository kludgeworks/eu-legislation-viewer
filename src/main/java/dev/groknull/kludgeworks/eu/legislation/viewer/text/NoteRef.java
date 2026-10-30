// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

/** A reference to a note, by the note's eId (#note_2). */
public record NoteRef(String href, String marker) implements Inline {}
