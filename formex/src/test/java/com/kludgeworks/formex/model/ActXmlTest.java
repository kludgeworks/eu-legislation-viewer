package com.kludgeworks.formex.model;

import com.kludgeworks.formex.util.FormexTestDataProvider;
import com.kludgeworks.formex.util.FormexVersionTestUtils;
import com.kludgeworks.formex.util.XmlTestUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.xmlunit.assertj.XmlAssert.assertThat;

@SuppressWarnings("java:S5853") // XML assertions don't support chaining
@DisplayName("ACT XML Validation Tests")
class ActXmlTest {

    static Stream<Arguments> actTestData() {
        return FormexTestDataProvider.actTestData();
    }

    @ParameterizedTest
    @MethodSource("actTestData")
    @DisplayName("Should unmarshal ACT files successfully")
    void shouldUnmarshalSuccessfully(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        ACT act = XmlTestUtils.unmarshalXmlFromClasspath(resourcePath, ACT.class);
        Assertions.assertThat( act ).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("actTestData")
    @DisplayName("Should have valid BIB.INSTANCE element with correct values")
    void shouldHaveValidBibInstance(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        ACT act = XmlTestUtils.unmarshalXmlFromClasspath(resourcePath, ACT.class);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);
        
        BIBINSTANCE bibInstance = act.getBIBINSTANCE();

        // Verify BIB.INSTANCE structure exists
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE" );

        // Verify page and sequence information matches object model
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/NO.SEQ" ).isEqualTo( bibInstance.getNOSEQ() );
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/PAGE.FIRST" ).isEqualTo( bibInstance.getPAGEFIRST() );
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/PAGE.LAST" ).isEqualTo( bibInstance.getPAGELAST() );
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/PAGE.TOTAL" )
            .isEqualTo( bibInstance.getPAGETOTAL().toString() );

        // Verify structural elements exist
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/LG.DOC" );
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/DATE" );
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/EEA" );
    }

    @ParameterizedTest
    @MethodSource("actTestData")
    @DisplayName("Should have valid DOCUMENT.REF element")
    void shouldHaveValidDocumentRef(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);

        // Verify DOCUMENT.REF structure exists
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/DOCUMENT.REF" );
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/DOCUMENT.REF/COLL" );
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/DOCUMENT.REF/NO.OJ" );
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/DOCUMENT.REF/YEAR" );
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/DOCUMENT.REF/LG.OJ" );
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/DOCUMENT.REF/VOLUME.REF" );
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/DOCUMENT.REF/PAGE.FIRST" );
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/DOCUMENT.REF/@FILE" );
    }

    @ParameterizedTest
    @MethodSource("actTestData")
    @DisplayName("Should have required ACT elements")
    void shouldHaveRequiredElements(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);
        
        assertThat( xml ).hasXPath( "//ACT/TITLE" );
        assertThat( xml ).hasXPath( "//ACT/PREAMBLE" );
        assertThat( xml ).hasXPath( "//ACT/ENACTING.TERMS" );
    }

    @ParameterizedTest
    @MethodSource("actTestData")
    @DisplayName("Should have NO.DOC element with correct structure")
    void shouldHaveValidNoDoc(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);

        // Verify NO.DOC structure exists
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/NO.DOC" );
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/NO.DOC/@TYPE" );
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/NO.DOC/@FORMAT" );
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/NO.DOC/NO.CURRENT" );
    }

    @ParameterizedTest
    @MethodSource("actTestData")
    @DisplayName("Should have proper element structure")
    void shouldHaveProperElementStructure(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);
        
        // Verify all main ACT elements exist
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE" );
        assertThat( xml ).hasXPath( "//ACT/TITLE" );
        assertThat( xml ).hasXPath( "//ACT/PREAMBLE" );
        assertThat( xml ).hasXPath( "//ACT/ENACTING.TERMS" );
    }
}