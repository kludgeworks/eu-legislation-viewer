// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import org.jspecify.annotations.Nullable;

/** One signatory's lines: organisation, role, person. */
public record Signatory(
    @Nullable Lines organization, @Nullable Lines role, @Nullable Lines person) {}
