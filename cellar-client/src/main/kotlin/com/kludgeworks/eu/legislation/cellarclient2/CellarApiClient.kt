package com.kludgeworks.eu.legislation.cellarclient2

import com.kludgeworks.eu.legislation.cellarclient2.models.Notice
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CellarApiClient(
    private val httpClient: HttpClient)
{
    private val logger = LoggerFactory.getLogger(CellarApiClient::class.java)

    suspend fun fetchNotice(): Notice? {
        return try {
            val response = httpClient.get("http://publications.europa.eu/resource/cellar/ebb1a9b2-a87f-11e5-b528-01aa75ed71a1?language=eng") {
                headers {
                    append(HttpHeaders.Accept, "application/xml;notice=tree")
                }
            }

            return response.body<Notice>();
        } catch (e: Exception) {
            println("Error fetching user with Ktor serialization: ${e.message}")
            null
        }
    }
}
