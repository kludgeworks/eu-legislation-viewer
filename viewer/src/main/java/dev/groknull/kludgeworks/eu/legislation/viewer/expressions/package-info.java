// SPDX-License-Identifier: MIT
/**
 * The text of a version: legal text converted from Formex (its unit tree, eIds, provenance). The
 * root package is the module's API and the onion's domain ring: the records, LegalText, the
 * aggregate, and LegalTexts, its repository. Its use cases are in {@code application}; in {@code
 * infra}, internal to the module, the store in Valkey, the adapter on //formex-converter's
 * stylesheets (FormexConverter), the reader and, in {@code infra.xml}, the JAXB classes generated
 * from legal-text.xsd.
 */
@ApplicationModule(displayName = "Expressions")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
