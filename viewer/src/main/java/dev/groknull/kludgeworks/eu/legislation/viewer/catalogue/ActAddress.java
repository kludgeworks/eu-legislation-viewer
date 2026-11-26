// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;

/**
 * The act and the language that the address of a page names, as an ELI has them: {@code
 * /eli/{type}/{year}/{number}/…/{language}}. For example, {@code /eli/reg_impl/2015/1998/…/eng}
 * names Implementing Regulation 2015/1998 in English.
 *
 * <p>A handler of a page under such an address takes it as an argument. The web side of the
 * catalogue reads it from the address, in one place, for the pages of all modules.
 *
 * @param eli the ELI of the act
 * @param language the language
 */
public record ActAddress(Eli eli, Language language) {}
