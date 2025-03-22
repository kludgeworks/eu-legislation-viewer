package com.kludgeworks.eu.legislation.cli.local.filehandling2;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

@Component
public class XmlProcessor implements PostDownloadProcessor {

    private static final Logger logger = LoggerFactory.getLogger(XmlProcessor.class);

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && (mimeType.contains("application/xml") ||
            mimeType.contains("text/xml"));
//            mimeType.endsWith("+xml"));
    }

    @Override
    public ProcessingResult process(Path path, String mimeType) throws IOException {
        logger.info("Formatting XML file: {}", path);

        try {
            // Read the XML file into a DOM Document
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            Document document = factory.newDocumentBuilder().parse(path.toFile());

            // Set up a transformer for pretty printing
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            // Transform the DOM Document to a pretty-printed string
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            String formattedXml = writer.toString();

            // Write back to the file
            Files.writeString(path, formattedXml);

            logger.info("Successfully formatted XML: {}", path);
            // Return processing result indicating this is not an archive
            return ProcessingResult.builder().isArchive( false ).processedFile( path ).build();

        } catch (TransformerException | SAXException | ParserConfigurationException exception) {
            throw new IOException("Failed to transform XML", exception);
        }
    }
}