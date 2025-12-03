package com.kludgeworks.eu.legislation.formex.downloader

import com.kludgeworks.eu.legislation.formex.downloader.config.SparqlProperties
import com.kludgeworks.eu.legislation.formex.downloader.config.StorageProperties
import com.kludgeworks.eu.legislation.formex.downloader.domain.CellarData
import com.univocity.parsers.csv.CsvParser
import com.univocity.parsers.csv.CsvParserSettings
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Spring Boot application entry point for Formex XML ingester.
 *
 * This application uses Apache Camel (Spring Boot) to download and process Formex XML documents
 * from the EU SPARQL endpoint.
 */
@SpringBootApplication
@EnableConfigurationProperties(SparqlProperties::class, StorageProperties::class)
class FormexDownloaderApplication

@Suppress("SpreadOperator") // Required by Spring Boot runApplication API
fun main(args: Array<String>) {
    runApplication<FormexDownloaderApplication>(*args)
}

