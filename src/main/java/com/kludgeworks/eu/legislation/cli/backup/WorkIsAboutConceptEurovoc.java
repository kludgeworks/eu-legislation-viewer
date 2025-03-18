package com.kludgeworks.eu.legislation.cli.backup;

public record WorkIsAboutConceptEurovoc(
        WorkIsAboutConceptEurovocConcept concept,
        WorkIsAboutConceptEurovocTT tt,
        WorkIsAboutConceptEurovocDom dom,
        WorkIsAboutConceptEurovocMth mth,
        String type
) {}
