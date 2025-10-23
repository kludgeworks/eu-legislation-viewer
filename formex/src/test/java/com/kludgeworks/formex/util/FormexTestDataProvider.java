package com.kludgeworks.formex.util;

import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

public class FormexTestDataProvider {
    
    public static Stream<Arguments> docTestData() {
        return FormexVersionTestUtils.discoverFormexVersions().stream()
            .flatMap(version -> FormexVersionTestUtils.getDocXmlFiles(version).stream()
                .map(fileName -> Arguments.of(version, fileName)));
    }
    
    public static Stream<Arguments> actTestData() {
        return FormexVersionTestUtils.discoverFormexVersions().stream()
            .flatMap(version -> FormexVersionTestUtils.getActXmlFiles(version).stream()
                .map(fileName -> Arguments.of(version, fileName)));
    }
    
    public static Stream<Arguments> annexTestData() {
        return FormexVersionTestUtils.discoverFormexVersions().stream()
            .flatMap(version -> FormexVersionTestUtils.getAnnexXmlFiles(version).stream()
                .map(fileName -> Arguments.of(version, fileName)));
    }
    
    public static Stream<Arguments> publicationTestData() {
        return FormexVersionTestUtils.discoverFormexVersions().stream()
            .flatMap(version -> FormexVersionTestUtils.getPublicationXmlFiles(version).stream()
                .map(fileName -> Arguments.of(version, fileName)));
    }
    
    public static Stream<Arguments> allXmlTestData() {
        return FormexVersionTestUtils.discoverFormexVersions().stream()
            .flatMap(version -> FormexVersionTestUtils.getXmlFilesForVersion(version).stream()
                .map(fileName -> Arguments.of(version, fileName)));
    }
    
    public static Stream<String> allVersions() {
        return FormexVersionTestUtils.discoverFormexVersions().stream();
    }
}