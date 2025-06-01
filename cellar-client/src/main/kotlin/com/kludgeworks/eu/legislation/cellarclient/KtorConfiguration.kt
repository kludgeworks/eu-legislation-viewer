package com.kludgeworks.eu.legislation.cellarclient

import dev.dirs.ProjectDirectories
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.cache.storage.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.xml.*
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File

@Configuration
class KtorConfiguration {

    @Bean
    fun ktorHttpClient(): HttpClient {
        return HttpClient(CIO) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
            }

            install(HttpCache) {
                val cacheDir = ProjectDirectories.from("", "kludgeworks", "EU Legislation Cache").configDir
                publicStorage(FileStorage(File(cacheDir)))
            }

            install(ContentNegotiation) {
                xml(format = XML {
                    /*
                    TODO we should probably configure this to be less strict at runtime
                    and then more strict during tests.

                    At runtime we wouldn't want a new element in the model to cause an issue, but at test time
                    it should fail so that we can update the model
                     */
                    unknownChildHandler = UnknownChildHandler { _, _, _, _, _ -> emptyList() }
                })
                xml(format = XML {
                    unknownChildHandler = UnknownChildHandler { _, _, _, _, _ -> emptyList() }
                }, contentType = ContentType.parse("application/xml;notice=tree"))
                json()
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = true
                }, contentType = ContentType.parse("application/sparql-results+json"))
            }
            engine {
                requestTimeout = 30_000
            }
        }
    }
}
