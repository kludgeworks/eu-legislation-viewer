package com.kludgeworks.eu.legislation.cellarclient

import com.kludgeworks.eu.legislation.cellarclient.models.Expression
import com.kludgeworks.eu.legislation.cellarclient.models.Manifestation
import com.kludgeworks.eu.legislation.cellarclient.models.Notice
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CellarApiClient(
    private val httpClient: HttpClient)
{
    private val logger = LoggerFactory.getLogger(CellarApiClient::class.java)

    // Base URL for the Cellar API
    private val baseUrl = "http://publications.europa.eu/resource/cellar/"

    /**
     * Fetches a notice from the Cellar API for the given resource ID
     * 
     * @param resourceId The unique identifier of the resource to fetch
     * @return The Notice object if found, or null if the resource doesn't exist (404)
     * @throws CellarApiException If there's an error during the API call
     */
    suspend fun fetchNotice(resourceId: String): Notice? {
        try {

            val response = httpClient.get("$baseUrl$resourceId") {
                headers {
                    append(HttpHeaders.Accept, "application/xml;notice=tree")
                }
            }

            if (response.status == HttpStatusCode.NotFound) {
                logger.info("Resource not found (404)")
                return null
            }

            return response.body<Notice>();
        } catch (e: ClientRequestException) {
            // If it's a 404 Not Found, return null as specified
            if (e.response.status == HttpStatusCode.NotFound) {
                logger.info("Resource not found (404): ${e.message}")
                return null
            } else {
                // For other client errors (4xx), throw a custom exception
                logger.error("Client error fetching notice: ${e.message}", e)
                throw CellarApiException("Error fetching notice: ${e.message}", e)
            }
        } catch (e: Exception) {
            // For all other exceptions, throw a custom exception
            logger.error("Error fetching notice: ${e.message}", e)
            throw CellarApiException("Error fetching notice: ${e.message}", e)
        }
    }

    /**
     * Retrieves expressions for a specific language from the notice
     * @param resourceId The unique identifier of the resource to fetch
     * @param languageCode The ISO language code (e.g., "eng" for English)
     * @return List of expressions in the specified language, or empty list if none found
     */
    suspend fun getExpressionsByLanguage(resourceId: String, languageCode: String): List<Expression> {
        val notice = fetchNotice(resourceId) ?: return emptyList()
        return notice.expression.filter { it.language?.value == languageCode }
    }

    /**
     * Retrieves all manifestations for a specific expression
     * @param resourceId The unique identifier of the resource to fetch
     * @param expressionUri The URI of the expression for which to find manifestations
     * @return List of manifestations for the given expression, or empty list if none found
     */
    suspend fun getManifestationsForExpression(resourceId: String, expressionUri: String): List<Manifestation> {
        val notice = fetchNotice(resourceId) ?: return emptyList()

        // Find all manifestations that reference this expression
        return notice.manifestation.filter { manifestation ->
            manifestation.manifestsExpression?.uri?.value == expressionUri
        }
    }
}
