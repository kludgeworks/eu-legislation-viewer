// SPDX-License-Identifier: MIT
/**
 * Expressions: an act's version in one language (ELI's LegalExpression, Cellar's expression, the
 * middle of FRBR's work, expression and manifestation), with its text converted from Formex (its
 * unit tree, eIds, provenance). The root package is the module's API and the onion's domain ring:
 * the records, Expression, the aggregate, and Expressions, its repository. Its use cases are in
 * {@code application}; in {@code infra}, internal to the module, the store in Valkey, the adapter
 * on //formex-converter's stylesheets (FormexConverter), the reader and, in {@code infra.xml}, the
 * JAXB classes generated from legal-text.xsd. A lookup that may find nothing returns an Optional
 * (Expressions.find); a value that may be absent, as a record component, is @Nullable.
 */
@ApplicationModule(displayName = "Expressions")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
