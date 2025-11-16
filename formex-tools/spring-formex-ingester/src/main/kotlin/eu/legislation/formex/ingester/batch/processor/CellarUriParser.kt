package eu.legislation.formex.ingester.batch.processor

import java.net.URI

/**
 * Utility for parsing and extracting components from Cellar URIs.
 *
 * Cellar URIs follow the pattern:
 *   http://publications.europa.eu/resource/cellar/{manifestation_uuid}/{doc_id}
 *
 * Example:
 *   http://publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1
 *
 * This utility extracts the path components for file storage organization:
 *   publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1
 */
object CellarUriParser {
    private const val MIN_PATH_SEGMENTS = 4
    private const val RESOURCE_INDEX = 0
    private const val CELLAR_INDEX = 1
    private const val MANIFESTATION_INDEX = 2
    private const val DOC_ID_INDEX = 3
    private const val PATH_COMPONENTS_END_INDEX = 3

    /**
     * Represents the parsed components of a Cellar URI.
     *
     * @property host The host part of the URI, including port if present (e.g., "publications.europa.eu" or "localhost:65254")
     * @property pathComponents The path segments after the host (e.g., ["resource", "cellar", "{manifestation}"])
     * @property manifestationId The manifestation UUID with version (e.g., "16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02")
     * @property docId The document identifier (e.g., "DOC_1")
     */
    data class ParsedCellarUri(
        val host: String,
        val pathComponents: List<String>,
        val manifestationId: String,
        val docId: String,
    ) {
        /**
         * Constructs the relative file path for storage.
         *
         * Format: host/resource/cellar/{manifestation}/{docId}.xml
         *
         * Example: publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1.xml
         *
         * @return The relative path to use for file storage
         */
        fun toRelativePath(): String =
            buildString {
                append(host)
                pathComponents.forEach { component ->
                    append("/")
                    append(component)
                }
                append("/")
                append(docId)
                append(".xml")
            }
    }

    /**
     * Parses a Cellar URI and extracts its components.
     *
     * Expected URI format:
     *   http://publications.europa.eu/resource/cellar/{manifestation_uuid}/{doc_id}
     *
     * @param uriString The full URI as a string
     * @return ParsedCellarUri containing extracted components
     * @throws IllegalArgumentException if the URI doesn't match the expected Cellar format
     */
    fun parse(uriString: String): ParsedCellarUri {
        val uri = URI(uriString)

        // Extract host (including port if present)
        // Use authority instead of host to preserve port numbers for localhost/test servers
        val host =
            uri.authority
                ?: throw IllegalArgumentException("URI must have a host: $uriString")

        // Parse path components
        val path =
            uri.path
                ?: throw IllegalArgumentException("URI must have a path: $uriString")

        val pathSegments = path.split("/").filter { it.isNotBlank() }

        // Cellar URIs should have at least: resource, cellar, manifestation, doc_id
        require(pathSegments.size >= MIN_PATH_SEGMENTS) {
            "URI path must have at least $MIN_PATH_SEGMENTS segments " +
                "[resource, cellar, manifestation, doc_id]: $uriString"
        }

        // Verify "resource" and "cellar" are in the path
        require(pathSegments[RESOURCE_INDEX] == "resource" && pathSegments[CELLAR_INDEX] == "cellar") {
            "URI path must start with /resource/cellar: $uriString"
        }

        // Extract manifestation (third segment) and doc_id (fourth segment)
        val manifestationId = pathSegments[MANIFESTATION_INDEX]
        val docId = pathSegments[DOC_ID_INDEX]

        // Build list of path components to preserve in storage
        val pathComponents = pathSegments.subList(RESOURCE_INDEX, PATH_COMPONENTS_END_INDEX)

        return ParsedCellarUri(
            host = host,
            pathComponents = pathComponents,
            manifestationId = manifestationId,
            docId = docId,
        )
    }
}
