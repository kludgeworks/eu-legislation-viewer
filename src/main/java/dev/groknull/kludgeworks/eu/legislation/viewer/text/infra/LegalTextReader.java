// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlLegalText;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SAXDestination;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import org.xml.sax.SAXException;

/**
 * Reads legal-text XML, as the Formex converter writes it, into the domain records: Saxon hands the
 * tree to JAXB as SAX events, with no serialisation in between, and {@link LegalTextMapper} maps
 * the generated classes onto the records. The document is validated against legal-text.xsd as it is
 * read, so a value the schema doesn't know (a newer document's unit kind) fails here instead of
 * becoming null: adding a value to the schema is a major version. One instance serves concurrent
 * calls.
 */
public final class LegalTextReader {

  private static final JAXBContext JAXB = jaxb();
  private static final Schema SCHEMA = schema();

  private final Processor processor;
  private final LegalTextMapper mapper = new LegalTextMapperImpl();

  public LegalTextReader(Processor processor) {
    this.processor = processor;
  }

  /**
   * @param legalText a legal-text document, valid against legal-text.xsd
   * @return its records
   * @throws InvalidLegalTextException if it can't be read as legal text
   */
  public LegalText read(XdmNode legalText) {
    try {
      var unmarshaller = JAXB.createUnmarshaller();
      unmarshaller.setSchema(SCHEMA);
      var handler = unmarshaller.getUnmarshallerHandler();
      processor.writeXdmValue(legalText, new SAXDestination(handler));
      var element = (JAXBElement<?>) handler.getResult();
      return mapper.legalText((XmlLegalText) element.getValue());
    } catch (JAXBException | SaxonApiException | ClassCastException e) {
      throw new InvalidLegalTextException("not legal text: " + e.getMessage(), e);
    }
  }

  private static Schema schema() {
    var resource = "/formex-converter/schema/legal-text.xsd";
    var url = Objects.requireNonNull(LegalTextReader.class.getResource(resource), resource);
    try {
      return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(url);
    } catch (SAXException e) {
      throw new IllegalStateException("legal-text.xsd doesn't load", e);
    }
  }

  private static JAXBContext jaxb() {
    try {
      return JAXBContext.newInstance(
          XmlLegalText.class.getPackageName(), XmlLegalText.class.getClassLoader());
    } catch (JAXBException e) {
      throw new IllegalStateException("the JAXB classes of legal-text.xsd don't load", e);
    }
  }
}
