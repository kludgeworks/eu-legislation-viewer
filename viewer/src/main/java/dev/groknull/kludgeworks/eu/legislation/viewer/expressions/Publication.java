// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * An Official Journal publication.
 *
 * @param series The OJ series: L or C.
 * @param number The issue number, without leading zeros; absent for documents published on their
 *     own (from October 2023), which have a document number instead.
 * @param date The publication date.
 * @param page The first page.
 * @param document The document's number in the Official Journal: for a document published on its
 *     own (from October 2023), its number in the series, 2026/449 (cited OJ L, 2026/449); before,
 *     for a C-series document, the number printed above its title, without brackets, 2022/C 155/03
 *     (Formex NO.DOC.C).
 */
public record Publication(
    String series,
    @Nullable String number,
    @Nullable LocalDate date,
    @Nullable String page,
    @Nullable String document) {}
