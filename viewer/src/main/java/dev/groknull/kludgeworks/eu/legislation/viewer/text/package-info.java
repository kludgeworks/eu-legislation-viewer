// SPDX-License-Identifier: MIT
/**
 * The text of a version: legal text converted from Formex (its unit tree, eIds, provenance). The
 * public API will be the domain records and the LegalTexts service; the converter and the generated
 * JAXB classes are in {@code infra}, internal to the module.
 */
@ApplicationModule(displayName = "Text")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
