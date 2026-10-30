// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

/** What an image shows, as Formex classifies it. */
public enum ImageContent {
  /** Text. */
  CHARACTER,
  /** A scanned form. */
  FORM,
  /** A mathematical or chemical expression Formex can't mark up. */
  FORMULA,
  /** A handwritten signature. */
  SIGNATURE,
  /** A technical drawing. */
  TECH_DESIGN,
  /** Anything else. */
  OTHER
}
