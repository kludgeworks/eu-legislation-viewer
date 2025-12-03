package com.kludgeworks.eu.legislation.formex.downloader.util
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Unit tests for CellarUriParser.
 *
 * Tests URI parsing and path extraction for various valid and invalid inputs.
 */
class CellarUriParserTest {
    @Test
    fun `parse should extract components from valid Cellar URI`() {
        // Given
        val uri = "http://publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1"

        // When
        val parsed = CellarUriParser.parse(uri)

        // Then
        assertEquals("publications.europa.eu", parsed.host)
        assertEquals("16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02", parsed.manifestationId)
        assertEquals("DOC_1", parsed.docId)
        assertEquals(
            listOf("resource", "cellar", "16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02"),
            parsed.pathComponents,
        )
    }

    @Test
    fun `parse should handle different doc IDs`() {
        // Given
        val uris =
            listOf(
                "http://publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1",
                "http://publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_2",
                "http://publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_10",
                "http://publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_100",
            )

        // When/Then
        uris.forEach { uri ->
            val parsed = CellarUriParser.parse(uri)
            val docId = uri.substringAfterLast("/")
            assertEquals(docId, parsed.docId)
        }
    }

    @Test
    fun `parse should handle different manifestation IDs`() {
        // Given
        val uris =
            listOf(
                "http://publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1",
                "http://publications.europa.eu/resource/cellar/2aff7602-1b5e-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1",
                "http://publications.europa.eu/resource/cellar/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.0001.01/DOC_1",
            )

        // When/Then
        uris.forEach { uri ->
            val parsed = CellarUriParser.parse(uri)
            val manifestation = uri.split("/").dropLast(1).last()
            assertEquals(manifestation, parsed.manifestationId)
        }
    }

    @Test
    fun `toRelativePath should construct correct path`() {
        // Given
        val uri = "http://publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1"
        val parsed = CellarUriParser.parse(uri)

        // When
        val relativePath = parsed.toRelativePath()

        // Then
        val expected = "publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1.xml"
        assertEquals(expected, relativePath)
    }

    @Test
    fun `toRelativePath should handle different doc IDs in path`() {
        // Given
        val testCases =
            listOf(
                "http://publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1" to
                    "publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1.xml",
                "http://publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_99" to
                    "publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_99.xml",
                "http://publications.europa.eu/resource/cellar/2aff7602-1b5e-11e7-aeb3-01aa75ed71a1.0006.02/DOC_5" to
                    "publications.europa.eu/resource/cellar/2aff7602-1b5e-11e7-aeb3-01aa75ed71a1.0006.02/DOC_5.xml",
            )

        // When/Then
        testCases.forEach { (uri, expectedPath) ->
            val parsed = CellarUriParser.parse(uri)
            val relativePath = parsed.toRelativePath()
            assertEquals(expectedPath, relativePath)
        }
    }

    @Test
    fun `parse should throw IllegalArgumentException for URI without host`() {
        // Given
        val uri = "/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1"

        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            CellarUriParser.parse(uri)
        }
    }

    @Test
    fun `parse should throw IllegalArgumentException for URI without resource-cellar path`() {
        // Given
        val invalidUris =
            listOf(
                "http://publications.europa.eu/other/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1",
                "http://publications.europa.eu/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1",
                "http://publications.europa.eu/resource/other/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1",
            )

        // When/Then
        invalidUris.forEach { uri ->
            assertThrows(IllegalArgumentException::class.java) {
                CellarUriParser.parse(uri)
            }
        }
    }

    @Test
    fun `parse should throw IllegalArgumentException for URI with insufficient path segments`() {
        // Given
        val invalidUris =
            listOf(
                "http://publications.europa.eu/resource",
                "http://publications.europa.eu/resource/cellar",
                "http://publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02",
            )

        // When/Then
        invalidUris.forEach { uri ->
            assertThrows(IllegalArgumentException::class.java) {
                CellarUriParser.parse(uri)
            }
        }
    }

    @Test
    fun `parse should handle HTTPS URIs`() {
        // Given
        val uri = "https://publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1"

        // When
        val parsed = CellarUriParser.parse(uri)

        // Then
        assertEquals("publications.europa.eu", parsed.host)
        assertEquals("16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02", parsed.manifestationId)
        assertEquals("DOC_1", parsed.docId)
    }

    @Test
    fun `parse should handle URIs with trailing slashes`() {
        // Given
        val uri = "http://publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1/"

        // When
        val parsed = CellarUriParser.parse(uri)

        // Then
        // Trailing slash creates empty segment which is filtered out
        assertEquals("publications.europa.eu", parsed.host)
    }

    @Test
    fun `parse should handle numeric doc IDs`() {
        // Given
        val uris =
            listOf(
                "http://publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/1",
                "http://publications.europa.eu/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/999",
            )

        // When/Then
        uris.forEach { uri ->
            val parsed = CellarUriParser.parse(uri)
            assertNotNull(parsed.docId)
            assertTrue(parsed.docId.matches(Regex("\\d+")))
        }
    }
}
