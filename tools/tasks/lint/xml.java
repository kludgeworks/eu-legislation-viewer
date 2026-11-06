///usr/bin/env jbang "$0" "$@" ; exit $?
// SPDX-License-Identifier: MIT
//JAVA 25

// mise run lint:xml (tools/tasks.toml; its help in xml.usage.kdl). Not executable, so that mise
// doesn't take it for a file task too: Linux won't exec the line above, a shell's trick.

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
