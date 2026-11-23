// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The versions of an act in a language, with what a comparison of two of them needs to know. The
 * act itself is not in it: the act is an aggregate of the catalogue, and a value holds no
 * aggregate.
 *
 * @param language the language of the texts
 * @param timeline the versions of the act in the language
 * @param preparation the preparation of the act in the language: the texts of its versions
 * @param included the acts that each version includes: Cellar's lists, and the acts that the built
 *     texts tag first
 */
@ValueObject
public record Versions(
    Language language, Timeline timeline, Preparation preparation, Inclusions included) {}
