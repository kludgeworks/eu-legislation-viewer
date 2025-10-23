package com.kludgeworks.formex.model;

import com.kludgeworks.formex.util.XmlTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.xmlunit.assertj.XmlAssert.assertThat;

@SuppressWarnings("java:S5853") // XML assertions don't support chaining
@DisplayName("DOC XML Validation Tests")
class DocXmlTest {

    private DOC doc;
    private String xml;

    @BeforeEach
    void setUp() {
        doc = XmlTestUtils.unmarshalXmlFromClasspath( "/32015R1998/DOC_1.xml", DOC.class );
        xml = XmlTestUtils.readXmlFromClasspath( "/32015R1998/DOC_1.xml" );
    }

    @Test
    @DisplayName("Should unmarshal DOC_1.xml successfully")
    void shouldUnmarshalSuccessfully() {
        assertThat( doc ).isNotNull();
    }

    @Test
    @DisplayName("Should have valid BIB.DOC element with correct values")
    void shouldHaveValidBibDoc() {
        BIBDOC bibDoc = doc.getBIBDOC();

        // Verify BIB.DOC identification values match object model
        assertThat( xml ).valueByXPath( "//DOC/BIB.DOC/PROD.ID" ).isEqualTo( bibDoc.getPRODID() );
        assertThat( xml ).valueByXPath( "//DOC/BIB.DOC/FIN.ID" ).isEqualTo( bibDoc.getFINID() );
        assertThat( xml ).valueByXPath( "//DOC/BIB.DOC/AUTHOR" ).isEqualTo( bibDoc.getAUTHORS().getFirst() );

        // Verify required structural elements exist
        assertThat( xml ).hasXPath( "//DOC/BIB.DOC/NO.DOC" );
        assertThat( xml ).hasXPath( "//DOC/BIB.DOC/EEA" );
    }

    @Test
    @DisplayName("Should have valid PUBLICATION.REF element with correct values")
    void shouldHaveValidPublicationRef() {
        PUBLICATIONREF publicationRef = doc.getPUBLICATIONREF();

        // Verify PUBLICATION.REF structure exists
        assertThat( xml ).hasXPath( "//DOC/PUBLICATION.REF" );

        // Verify Official Journal publication details match object model
        assertThat( xml ).valueByXPath( "//DOC/PUBLICATION.REF/COLL" ).isEqualTo( publicationRef.getCOLL().value() );
        assertThat( xml ).valueByXPath( "//DOC/PUBLICATION.REF/NO.OJ" ).isEqualTo( publicationRef.getNOOJ() );
        assertThat( xml ).valueByXPath( "//DOC/PUBLICATION.REF/VOLUME.REF" )
            .isEqualTo( publicationRef.getVOLUMEREF() );
        assertThat( xml ).valueByXPath( "//DOC/PUBLICATION.REF/LG.OJ" ).isEqualTo( publicationRef.getLGOJ().value() );
        assertThat( xml ).valueByXPath( "//DOC/PUBLICATION.REF/DATE/@ISO" )
            .isEqualTo( publicationRef.getDATE().getISO() );
    }

    @Test
    @DisplayName("Should have valid FMX structure")
    void shouldHaveFmxStructure() {
        assertThat(xml).hasXPath("//DOC/FMX");
        assertThat(xml).hasXPath("//DOC/FMX/DOC.MAIN.PUB");
        assertThat(xml).nodesByXPath("//DOC/FMX/DOC.SUB.PUB")
            .hasSize(doc.getFMX().getDOCSUBPUBS().size());
    }

    @Test
    @DisplayName("Should have valid main publication attributes")
    void shouldHaveValidMainPublicationAttributes() {
        DOCMAINPUB mainPub = doc.getFMX().getDOCMAINPUB();

        assertThat(xml).valueByXPath("//DOC/FMX/DOC.MAIN.PUB/@NO.SEQ")
            .isEqualTo(mainPub.getNOSEQ());
        assertThat(xml).valueByXPath("//DOC/FMX/DOC.MAIN.PUB/@SYNOPTISM")
            .isEqualTo(mainPub.getSYNOPTISM());
    }

    @Test
    @DisplayName("Should have valid main publication page information")
    void shouldHaveValidMainPublicationPages() {
        DOCMAINPUB mainPub = doc.getFMX().getDOCMAINPUB();

        assertThat(xml).valueByXPath("//DOC/FMX/DOC.MAIN.PUB/PAGE.FIRST")
            .isEqualTo(mainPub.getPAGEFIRST());
        assertThat(xml).valueByXPath("//DOC/FMX/DOC.MAIN.PUB/PAGE.LAST")
            .isEqualTo(mainPub.getPAGELAST());
        assertThat(xml).valueByXPath("//DOC/FMX/DOC.MAIN.PUB/PAGE.TOTAL")
            .isEqualTo(mainPub.getPAGETOTAL().toString());
        assertThat(xml).valueByXPath("//DOC/FMX/DOC.MAIN.PUB/PAGE.SEQ")
            .isEqualTo(mainPub.getPAGESEQ());
    }

