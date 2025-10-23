package com.kludgeworks.formex.model;

import com.kludgeworks.formex.util.XmlTestUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.xmlunit.assertj.XmlAssert.assertThat;

@SuppressWarnings("java:S5853") // XML assertions don't support chaining
@DisplayName("ACT XML Validation Tests")
class ActXmlTest {

    private ACT act;
    private String xml;

    @BeforeEach
    void setUp() {
        act = XmlTestUtils.unmarshalXmlFromClasspath( "/32015R1998/DOC_2.xml", ACT.class );
        xml = XmlTestUtils.readXmlFromClasspath( "/32015R1998/DOC_2.xml" );
    }

    @Test
    @DisplayName("Should unmarshal DOC_2.xml successfully")
    void shouldUnmarshalSuccessfully() {
        Assertions.assertThat( act ).isNotNull();
    }

    @Test
    @DisplayName("Should have valid BIB.INSTANCE element with correct values")
    void shouldHaveValidBibInstance() {
        BIBINSTANCE bibInstance = act.getBIBINSTANCE();

        // Verify BIB.INSTANCE structure exists
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE" );

        // Verify page and sequence information matches object model
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/NO.SEQ" ).isEqualTo( bibInstance.getNOSEQ() );
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/PAGE.FIRST" ).isEqualTo( bibInstance.getPAGEFIRST() );
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/PAGE.LAST" ).isEqualTo( bibInstance.getPAGELAST() );
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/PAGE.TOTAL" )
            .isEqualTo( bibInstance.getPAGETOTAL().toString() );

        // Verify language and date elements match object model
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/LG.DOC" ).isEqualTo( bibInstance.getLGDOC().value() );
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/DATE" );
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/DATE/@ISO" )
            .isEqualTo( bibInstance.getDATES().getFirst().getISO() );

        // Verify EEA element exists
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/EEA" );
    }

    @Test
    @DisplayName("Should have valid DOCUMENT.REF element")
    void shouldHaveValidDocumentRef() {
        DOCUMENTREF documentRef = act.getBIBINSTANCE().getDOCUMENTREF();

        // Verify DOCUMENT.REF structure exists
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/DOCUMENT.REF" );

        // Verify Official Journal reference values match object model
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/DOCUMENT.REF/COLL" )
            .isEqualTo( documentRef.getCOLL().value() );
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/DOCUMENT.REF/NO.OJ" ).isEqualTo( documentRef.getNOOJ() );
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/DOCUMENT.REF/YEAR" ).isEqualTo( documentRef.getYEAR() );
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/DOCUMENT.REF/LG.OJ" )
            .isEqualTo( documentRef.getLGOJ().value() );

        // Verify volume and page information match object model
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/DOCUMENT.REF/VOLUME.REF" )
            .isEqualTo( documentRef.getVOLUMEREF() );
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/DOCUMENT.REF/PAGE.FIRST" )
            .isEqualTo( documentRef.getPAGEFIRST() );
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/DOCUMENT.REF/@FILE" );
    }

    @ParameterizedTest
    @ValueSource(strings = { "//ACT/TITLE", "//ACT/PREAMBLE", "//ACT/ENACTING.TERMS" })
    @DisplayName("Should have required ACT elements")
    void shouldHaveRequiredElements(String xpath) {
        assertThat( xml ).hasXPath( xpath );
    }

    @Test
    @DisplayName("Should have correct NNC attribute default")
    void shouldHaveCorrectNncAttribute() {
        TBoolean nnc = act.getNNC();

        if ( nnc != null ) {
            assertThat( nnc.value() ).isEqualTo( "NO" );
        }
    }

    @Test
    @DisplayName("Should have NO.DOC element with correct values")
    void shouldHaveValidNoDoc() {
        NODOC noDoc = act.getBIBINSTANCE().getNODOCS().getFirst();

        // Verify NO.DOC structure and attributes match object model
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/NO.DOC" );
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/NO.DOC/@TYPE" ).isEqualTo( noDoc.getTYPE() );
        assertThat( xml ).valueByXPath( "//ACT/BIB.INSTANCE/NO.DOC/@FORMAT" ).isEqualTo( noDoc.getFORMAT() );
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE/NO.DOC/NO.CURRENT" );
    }


    @Test
    @DisplayName("Should have proper element structure")
    void shouldHaveProperElementStructure() {
        // Verify all main ACT elements exist
        assertThat( xml ).hasXPath( "//ACT/BIB.INSTANCE" );
        assertThat( xml ).hasXPath( "//ACT/TITLE" );
        assertThat( xml ).hasXPath( "//ACT/PREAMBLE" );
        assertThat( xml ).hasXPath( "//ACT/ENACTING.TERMS" );
    }
}