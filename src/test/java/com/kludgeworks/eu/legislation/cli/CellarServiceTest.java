package com.kludgeworks.eu.legislation.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
class CellarServiceTest {

    @Test
    void retrieveResourceLegal() {
        CellarService service = new CellarService();
        ResourceLegalData resourceLegalData = service.retrieveResourceLegal("32015R1998");


        System.out.println("resourceLegalData.work() = " + resourceLegalData.work());
        System.out.println("resourceLegalData.amendments().size() = " + resourceLegalData.amendments().size());
        System.out.println("resourceLegalData.corrections().size() = " + resourceLegalData.corrections().size());
    }
}