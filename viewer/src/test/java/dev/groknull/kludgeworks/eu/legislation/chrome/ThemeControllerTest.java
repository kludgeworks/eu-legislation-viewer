// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.chrome;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class ThemeControllerTest {
  private static final String PAGE = "/eli/reg_impl/2015/1998/2026-03-22/eng";

  private final ThemeController themes = new ThemeController();

  @Test
  void keepsTheChosenThemeAndGoesBackToThePage() {
    var chosen = themes.choose("dark", PAGE + "?from=x", "navigate", new MockHttpServletRequest());

    assertThat(chosen.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(chosen.getHeaders().getLocation()).isEqualTo(URI.create(PAGE + "?from=x"));
    assertThat(chosen.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
        .startsWith("theme=dark;")
        .contains("HttpOnly");
  }

  @Test
  void keepsTheThemeTheScriptSwitchedThePageTo() {
    var kept = themes.choose("light", PAGE, "cors", new MockHttpServletRequest());

    assertThat(kept.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(kept.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).startsWith("theme=light;");
  }

  @Test
  void goesBackOnlyToAPageOfThisSite() {
    for (var elsewhere :
        new String[] {"https://example.org/", "//example.org/", "/\\example.org/"}) {
      assertThat(
              themes
                  .choose("light", elsewhere, null, new MockHttpServletRequest())
                  .getHeaders()
                  .getLocation())
          .as(elsewhere)
          .isEqualTo(URI.create("/"));
    }
  }
}
