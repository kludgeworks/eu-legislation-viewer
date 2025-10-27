package com.kludgeworks.formex.model;

import com.kludgeworks.formex.util.FormexVersionTestUtils;
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
    @DisplayName("Should unmarshal DOC file to DOC object")
    void shouldUnmarshalDoc1() {
        // Use actual file path from formex-05.55-20141201.xd
        String resourcePath = FormexVersionTestUtils.buildResourcePath(
            "formex-05.55-20141201.xd",
            "5192d525-c62e-11e4-bbe1-01aa75ed71a1.0006.02/DOC_1.xml"
        );
        DOC doc = XmlTestUtils.unmarshalXmlFromClasspath(resourcePath, DOC.class);
        
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
    @DisplayName("Should unmarshal ACT file to ACT object")
    void shouldUnmarshalDoc2() {
        // Use actual file path from formex-05.55-20141201.xd
        String resourcePath = FormexVersionTestUtils.buildResourcePath(
            "formex-05.55-20141201.xd",
            "ee66b1ba-7c7a-11e5-9fae-01aa75ed71a1.0006.02/DOC_2.xml"
        );
        ACT act = XmlTestUtils.unmarshalXmlFromClasspath(resourcePath, ACT.class);
        
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
    @ValueSource(strings = {
        "formex-01.01-20040501.xd/bdf7a506-c19a-4032-ad42-c8f256ed1a1c.0004.05/DOC_3.xml",
        "formex-01.01-20040501.xd/bdf7a506-c19a-4032-ad42-c8f256ed1a1c.0004.05/DOC_4.xml",
        "formex-02.00-20050101.xd/564a8cae-615f-408c-b1f8-266c83e2cf36.0004.04/DOC_2.xml",
        "formex-02.00-20050101.xd/564a8cae-615f-408c-b1f8-266c83e2cf36.0004.04/DOC_6.xml"
    })
    @DisplayName("Should unmarshal ANNEX XML files to ANNEX objects")
    void shouldUnmarshalAnnexFiles(String versionAndPath) {
        String[] parts = versionAndPath.split("/", 2);
        String resourcePath = FormexVersionTestUtils.buildResourcePath(parts[0], parts[1]);
        ANNEX annex = XmlTestUtils.unmarshalXmlFromClasspath(resourcePath, ANNEX.class);
        
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
            String docPath = FormexVersionTestUtils.buildResourcePath(
                "formex-05.55-20141201.xd",
                "5192d525-c62e-11e4-bbe1-01aa75ed71a1.0006.02/DOC_1.xml"
            );
            XmlTestUtils.unmarshalXmlFromClasspath(docPath, DOC.class);
        }).doesNotThrowAnyException();

        assertThatCode(() -> {
            String actPath = FormexVersionTestUtils.buildResourcePath(
                "formex-05.55-20141201.xd",
                "ee66b1ba-7c7a-11e5-9fae-01aa75ed71a1.0006.02/DOC_2.xml"
            );
            XmlTestUtils.unmarshalXmlFromClasspath(actPath, ACT.class);
        }).doesNotThrowAnyException();

        assertThatCode(() -> {
            String annex1 = FormexVersionTestUtils.buildResourcePath(
                "formex-01.01-20040501.xd",
                "bdf7a506-c19a-4032-ad42-c8f256ed1a1c.0004.05/DOC_3.xml"
            );
            String annex2 = FormexVersionTestUtils.buildResourcePath(
                "formex-01.01-20040501.xd",
                "bdf7a506-c19a-4032-ad42-c8f256ed1a1c.0004.05/DOC_4.xml"
            );
            XmlTestUtils.unmarshalXmlFromClasspath(annex1, ANNEX.class);
            XmlTestUtils.unmarshalXmlFromClasspath(annex2, ANNEX.class);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should fail gracefully for non-existent files")
    void shouldFailGracefullyForNonExistentFiles() {
        assertThatThrownBy(() -> {
            String path = FormexVersionTestUtils.buildResourcePath(
                "formex-05.55-20141201.xd",
                "NON_EXISTENT.xml"
            );
            XmlTestUtils.unmarshalXmlFromClasspath(path, DOC.class);
        }).isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to unmarshal XML from classpath");
    }

    @Test
    @DisplayName("Should fail gracefully for wrong class type")
    void shouldFailGracefullyForWrongClassType() {
        // This file contains <ACT> root element, not <DOC>, so this should fail
        assertThatThrownBy(() -> {
            String path = FormexVersionTestUtils.buildResourcePath(
                "formex-05.55-20141201.xd",
                "ee66b1ba-7c7a-11e5-9fae-01aa75ed71a1.0006.02/DOC_2.xml"
            );
            XmlTestUtils.unmarshalXmlFromClasspath(path, DOC.class);
        }).isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to unmarshal");
    }
}