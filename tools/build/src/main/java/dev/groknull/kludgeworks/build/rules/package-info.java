// SPDX-License-Identifier: MIT
/**
 * The business rules on the wiki: each module's rules suite saves its run as Cucumber's messages
 * ({@link RulesMessages}), and {@link RulesWiki} writes a wiki page for each feature, with the
 * result of each example and what changed since the last release.
 */
@NullMarked
package dev.groknull.kludgeworks.build.rules;

import org.jspecify.annotations.NullMarked;
