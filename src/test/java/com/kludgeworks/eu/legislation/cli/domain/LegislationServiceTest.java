package com.kludgeworks.eu.legislation.cli.domain;

import java.util.List;
import java.util.Optional;

import com.kludgeworks.eu.legislation.cli.Legislation_32015R1998;
import com.kludgeworks.eu.legislation.cli.domain.mapper.SparqlResultMapperImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.kludgeworks.eu.legislation.cli.Legislation_32015R1998.*;

class LegislationServiceTest {

    private LegislationService legislationService;

    @BeforeEach
    void setUp() {
        legislationService = new LegislationService(new SparqlResultMapperImpl());
    }

    @Test
    void findExpression() throws LegislationServiceException {
        final Optional<String> expression = legislationService.findExpression(CELEX, "eng");
        Assertions.assertTrue(expression.isPresent());
        Assertions.assertEquals(EXPRESSION, expression.get());
    }
    
    @Test
    void findExpressionNonExistent() throws LegislationServiceException {
        final Optional<String> expression = legislationService.findExpression(CELEX, "xyz");
        Assertions.assertTrue(expression.isEmpty());
    }

    @Test
    void getManifestations() {
        List<Manifestation> manifestations = legislationService.getManifestations(EXPRESSION);
        Assertions.assertEquals(MANIFESTATIONS, manifestations);
    }

    @Test
    void getContentItems() {
        List<ContentItem> contentItems = legislationService.getContentItems(FMX4_MANIFESTATION);
        Assertions.assertEquals(CONTENT_ITEMS, contentItems);
    }

    @Test
    void getAmendments() {
        List<Amendment> amendments = legislationService.getAmendments(CELEX);
        Assertions.assertEquals(AMENDMENTS, amendments);
    }

    @Test
    void getConsolidations() {
        List<Consolidation> consolidations = legislationService.getConsolidations(CELEX);
        Assertions.assertEquals(Legislation_32015R1998.CONSOLIDATIONS, consolidations);
    }
}