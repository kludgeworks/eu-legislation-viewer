package com.kludgeworks.formex.util;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

public class XmlTestUtils {


    public static <T> T unmarshalXmlFromClasspath(String resourcePath, Class<T> clazz) {
        try {
            InputStream inputStream = XmlTestUtils.class.getResourceAsStream( resourcePath );
            if ( inputStream == null ) {
                throw new IllegalArgumentException( "Resource not found: " + resourcePath );
            }

            JAXBContext jaxbContext = JAXBContext.newInstance( clazz );
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

            return clazz.cast( unmarshaller.unmarshal( inputStream ) );
        }
        catch ( Exception e ) {
            throw new RuntimeException( "Failed to unmarshal XML from classpath: " + resourcePath, e );
        }
    }

    public static <T> String marshalToString(T object) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance( object.getClass() );
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty( Marshaller.JAXB_FORMATTED_OUTPUT, true );

            StringWriter stringWriter = new StringWriter();
            marshaller.marshal( object, stringWriter );
            return stringWriter.toString();
        }
        catch ( Exception e ) {
            throw new RuntimeException( "Failed to marshal object to XML string", e );
        }
    }

    public static String readXmlFromClasspath(String resourcePath) {
        try {
            InputStream inputStream = XmlTestUtils.class.getResourceAsStream(resourcePath);
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to read XML from classpath: " + resourcePath, e);
        }
    }
}