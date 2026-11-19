// SPDX-License-Identifier: MIT
/**
 * Postgres's full-text search and its trigram similarity ({@code pg_trgm}) for jOOQ, which has
 * neither built in.
 *
 * <ul>
 *   <li>{@link TsVector} and {@link TsQuery} are Java types for Postgres's {@code tsvector} and
 *       {@code tsquery}. jOOQ's code generation maps every {@code tsvector} column to {@link
 *       TsVector} with a forced type that matches by type (the {@code jooq.xml} of a module).
 *   <li>{@link TextSearch} has the operators and the functions of {@code pg_catalog} as typed
 *       fields and conditions.
 * </ul>
 *
 * <p>The library holds nothing of the viewer's own. It is outside the viewer's application modules,
 * as a library of another project would be.
 */
@NullMarked
package dev.groknull.kludgeworks.jooq.postgres;

import org.jspecify.annotations.NullMarked;
