package com.kludgeworks.formex.model;

import com.kludgeworks.formex.util.XmlTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import org.xmlunit.assertj.XmlAssert;

@SuppressWarnings("java:S5853") // XML assertions don't support chaining
@DisplayName("Formex XML Unmarshalling Tests")
class FormexUnmarshallingTest {

    @Test
    @DisplayName("Should unmarshal DOC_1.xml to DOC object")
    void shouldUnmarshalDoc1() {
        DOC doc = XmlTestUtils.unmarshalXmlFromClasspath("/32015R1998/DOC_1.xml", DOC.class);
        
        assertThat(doc).isNotNull();
        assertThat(doc.getBIBDOC()).isNotNull();
        assertThat(doc.getPUBLICATIONREF()).isNotNull();
        assertThat(doc.getFMX()).isNotNull();
        
        // Validate marshalling back to XML maintains all required DOC structure elements
        String marshalledXml = XmlTestUtils.marshalToString(doc);
        XmlAssert.assertThat(marshalledXml).hasXPath("//DOC");
        XmlAssert.assertThat(marshalledXml).hasXPath("//DOC/BIB.DOC");
        XmlAssert.assertThat(marshalledXml).hasXPath("//DOC/PUBLICATION.REF");
        XmlAssert.assertThat(marshalledXml).hasXPath("//DOC/FMX");
    }

    @Test
    @DisplayName("Should unmarshal DOC_2.xml to ACT object")
    void shouldUnmarshalDoc2() {
        ACT act = XmlTestUtils.unmarshalXmlFromClasspath("/32015R1998/DOC_2.xml", ACT.class);
        
        assertThat(act).isNotNull();
        assertThat(act.getBIBINSTANCE()).isNotNull();
        assertThat(act.getGRANNOTATIONSAndTOCSAndTITLES()).isNotNull();
        assertThat(act.getPREAMBLE()).isNotNull();
        assertThat(act.getENACTINGTERMS()).isNotNull();
        
        // Validate marshalling back to XML maintains all required ACT structure elements
        String marshalledXml = XmlTestUtils.marshalToString(act);
        XmlAssert.assertThat(marshalledXml).hasXPath("//ACT");
        XmlAssert.assertThat(marshalledXml).hasXPath("//ACT/BIB.INSTANCE");
        XmlAssert.assertThat(marshalledXml).hasXPath("//ACT/PREAMBLE");
        XmlAssert.assertThat(marshalledXml).hasXPath("//ACT/ENACTING.TERMS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOC_3.xml", "DOC_4.xml", "DOC_5.xml", "DOC_6.xml"})
    @DisplayName("Should unmarshal ANNEX XML files to ANNEX objects")
    void shouldUnmarshalAnnexFiles(String fileName) {
        ANNEX annex = XmlTestUtils.unmarshalXmlFromClasspath("/32015R1998/" + fileName, ANNEX.class);
        
        assertThat(annex).isNotNull();
        assertThat(annex.getBIBINSTANCE()).isNotNull();
        
        // Validate marshalling back to XML maintains ANNEX structure elements
        String marshalledXml = XmlTestUtils.marshalToString(annex);
        XmlAssert.assertThat(marshalledXml).hasXPath("//ANNEX");
        XmlAssert.assertThat(marshalledXml).hasXPath("//ANNEX/BIB.INSTANCE");
    }

    @Test
    @DisplayName("Should handle all test XML files without errors")
    void shouldHandleAllTestFiles() {
        assertThatCode(() -> {
            XmlTestUtils.unmarshalXmlFromClasspath("/32015R1998/DOC_1.xml", DOC.class);
        }).doesNotThrowAnyException();

        assertThatCode(() -> {
            XmlTestUtils.unmarshalXmlFromClasspath("/32015R1998/DOC_2.xml", ACT.class);
        }).doesNotThrowAnyException();

        assertThatCode(() -> {
            XmlTestUtils.unmarshalXmlFromClasspath("/32015R1998/DOC_3.xml", ANNEX.class);
            XmlTestUtils.unmarshalXmlFromClasspath("/32015R1998/DOC_4.xml", ANNEX.class);
            XmlTestUtils.unmarshalXmlFromClasspath("/32015R1998/DOC_5.xml", ANNEX.class);
            XmlTestUtils.unmarshalXmlFromClasspath("/32015R1998/DOC_6.xml", ANNEX.class);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should fail gracefully for non-existent files")
    void shouldFailGracefullyForNonExistentFiles() {
        assertThatThrownBy(() -> {
            XmlTestUtils.unmarshalXmlFromClasspath("/32015R1998/NON_EXISTENT.xml", DOC.class);
        }).isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to unmarshal XML from classpath");
    }

    @Test
    @DisplayName("Should fail gracefully for wrong class type")
    void shouldFailGracefullyForWrongClassType() {
        // DOC_2.xml contains <ACT> root element, not <DOC>, so this should fail
        assertThatThrownBy(() -> {
            XmlTestUtils.unmarshalXmlFromClasspath("/32015R1998/DOC_2.xml", DOC.class);
        }).isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to unmarshal");
    }
}