// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * An Official Journal publication.
 *
 * @param series The OJ series: L or C.
 * @param number The issue number, without leading zeros; absent for acts published individually
 *     (from October 2023).
 * @param date The publication date.
 * @param page The first page.
 * @param document A C-series document's number, as printed above its title, without brackets:
 *     2022/C 155/03 (Formex NO.DOC.C).
 */
public record Publication(
    String series,
    @Nullable String number,
    @Nullable LocalDate date,
    @Nullable String page,
    @Nullable String document) {}
