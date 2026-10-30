// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import dev.groknull.kludgeworks.build.Schematron.Finding;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmValue;

/**
 * xslt-quality (github.com/mricaud/xslt-quality), Schematron rules for XSLT: its validator as
 * XsltQualityBuild built it, on the classpath under /xslt-quality/.
 */
final class XsltQuality {
  private static final String XSLQ = "https://github.com/mricaud/xsl-quality";
  private static final String VALIDATOR = "/xslt-quality/sch/validator.xsl";
  private static final String CONF = "/xslt-quality/conf/xslt-quality.conf.xml";

  private final Schematron schema;
  private final Map<QName, XdmValue> parameters;

  private XsltQuality(Schematron schema, Map<QName, XdmValue> parameters) {
    this.schema = schema;
    this.parameters = parameters;
  }

  static XsltQuality load(Processor saxon) throws SaxonApiException {
    // our configuration replaces the default; current-sch.filename is required regardless
    return new XsltQuality(
        Schematron.load(saxon, resource(VALIDATOR)),
        Map.of(
            new QName(XSLQ, "conf-default.uri"), new XdmAtomicValue(resource(CONF)),
            new QName("current-sch.filename"), new XdmAtomicValue("../conf/xslt-quality")));
  }

  /** The findings for one stylesheet. */
  List<Finding> check(Path stylesheet) throws SaxonApiException {
    return schema.validate(new StreamSource(stylesheet.toFile()), parameters);
  }

  private static String resource(String name) {
    return Objects.requireNonNull(XsltQuality.class.getResource(name), name).toExternalForm();
  }
}
