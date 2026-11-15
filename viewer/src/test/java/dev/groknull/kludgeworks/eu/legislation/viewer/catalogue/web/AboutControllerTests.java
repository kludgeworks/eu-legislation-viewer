// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.site.Release;
import java.io.UnsupportedEncodingException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Tests the About page, which the home page's footer links to by its parts. */
@WebMvcTest(AboutController.class)
@Import({ControllerTesting.class, ApiDocs.class})
class AboutControllerTests {
  @Autowired MockMvcTester mvc;

  @Test
  void saysWhatThisIsWhatItKeepsAndWhichBuild() throws UnsupportedEncodingException {
    var page = page();

    assertThat(page.select("article section").eachAttr("id"))
        .containsExactly("what", "privacy", "build");
    assertThat(page.select("#privacy li b").eachText()).containsExactly("theme", "search");
    assertThat(page.select("#build .mono").text()).isEqualTo("2026.11.7");
  }

  /**
   * The contents are those of the API's documentation: the parts of the page by their ids, and the
   * parts of the API on its page.
   */
  @Test
  void hasTheContentsOfTheApisDocumentation() throws UnsupportedEncodingException {
    var contents = page().select("nav[aria-label=Contents]");

    assertThat(contents.select("a[href^=#]").eachAttr("href"))
        .containsExactly("#what", "#privacy", "#build");
    assertThat(contents.select("a[href=/api/docs#act]").text()).isEqualTo("Act");
    assertThat(contents.select("a[href=/api/docs#text-failed]").text()).isEqualTo("text-failed");
  }

  private org.jsoup.nodes.Document page() throws UnsupportedEncodingException {
    var result = mvc.get().uri("/about").exchange();
    assertThat(result).hasStatusOk();
    return Jsoup.parse(result.getResponse().getContentAsString());
  }

  /** The release, as a release build stamps it. */
  @TestConfiguration(proxyBeanMethods = false)
  static class Released {
    @Bean
    Release release() {
      return new Release("2026.11.7");
    }
  }
}
