// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Tests the About page, which the home page's footer links to by its parts. */
@WebMvcTest(AboutController.class)
@Import(ControllerTesting.class)
class AboutControllerTests {
  @Autowired MockMvcTester mvc;

  @Test
  void saysWhatThisIsWhatItKeepsWhatItOffersAndWhichBuild() throws UnsupportedEncodingException {
    var result = mvc.get().uri("/about").exchange();
    assertThat(result).hasStatusOk();
    var page = Jsoup.parse(result.getResponse().getContentAsString());

    assertThat(page.select(".about-page section").eachAttr("id"))
        .containsExactly("what", "privacy", "api", "build");
    assertThat(page.select("#privacy li b").eachText()).containsExactly("theme", "search");
    assertThat(page.select("#build .mono").text()).isEqualTo(AboutController.UNVERSIONED);
  }
}
