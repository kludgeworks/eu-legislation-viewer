// SPDX-License-Identifier: MIT
//JAVA 25

// mise run lint:xml <file>... (tools/tasks.toml), which runs this with jbang: not executable itself,
// since a Java file can't start with #!, and jbang's ///usr/bin/env line is a shell's trick that
// Linux won't execute.
//
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
