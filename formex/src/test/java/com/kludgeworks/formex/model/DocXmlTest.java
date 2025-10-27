package com.kludgeworks.formex.model;

import com.kludgeworks.formex.util.FormexTestDataProvider;
import com.kludgeworks.formex.util.FormexVersionTestUtils;
import com.kludgeworks.formex.util.XmlTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.xmlunit.assertj.XmlAssert.assertThat;

@SuppressWarnings("java:S5853") // XML assertions don't support chaining
@DisplayName("DOC XML Validation Tests")
class DocXmlTest {

    static Stream<Arguments> docTestData() {
        return FormexTestDataProvider.docTestData();
    }

    @ParameterizedTest
    @MethodSource("docTestData")
    @DisplayName("Should unmarshal DOC files successfully")
    void shouldUnmarshalSuccessfully(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        DOC doc = XmlTestUtils.unmarshalXmlFromClasspath(resourcePath, DOC.class);
        assertThat( doc ).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("docTestData")
    @DisplayName("Should have valid BIB.DOC element with correct values")
    void shouldHaveValidBibDoc(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        DOC doc = XmlTestUtils.unmarshalXmlFromClasspath(resourcePath, DOC.class);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);
        
        BIBDOC bibDoc = doc.getBIBDOC();

        // Verify BIB.DOC identification values match object model
        assertThat( xml ).valueByXPath( "//DOC/BIB.DOC/PROD.ID" ).isEqualTo( bibDoc.getPRODID() );
        assertThat( xml ).valueByXPath( "//DOC/BIB.DOC/FIN.ID" ).isEqualTo( bibDoc.getFINID() );

        // Verify AUTHOR if present
        if (!bibDoc.getAUTHORS().isEmpty()) {
            assertThat( xml ).valueByXPath( "//DOC/BIB.DOC/AUTHOR" ).isEqualTo( bibDoc.getAUTHORS().getFirst() );
        }

        // Verify required structural elements exist
        assertThat( xml ).hasXPath( "//DOC/BIB.DOC/NO.DOC" );
        assertThat( xml ).hasXPath( "//DOC/BIB.DOC/EEA" );
    }

    @ParameterizedTest
    @MethodSource("docTestData")
    @DisplayName("Should have valid PUBLICATION.REF element with correct values")
    void shouldHaveValidPublicationRef(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);

        // Verify PUBLICATION.REF structure exists
        assertThat( xml ).hasXPath( "//DOC/PUBLICATION.REF" );
        assertThat( xml ).hasXPath( "//DOC/PUBLICATION.REF/COLL" );
        assertThat( xml ).hasXPath( "//DOC/PUBLICATION.REF/NO.OJ" );
        assertThat( xml ).hasXPath( "//DOC/PUBLICATION.REF/VOLUME.REF" );
        assertThat( xml ).hasXPath( "//DOC/PUBLICATION.REF/LG.OJ" );
        assertThat( xml ).hasXPath( "//DOC/PUBLICATION.REF/DATE/@ISO" );
    }

    @ParameterizedTest
    @MethodSource("docTestData")
    @DisplayName("Should have valid FMX structure")
    void shouldHaveFmxStructure(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        DOC doc = XmlTestUtils.unmarshalXmlFromClasspath(resourcePath, DOC.class);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);
        
        assertThat(xml).hasXPath("//DOC/FMX");
        assertThat(xml).hasXPath("//DOC/FMX/DOC.MAIN.PUB");
        assertThat(xml).nodesByXPath("//DOC/FMX/DOC.SUB.PUB")
            .hasSize(doc.getFMX().getDOCSUBPUBS().size());
    }

    @ParameterizedTest
    @MethodSource("docTestData")
    @DisplayName("Should have valid main publication attributes")
    void shouldHaveValidMainPublicationAttributes(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        DOC doc = XmlTestUtils.unmarshalXmlFromClasspath(resourcePath, DOC.class);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);
        
        DOCMAINPUB mainPub = doc.getFMX().getDOCMAINPUB();

        assertThat(xml).valueByXPath("//DOC/FMX/DOC.MAIN.PUB/@NO.SEQ")
            .isEqualTo(mainPub.getNOSEQ());
        assertThat(xml).valueByXPath("//DOC/FMX/DOC.MAIN.PUB/@SYNOPTISM")
            .isEqualTo(mainPub.getSYNOPTISM());
    }

    @ParameterizedTest
    @MethodSource("docTestData")
    @DisplayName("Should have valid main publication page information")
    void shouldHaveValidMainPublicationPages(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        DOC doc = XmlTestUtils.unmarshalXmlFromClasspath(resourcePath, DOC.class);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);
        
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

    @ParameterizedTest
    @MethodSource("docTestData")
    @DisplayName("Should have valid first sub-publication")
    void shouldHaveValidFirstSubPublication(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        DOC doc = XmlTestUtils.unmarshalXmlFromClasspath(resourcePath, DOC.class);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);

        // Skip test if document has no sub-publications (this is valid)
        if (doc.getFMX().getDOCSUBPUBS().isEmpty()) {
            return;
        }

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

    @ParameterizedTest
    @MethodSource("docTestData")
    @DisplayName("Should not have PUBLICATION.REF.SE element")
    void shouldNotHavePublicationRefSe(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);
        assertThat( xml ).doesNotHaveXPath( "//DOC/PUBLICATION.REF.SE" );
    }

    @ParameterizedTest
    @MethodSource("docTestData")
    @DisplayName("Should have PAPER and PDF elements")
    void shouldHavePaperAndPdf(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        DOC doc = XmlTestUtils.unmarshalXmlFromClasspath(resourcePath, DOC.class);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);
        
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

    @ParameterizedTest
    @MethodSource("docTestData")
    @DisplayName("Should have proper file structure references")
    void shouldHaveProperFileReferences(String version, String fileName) {
        String resourcePath = FormexVersionTestUtils.buildResourcePath(version, fileName);
        DOC doc = XmlTestUtils.unmarshalXmlFromClasspath(resourcePath, DOC.class);
        String xml = XmlTestUtils.readXmlFromClasspath(resourcePath);
        
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
        
        // Dynamically verify each sub-publication based on actual count
        for (int i = 0; i < fmx.getDOCSUBPUBS().size(); i++) {
            int xpathIndex = i + 1; // XPath is 1-based
            assertThat( xml ).valueByXPath( "//DOC/FMX/DOC.SUB.PUB[" + xpathIndex + "]/REF.PHYS/@TYPE" )
                .isEqualTo( fmx.getDOCSUBPUBS().get( i ).getREFPHYS().getFirst().getTYPE() );
        }

    }
}