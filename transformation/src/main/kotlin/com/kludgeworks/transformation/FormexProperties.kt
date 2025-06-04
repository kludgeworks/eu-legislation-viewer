package com.kludgeworks.transformation

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "formex")
data class FormexProperties(
    /**
     * Classpath or file path to the XSLT stylesheet for transformation.
     */
    val xsltPath: String = "classpath:xslt/formex-transformer.xslt",

    /**
     * Classpath or file path to the XSD schema for validation.
     */
    val xsdPath: String = "classpath:xsd/simplified-legislation.xsd"
)