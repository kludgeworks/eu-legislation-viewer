// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.XmlLegalText;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SAXDestination;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

/**
 * Reads legal-text XML, as {@link FormexConverter} writes it, into the domain records: Saxon hands
 * the tree to JAXB as SAX events, with no serialisation in between, and {@link LegalTextMapper}
 * maps the generated classes onto the records. One instance serves concurrent calls.
 */
public final class LegalTextReader {

  private static final JAXBContext JAXB = jaxb();

  private final Processor processor;
  private final LegalTextMapper mapper = new LegalTextMapperImpl();

  public LegalTextReader(Processor processor) {
    this.processor = processor;
  }

  /**
   * @param legalText a legal-text document, valid against legal-text.xsd
   * @return its records
   * @throws ConversionException if it can't be read as legal text
   */
  public LegalText read(XdmNode legalText) {
    try {
      var handler = JAXB.createUnmarshaller().getUnmarshallerHandler();
      processor.writeXdmValue(legalText, new SAXDestination(handler));
      var element = (JAXBElement<?>) handler.getResult();
      return mapper.legalText((XmlLegalText) element.getValue());
    } catch (JAXBException | SaxonApiException | ClassCastException e) {
      throw new ConversionException("not legal text: " + e.getMessage(), e);
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
