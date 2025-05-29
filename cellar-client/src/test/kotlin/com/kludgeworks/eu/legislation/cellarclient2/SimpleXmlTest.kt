package com.kludgeworks.eu.legislation.cellarclient2

import com.kludgeworks.eu.legislation.cellarclient2.models.Notice
import kotlinx.coroutines.test.runTest
import nl.adaptivity.xmlutil.serialization.XML
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SimpleXmlTest {

    @Test
    fun `test XML deserialization with local data`() = runTest {
        val xmlString = javaClass.getResourceAsStream("/notice_sample.xml")?.bufferedReader()?.readText()
            ?: throw IllegalStateException("Could not read notice_sample.xml from resources")

        val xml = XML {}
        val notice = xml.decodeFromString(Notice.serializer(), xmlString)

        assertNotNull(notice)
        assertEquals("eng", notice.decoding)
        assertEquals("tree", notice.type)
        assertNotNull(notice.work)
        assertNotNull(notice.work.uri)
        assertEquals("http://publications.europa.eu/resource/cellar/ebb1a9b2-a87f-11e5-b528-01aa75ed71a1", notice.work.uri.value)

        println("notice = $notice")
    }
}
