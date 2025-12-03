package com.kludgeworks.eu.legislation.formex.downloader.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class HttpClientConfig {
    companion object {
        private const val SPARQL_READ_TIMEOUT_MINUTES = 4L
        private const val XML_READ_TIMEOUT_SECONDS = 30L
    }

    @Bean
    fun sparqlRestClient(): RestClient =
        RestClient
            .builder()
            .requestFactory(
                JdkClientHttpRequestFactory().apply {
                    setReadTimeout(Duration.ofMinutes(SPARQL_READ_TIMEOUT_MINUTES))
                },
            ).build()

    @Bean
    fun xmlDownloadRestClient(): RestClient =
        RestClient
            .builder()
            .requestFactory(
                JdkClientHttpRequestFactory().apply {
                    setReadTimeout(Duration.ofSeconds(XML_READ_TIMEOUT_SECONDS))
                },
            ).build()
}
