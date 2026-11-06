///usr/bin/env jbang "$0" "$@" ; exit $?
// SPDX-License-Identifier: MIT
//MISE description="Check that XML files are well-formed"
//MISE alias="lint:xml"
//MISE quiet=true
//USAGE arg "<file>..." help="The XML files to check: stylesheets, schemas, XSpec suites, any XML"
//JAVA 25

// Well-formedness only, as xmllint --noout checks it, with the JDK's parser: the one Saxon and the
// converter's tests read these files with in the build, and a tool mise already pins (java), where
// xmllint would come from the system. Not validating, and no DTD fetched: a file's DOCTYPE is
// declared, not followed. hk runs it (hk.pkl, the xml step); a finding is gcc's path:line:column.

import java.io.IOException;
import java.nio.file.Path;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

void main(String... paths) throws ParserConfigurationException, SAXException {
  var factory = SAXParserFactory.newInstance();
  factory.setNamespaceAware(true);
  factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
  var parser = factory.newSAXParser();
  int malformed = 0;
  for (var path : paths) {
    try {
      parser.reset();
      parser.parse(Path.of(path).toFile(), new DefaultHandler());
    } catch (SAXParseException e) {
      malformed++;
      System.err.printf(
          "%s:%d:%d: error: %s%n", path, e.getLineNumber(), e.getColumnNumber(), e.getMessage());
    } catch (IOException e) {
      malformed++;
      System.err.printf("%s: error: %s%n", path, e.getMessage());
    }
  }
  if (malformed > 0) {
    System.exit(1);
  }
}
