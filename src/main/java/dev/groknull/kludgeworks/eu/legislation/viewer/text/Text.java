// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

/**
 * Plain text, with its characters exactly as in the source, including non-breaking spaces.
 *
 * @param value The text.
 */
public record Text(String value) implements Inline {}
