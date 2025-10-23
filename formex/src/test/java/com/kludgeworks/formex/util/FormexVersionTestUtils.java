package com.kludgeworks.formex.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FormexVersionTestUtils {
    
    private static final String VERSIONS_BASE_PATH = "/formex/versions";
    
    /**
     * Generic method to list resource contents with a custom filter predicate.
     * Handles both JAR and filesystem resources.
     */
    private static List<String> listResourceContents(String resourcePath, Predicate<Path> filter, String errorMessage) {
        try {
            URI uri = Objects.requireNonNull( FormexVersionTestUtils.class.getResource( resourcePath ) ).toURI();
            Path path;
            
            if (uri.getScheme().equals("jar")) {
                FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
                path = fileSystem.getPath(resourcePath);
            } else {
                path = Paths.get(uri);
            }
            
            List<String> results = new ArrayList<>();
            try (Stream<Path> stream = Files.list(path)) {
                stream.filter(filter)
                      .map(p -> p.getFileName().toString())
                      .sorted()
                      .forEach(results::add);
            }
            
            return results;
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(errorMessage, e);
        }
    }
    
    public static List<String> discoverFormexVersions() {
        return listResourceContents(VERSIONS_BASE_PATH, Files::isDirectory, "Failed to discover Formex versions");
    }
    
    public static List<String> getXmlFilesForVersion(String version) {
        String versionPath = VERSIONS_BASE_PATH + "/" + version;
        Predicate<Path> xmlFilter = path -> Files.isRegularFile(path) && 
                                           path.toString().toLowerCase().endsWith(".xml");
        return listResourceContents(versionPath, xmlFilter, "Failed to get XML files for version: " + version);
    }
    
    public static String buildResourcePath(String version, String fileName) {
        return VERSIONS_BASE_PATH + "/" + version + "/" + fileName;
    }
    
    public static List<String> getDocXmlFiles(String version) {
        return getFilesByRootElement(version, "DOC");
    }
    
    public static List<String> getActXmlFiles(String version) {
        return getFilesByRootElement(version, "ACT");
    }
    
    public static List<String> getAnnexXmlFiles(String version) {
        return getFilesByRootElement(version, "ANNEX");
    }
    
    public static List<String> getPublicationXmlFiles(String version) {
        return getFilesByRootElement(version, "PUBLICATION");
    }
    
    public static List<String> getFilesByRootElement(String version, String rootElementName) {
        return getXmlFilesForVersion(version).stream()
            .filter(fileName -> hasRootElement(version, fileName, rootElementName))
            .toList();
    }
    
    private static boolean hasRootElement(String version, String fileName, String rootElementName) {
        try {
            String path = buildResourcePath(version, fileName);
            String extractedRootElement = extractRootElementName(path);
            return rootElementName.equals(extractedRootElement);
        } catch (Exception e) {
            return false;
        }
    }
    
    private static String extractRootElementName(String resourcePath) {
        try (InputStream inputStream = FormexVersionTestUtils.class.getResourceAsStream(resourcePath)) {
            assert inputStream != null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

                String content = reader.lines().collect(Collectors.joining());
                Pattern rootElementPattern = Pattern.compile("<([A-Z]+)(?:\\s|>)");
                Matcher matcher = rootElementPattern.matcher(content);
                return matcher.find() ? matcher.group(1) : "";
            }
        }
        catch ( IOException e) {
            throw new RuntimeException("Failed to read resource: " + resourcePath, e);
        }
    }
}