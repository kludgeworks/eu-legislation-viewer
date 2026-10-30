// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

class FormexFilesTest {
  static final MediaType RESULTS = MediaType.parseMediaType("application/sparql-results+json");

  @Test
  void findsEachFileOfAVersionWithOneItemPerFile() {
    var mock = Recorded.Mock.of();
    mock.server()
        .expect(requestTo(Recorded.SPARQL))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("%2232009D0959%22")))
        .andRespond(withSuccess(Recorded.bytes("sparql-32009D0959.json"), RESULTS));

    var version = files(mock).manifestation("32009D0959", "ENG").orElseThrow();

    assertThat(version.uri())
        .isEqualTo(
            URI.create(
                "http://publications.europa.eu/resource/cellar/8f145680-d086-4e3d-b6a0-a8b83cdc04b9.0006.01"));
    assertThat(version.zipped()).isFalse();
    assertThat(version.files())
        .extracting(FormexManifestation.File::name)
        .containsExactly(
            "L_2009330EN.01008001.doc.xml",
            "L_2009330EN.01008101.xml",
            "L_2009330EN.01008001.xml",
            "L_2009330EN.01008102.tif");
    assertThat(version.file("L_2009330EN.01008102.tif"))
        .map(f -> f.mediaType() + " " + f.sha1().orElseThrow() + " " + f.uri())
        .contains(
            "image/tiff 0e2f1dcaa096a07478cfb6a82a4b74d4e832f621"
                + " http://publications.europa.eu/resource/cellar/8f145680-d086-4e3d-b6a0-a8b83cdc04b9.0006.01/DOC_4");
  }

  @Test
  void findsTheEntriesOfAZipOnlyVersionFromTheContentStreamList() {
    var mock = Recorded.Mock.of();
    mock.server()
        .expect(requestTo(Recorded.SPARQL))
        .andRespond(withSuccess(Recorded.bytes("sparql-32023R1669.json"), RESULTS));
    mock.server()
        .expect(requestTo("http://publications.europa.eu/resource/celex/32023R1669"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Accept", "application/list;mtype=fmx4"))
        .andExpect(header("Accept-Language", "eng"))
        .andRespond(
            withSuccess(Recorded.bytes("list-32023R1669.xhtml"), MediaType.APPLICATION_XHTML_XML));

    var version = files(mock).manifestation("32023R1669", "ENG").orElseThrow();

    assertThat(version.zipped()).isTrue();
    assertThat(version.lastModified()).isEqualTo(Instant.parse("2023-08-31T07:11:31.129Z"));
    // the package descriptor, 11 more XML files and 15 images
    assertThat(version.files()).hasSize(27);
    assertThat(version.files().stream().filter(f -> f.mediaType().equals("image/tiff")))
        .hasSize(15);
    assertThat(version.file("L_2023214EN.01000901.doc.xml"))
        .map(FormexManifestation.File::uri)
        .contains(
            URI.create(
                "http://publications.europa.eu/resource/cellar/80cad8d3-47c9-11ee-bbdc-01aa75ed71a1.0006.02/DOC_1/L_2023214EN.01000901.doc.xml"));
    assertThat(version.files()).allSatisfy(f -> assertThat(f.sha1()).isEqualTo(Optional.empty()));
    mock.server().verify();
  }

  @Test
  void refusesWhatIsNotACelexNumber() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> files(Recorded.Mock.of()).manifestation("32015R1998\"^^xsd:string } #", "ENG"));
  }

  private static FormexFiles files(Recorded.Mock mock) {
    return mock.formexFiles();
  }
}
