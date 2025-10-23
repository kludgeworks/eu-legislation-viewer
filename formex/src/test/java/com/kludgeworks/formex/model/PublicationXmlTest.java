package com.kludgeworks.formex.model;

import com.kludgeworks.formex.util.FormexTestDataProvider;
import com.kludgeworks.formex.util.FormexVersionTestUtils;
import com.kludgeworks.formex.util.XmlTestUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.xmlunit.assertj.XmlAssert.assertThat;

@SuppressWarnings("java:S5853") // XML assertions don't support chaining
@DisplayName("PUBLICATION XML Validation Tests")
class PublicationXmlTest {

    static Stream<Arguments> publicationTestData() {
        return FormexTestDataProvider.publicationTestData();
    }

    @ParameterizedTest
    @MethodSource("publicationTestData")
    @DisplayName("Should unmarshal PUBLICATION files successfully")
    void shouldUnmarshalSuccessfully(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        PUBLICATION publication = XmlTestUtils.unmarshalXmlFromClasspath(resourcePath, PUBLICATION.class);
        
        Assertions.assertThat(publication).isNotNull();
        Assertions.assertThat(publication.getOJ()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("publicationTestData")
    @DisplayName("Should have valid OJ element with correct structure")
    void shouldHaveValidOjElement(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        PUBLICATION publication = XmlTestUtils.unmarshalXmlFromClasspath(resourcePath, PUBLICATION.class);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);
        
        OJ oj = publication.getOJ();
        
        // Verify OJ structure exists
        assertThat(xml).hasXPath("//PUBLICATION/OJ");
        assertThat(xml).hasXPath("//PUBLICATION/OJ/BIB.OJ");
        assertThat(xml).hasXPath("//PUBLICATION/OJ/VOLUME");
        
        // Verify object model matches XML
        Assertions.assertThat(oj).isNotNull();
        Assertions.assertThat(oj.getBIBOJ()).isNotNull();
        Assertions.assertThat(oj.getVOLUMES()).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("publicationTestData")
    @DisplayName("Should have valid BIB.OJ element with required fields")
    void shouldHaveValidBibOj(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        PUBLICATION publication = XmlTestUtils.unmarshalXmlFromClasspath(resourcePath, PUBLICATION.class);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);
        
        BIBOJ bibOj = publication.getOJ().getBIBOJ();
        
        // Verify BIB.OJ required elements
        assertThat(xml).hasXPath("//PUBLICATION/OJ/BIB.OJ/COLL");
        assertThat(xml).hasXPath("//PUBLICATION/OJ/BIB.OJ/TITLE");
        assertThat(xml).hasXPath("//PUBLICATION/OJ/BIB.OJ/NO.OJ");
        assertThat(xml).hasXPath("//PUBLICATION/OJ/BIB.OJ/DATE");
        assertThat(xml).hasXPath("//PUBLICATION/OJ/BIB.OJ/LG.OJ");
        
        // Verify values match object model
        assertThat(xml).valueByXPath("//PUBLICATION/OJ/BIB.OJ/COLL")
            .isEqualTo(bibOj.getCOLL().value());
        assertThat(xml).valueByXPath("//PUBLICATION/OJ/BIB.OJ/NO.OJ")
            .isEqualTo(bibOj.getNOOJ());
        assertThat(xml).valueByXPath("//PUBLICATION/OJ/BIB.OJ/LG.OJ")
            .isEqualTo(bibOj.getLGOJ().value());
    }

    @ParameterizedTest
    @MethodSource("publicationTestData")
    @DisplayName("Should have valid VOLUME structure")
    void shouldHaveValidVolumeStructure(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        PUBLICATION publication = XmlTestUtils.unmarshalXmlFromClasspath(resourcePath, PUBLICATION.class);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);
        
        List<VOLUME> volumes = publication.getOJ().getVOLUMES();
        
        // Verify VOLUME structure
        assertThat(xml).hasXPath("//PUBLICATION/OJ/VOLUME");
        assertThat(xml).hasXPath("//PUBLICATION/OJ/VOLUME/BIB.VOLUME");
        assertThat(xml).hasXPath("//PUBLICATION/OJ/VOLUME/SECTION");
        
        // Verify object model matches XML count
        assertThat(xml).nodesByXPath("//PUBLICATION/OJ/VOLUME")
            .hasSize(volumes.size());
    }

    @ParameterizedTest
    @MethodSource("publicationTestData")
    @DisplayName("Should have valid BIB.VOLUME with page information")
    void shouldHaveValidBibVolume(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);
        
        // Verify BIB.VOLUME required elements
        assertThat(xml).hasXPath("//PUBLICATION/OJ/VOLUME/BIB.VOLUME/VOLUME.ID");
        assertThat(xml).hasXPath("//PUBLICATION/OJ/VOLUME/BIB.VOLUME/PAGE.TOTAL");
        assertThat(xml).hasXPath("//PUBLICATION/OJ/VOLUME/BIB.VOLUME/PAGE.SUMMARY");
        assertThat(xml).hasXPath("//PUBLICATION/OJ/VOLUME/BIB.VOLUME/PAGE.CONTENTS");
    }

    @ParameterizedTest
    @MethodSource("publicationTestData")
    @DisplayName("Should have valid SECTION structure with ITEM.PUB elements")
    void shouldHaveValidSectionStructure(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);
        
        // Verify SECTION structure
        assertThat(xml).hasXPath("//PUBLICATION/OJ/VOLUME/SECTION");
        assertThat(xml).hasXPath("//PUBLICATION/OJ/VOLUME/SECTION/@TYPE");
        assertThat(xml).hasXPath("//PUBLICATION/OJ/VOLUME/SECTION/TITLE");
        assertThat(xml).hasXPath("//PUBLICATION/OJ/VOLUME/SECTION/SUBSECTION");
        assertThat(xml).hasXPath("//PUBLICATION/OJ/VOLUME/SECTION/SUBSECTION/ITEM.PUB");
        assertThat(xml).hasXPath("//PUBLICATION/OJ/VOLUME/SECTION/SUBSECTION/ITEM.PUB/@DOC.INSTANCE");
    }
}