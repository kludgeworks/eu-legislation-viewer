package com.kludgeworks.eu.legislation.cellarclient

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import io.ktor.serialization.kotlinx.xml.*
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML

@Configuration
class KtorConfiguration {

    @Bean
    fun ktorHttpClient(): HttpClient {
        return HttpClient(CIO) {
            install(ContentNegotiation) {
                xml(format = XML {
                    // Configure XML parsing to be less strict
                    unknownChildHandler = UnknownChildHandler { _, _, _, _, _ -> emptyList() }
                })
                xml(format = XML {
                    unknownChildHandler = UnknownChildHandler { _, _, _, _, _ -> emptyList() }
                }, contentType = ContentType.parse("application/xml;notice=tree"))
            }
            engine {
                requestTimeout = 30_000
            }
        }
    }
}
