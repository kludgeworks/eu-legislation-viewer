// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

/**
 * The two shapes of an ELI in a path, as Spring MVC patterns: an act's and a treaty's (Eli). A
 * route under an ELI maps both, each followed by the rest of its path, and takes the ELI read from
 * either as {@code @RequestAttribute(EliPaths.ELI) Eli eli} (EliInterceptor).
 */
public final class EliPaths {
  /** The request attribute holding the ELI of a route under one. */
  public static final String ELI = "dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli";

  /** An act's: /eli/reg_impl/2015/1998, /eli/dec/2007/44(1), /eli/C/2023/1464. */
  public static final String ACT =
      "/eli/{type:[a-z][a-z_]*|C}/{year:[0-9]{4}}/{number:[0-9]+(?:\\([0-9]+\\))?}";

  /** A treaty's, by its edition: /eli/treaty/tfeu_2016. */
  public static final String TREATY = "/eli/treaty/{edition:[a-z]+_[0-9]{4}(?:\\([0-9]+\\))?}";

  private EliPaths() {}
}
