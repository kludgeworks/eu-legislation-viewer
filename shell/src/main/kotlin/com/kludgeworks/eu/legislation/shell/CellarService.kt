package com.kludgeworks.eu.legislation.shell

import com.kludgeworks.eu.legislation.cellarclient.CellarApiClient
import com.kludgeworks.eu.legislation.cellarclient.models.Expression
import com.kludgeworks.eu.legislation.cellarclient.models.Manifestation
import com.kludgeworks.eu.legislation.cellarclient.models.Notice
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

/**
 * Service that wraps CellarApiClient and provides caching for its methods.
 */
@Service
class CellarService(
    private val cellarApiClient: CellarApiClient
) {
    /**
     * Fetches a notice from the Cellar API for the given resource ID with caching.
     *
     * @param resourceId The unique identifier of the resource to fetch
     * @return The Notice object if found, or null if the resource doesn't exist (404)
     */
    @Cacheable("notices")
    suspend fun fetchNotice(resourceId: String): Notice? {
        return cellarApiClient.fetchNotice(resourceId)
    }

    /**
     * Fetches a notice from the Cellar API for the given CELEX number with caching.
     *
     * @param celexNumber The CELEX number of the resource to fetch
     * @return The Notice object if found, or null if the resource doesn't exist (404)
     */
    @Cacheable("notices")
    suspend fun fetchNoticeByCelex(celexNumber: String): Notice? {
        return cellarApiClient.fetchNoticeByCelex(celexNumber)
    }

    /**
     * Retrieves expressions for a specific language from the notice with caching.
     *
     * @param resourceId The unique identifier of the resource to fetch
     * @param languageCode The ISO language code (e.g., "eng" for English)
     * @return List of expressions in the specified language, or empty list if none found
     */
    @Cacheable("expressionsByLanguage")
    suspend fun getExpressionsByLanguage(resourceId: String, languageCode: String): List<Expression> {
        return cellarApiClient.getExpressionsByLanguage(resourceId, languageCode)
    }

    /**
     * Retrieves all manifestations for a specific expression with caching.
     *
     * @param resourceId The unique identifier of the resource to fetch
     * @param expressionUri The URI of the expression for which to find manifestations
     * @return List of manifestations for the given expression, or empty list if none found
     */
    @Cacheable("manifestationsForExpression")
    suspend fun getManifestationsForExpression(resourceId: String, expressionUri: String): List<Manifestation> {
        return cellarApiClient.getManifestationsForExpression(resourceId, expressionUri)
    }
}