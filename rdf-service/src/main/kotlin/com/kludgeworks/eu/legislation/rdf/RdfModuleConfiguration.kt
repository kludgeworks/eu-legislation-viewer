package com.kludgeworks.eu.legislation.rdf

import com.kludgeworks.eu.legislation.rdf.config.RdfProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.modulith.ApplicationModule

@Configuration
@ApplicationModule(
    allowedDependencies = ["cdm", "arrow-core", "arrow-fx-coroutines", "rdf4j::"]
)
@EnableConfigurationProperties(RdfProperties::class)
@ComponentScan
class RdfModuleConfiguration