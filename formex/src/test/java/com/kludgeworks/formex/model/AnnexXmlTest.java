package com.kludgeworks.formex.model;

import com.kludgeworks.formex.util.XmlTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xmlunit.assertj.XmlAssert;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.xmlunit.assertj.XmlAssert.assertThat;

@DisplayName("ANNEX XML Validation Tests")
class AnnexXmlTest {

    @Test
    @DisplayName("Should unmarshal DOC_3.xml (first annex) successfully")
    void shouldUnmarshalDoc3Successfully() {
        ANNEX annex = XmlTestUtils.unmarshalXmlFromClasspath( "/32015R1998/DOC_3.xml", ANNEX.class );

        assertThat( annex ).isNotNull();
        assertThat( annex.getBIBINSTANCE() ).isNotNull();
        assertThat( annex.getGRANNOTATIONSAndTITLESAndTOCS() ).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({
        "DOC_3.xml",
        "DOC_4.xml",
        "DOC_5.xml",
        "DOC_6.xml"
    })
    @DisplayName("Should have correct ANNEX values for each document")
    void shouldHaveCorrectAnnexValues(String fileName) {
        ANNEX annex = XmlTestUtils.unmarshalXmlFromClasspath( "/32015R1998/" + fileName, ANNEX.class );
        String xml = XmlTestUtils.readXmlFromClasspath( "/32015R1998/" + fileName );
        BIBINSTANCE bibInstance = annex.getBIBINSTANCE();

        assertThat( xml ).satisfies( x -> {
            // Verify BIB.INSTANCE structure and sequence information match object model
            XmlAssert.assertThat( x ).hasXPath( "//ANNEX/BIB.INSTANCE" );
            XmlAssert.assertThat( x ).valueByXPath( "//ANNEX/BIB.INSTANCE/NO.SEQ" ).isEqualTo( bibInstance.getNOSEQ() );

            // Verify page information matches object model
            XmlAssert.assertThat( x )
                .valueByXPath( "//ANNEX/BIB.INSTANCE/PAGE.FIRST" )
                .isEqualTo( bibInstance.getPAGEFIRST() );
            XmlAssert.assertThat( x )
                .valueByXPath( "//ANNEX/BIB.INSTANCE/PAGE.LAST" )
                .isEqualTo( bibInstance.getPAGELAST() );
            XmlAssert.assertThat( x )
                .valueByXPath( "//ANNEX/BIB.INSTANCE/PAGE.TOTAL" )
                .isEqualTo( bibInstance.getPAGETOTAL().toString() );

            // Verify document reference exists
            XmlAssert.assertThat( x ).hasXPath( "//ANNEX/BIB.INSTANCE/DOCUMENT.REF" );
        } );

    }

    @Test
    @DisplayName("Should have valid BIB.INSTANCE with common properties")
    void shouldHaveValidBibInstanceCommonProperties() {
        ANNEX annex = XmlTestUtils.unmarshalXmlFromClasspath( "/32015R1998/DOC_3.xml", ANNEX.class );
        String xml = XmlTestUtils.readXmlFromClasspath( "/32015R1998/DOC_3.xml" );
        BIBINSTANCE bibInstance = annex.getBIBINSTANCE();

        assertThat( xml ).satisfies( x -> {
            // Verify common BIB.INSTANCE properties across all annexes
            XmlAssert.assertThat( x ).hasXPath( "//ANNEX/BIB.INSTANCE" );
            XmlAssert.assertThat( x )
                .valueByXPath( "//ANNEX/BIB.INSTANCE/PAGE.SEQ" )
                .isEqualTo( bibInstance.getPAGESEQ() );
            XmlAssert.assertThat( x ).hasXPath( "//ANNEX/BIB.INSTANCE/DATE" );
        } );

    }

    @Test
    @DisplayName("Should have DOCUMENT.REF with correct OJ reference")
    void shouldHaveValidDocumentRefOjReference() {
        ANNEX annex = XmlTestUtils.unmarshalXmlFromClasspath( "/32015R1998/DOC_3.xml", ANNEX.class );
        String xml = XmlTestUtils.readXmlFromClasspath( "/32015R1998/DOC_3.xml" );
        BIBINSTANCE bibInstance = annex.getBIBINSTANCE();
        DOCUMENTREF documentRef = bibInstance.getDOCUMENTREF();

        assertThat( xml ).satisfies( x -> {
            // Verify DOCUMENT.REF structure exists
            XmlAssert.assertThat( x ).hasXPath( "//ANNEX/BIB.INSTANCE/DOCUMENT.REF" );

            // Verify Official Journal reference details
            XmlAssert.assertThat( x )
                .valueByXPath( "//ANNEX/BIB.INSTANCE/DOCUMENT.REF/COLL" )
                .isEqualTo( documentRef.getCOLL().value() );
            XmlAssert.assertThat( x )
                .valueByXPath( "//ANNEX/BIB.INSTANCE/DOCUMENT.REF/NO.OJ" )
                .isEqualTo( documentRef.getNOOJ() );
            XmlAssert.assertThat( x )
                .valueByXPath( "//ANNEX/BIB.INSTANCE/DOCUMENT.REF/YEAR" )
                .isEqualTo( documentRef.getYEAR() );
            XmlAssert.assertThat( x )
                .valueByXPath( "//ANNEX/BIB.INSTANCE/DOCUMENT.REF/LG.OJ" )
                .isEqualTo( documentRef.getLGOJ().value() );
            XmlAssert.assertThat( x )
                .valueByXPath( "//ANNEX/BIB.INSTANCE/DOCUMENT.REF/VOLUME.REF" )
                .isEqualTo( documentRef.getVOLUMEREF() );
        } );

    }

    @ParameterizedTest
    @ValueSource(strings = { "//ANNEX/TITLE", "//ANNEX/CONTENTS" })
    @DisplayName("Should have required ANNEX structural elements")
    void shouldHaveRequiredStructuralElements(String xpath) {
        String xml = XmlTestUtils.readXmlFromClasspath( "/32015R1998/DOC_3.xml" );

        XmlAssert.assertThat( xml ).hasXPath( xpath );
    }

    @Test
    @DisplayName("Should have correct NNC attribute")
    void shouldHaveCorrectNncAttribute() {
        ANNEX annex = XmlTestUtils.unmarshalXmlFromClasspath( "/32015R1998/DOC_3.xml", ANNEX.class );

        TBoolean nnc = annex.getNNC();
        if ( nnc != null ) {
            assertThat( nnc.value() ).isEqualTo( "NO" );
        }
    }

    @ParameterizedTest
    @CsvSource({
        "DOC_3.xml",
        "DOC_4.xml",
        "DOC_5.xml",
        "DOC_6.xml"
    })
    @DisplayName("Should handle all ANNEX files without errors")
    void shouldHandleAllAnnexFilesWithoutErrors(String fileName) {
        assertThatCode( () -> {
            ANNEX annex = XmlTestUtils.unmarshalXmlFromClasspath( "/32015R1998/" + fileName, ANNEX.class );
            assertThat( annex ).isNotNull();
            assertThat( annex.getBIBINSTANCE() ).isNotNull();
            assertThat( annex.getCONTENTS() ).isNotNull();
            assertThat( annex.getGRANNOTATIONSAndTITLESAndTOCS() ).isNotNull();
        } ).doesNotThrowAnyException();
    }
}