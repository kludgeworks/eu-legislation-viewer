// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

/** A reference to a note: its eId (note_2) and the marker as printed. */
public record NoteRef(EId note, String marker) implements Inline {}