    @Test
    @DisplayName("Should have valid first sub-publication")
    void shouldHaveValidFirstSubPublication() {
        DOCSUBPUB firstSubPub = doc.getFMX().getDOCSUBPUBS().getFirst();

        assertThat(xml).valueByXPath("//DOC/FMX/DOC.SUB.PUB[1]/@TYPE")
            .isEqualTo(firstSubPub.getTYPE());
        assertThat(xml).valueByXPath("//DOC/FMX/DOC.SUB.PUB[1]/@NO.SEQ")
            .isEqualTo(firstSubPub.getNOSEQ());
        assertThat(xml).valueByXPath("//DOC/FMX/DOC.SUB.PUB[1]/@SYNOPTISM")
            .isEqualTo(firstSubPub.getSYNOPTISM());
        assertThat(xml).valueByXPath("//DOC/FMX/DOC.SUB.PUB[1]/PAGE.FIRST")
            .isEqualTo(firstSubPub.getPAGEFIRST());
        assertThat(xml).valueByXPath("//DOC/FMX/DOC.SUB.PUB[1]/PAGE.LAST")
            .isEqualTo(firstSubPub.getPAGELAST());
        assertThat(xml).valueByXPath("//DOC/FMX/DOC.SUB.PUB[1]/PAGE.TOTAL")
            .isEqualTo(firstSubPub.getPAGETOTAL().toString());
    }

    @Test
    @DisplayName("Should not have PUBLICATION.REF.SE element")
    void shouldNotHavePublicationRefSe() {
        assertThat( xml ).doesNotHaveXPath( "//DOC/PUBLICATION.REF.SE" );
    }

    @Test
    @DisplayName("Should have PAPER and PDF elements")
    void shouldHavePaperAndPdf() {
        PDF pdf = doc.getPDF();
        // Verify top-level PAPER and PDF elements exist
        assertThat( xml ).hasXPath( "//DOC/PAPER" );
        assertThat( xml ).hasXPath( "//DOC/PDF" );

        // Verify PAPER structure
        assertThat( xml ).hasXPath( "//DOC/PAPER/VOLUME.PAPER" );
        assertThat( xml ).hasXPath( "//DOC/PAPER/VOLUME.PAPER/ITEM.VOLUME" );

        // Verify PDF structure and reference match object model
        assertThat( xml ).hasXPath( "//DOC/PDF/ITEM.VOLUME" );
        assertThat( xml ).hasXPath( "//DOC/PDF/ITEM.VOLUME/ITEM.REF" );
        assertThat( xml ).valueByXPath( "//DOC/PDF/ITEM.VOLUME/ITEM.REF/@REF.PDF" )
            .isEqualTo( pdf.getITEMVOLUMES().getFirst().getITEMREF().getREFPDF() );
    }

    @Test
    @DisplayName("Should have proper file structure references")
    void shouldHaveProperFileReferences() {
        FMX fmx = doc.getFMX();
        DOCMAINPUB mainPub = fmx.getDOCMAINPUB();

// Verify main publication physical reference matches object model
        assertThat( xml ).hasXPath( "//DOC/FMX/DOC.MAIN.PUB/REF.PHYS" );
        assertThat( xml ).valueByXPath( "//DOC/FMX/DOC.MAIN.PUB/REF.PHYS/@FILE" )
            .isEqualTo( mainPub.getREFPHYS().getFirst().getFILE() );
        assertThat( xml ).valueByXPath( "//DOC/FMX/DOC.MAIN.PUB/REF.PHYS/@TYPE" )
            .isEqualTo( mainPub.getREFPHYS().getFirst().getTYPE() );

        // Verify all sub-publication physical references match object model
        assertThat( xml ).nodesByXPath( "//DOC/FMX/DOC.SUB.PUB/REF.PHYS" ).hasSize( fmx.getDOCSUBPUBS().size() );
        assertThat( xml ).valueByXPath( "//DOC/FMX/DOC.SUB.PUB[1]/REF.PHYS/@TYPE" )
            .isEqualTo( fmx.getDOCSUBPUBS().get( 0 ).getREFPHYS().getFirst().getTYPE() );
        assertThat( xml ).valueByXPath( "//DOC/FMX/DOC.SUB.PUB[2]/REF.PHYS/@TYPE" )
            .isEqualTo( fmx.getDOCSUBPUBS().get( 1 ).getREFPHYS().getFirst().getTYPE() );
        assertThat( xml ).valueByXPath( "//DOC/FMX/DOC.SUB.PUB[3]/REF.PHYS/@TYPE" )
            .isEqualTo( fmx.getDOCSUBPUBS().get( 2 ).getREFPHYS().getFirst().getTYPE() );
        assertThat( xml ).valueByXPath( "//DOC/FMX/DOC.SUB.PUB[4]/REF.PHYS/@TYPE" )
            .isEqualTo( fmx.getDOCSUBPUBS().get( 3 ).getREFPHYS().getFirst().getTYPE() );

    }
}