package com.kludgeworks.transformation.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class LegislationDateTest {

    @Test
    fun `should parse ISO date correctly`() {
        val date = LegislationDate(display = "5 November 2015", iso = "20151105")
        
        assertEquals("5 November 2015", date.display)
        assertEquals("20151105", date.iso)
        assertEquals(LocalDate.of(2015, 11, 5), date.localDate)
    }
    
    @Test
    fun `should format date correctly`() {
        val date = LegislationDate(display = "1 February 2016", iso = "20160201")
        
        assertEquals("01/02/2016", date.format("dd/MM/yyyy"))
        assertEquals("2016-02-01", date.format("yyyy-MM-dd"))
    }
    
    @Test
    fun `should compare dates correctly`() {
        val earlierDate = LegislationDate(display = "5 November 2015", iso = "20151105")
        val laterDate = LegislationDate(display = "1 February 2016", iso = "20160201")
        
        assertTrue(earlierDate.isBefore(laterDate))
        assertFalse(earlierDate.isAfter(laterDate))
        assertTrue(laterDate.isAfter(earlierDate))
        assertFalse(laterDate.isBefore(earlierDate))
    }
    
    @Test
    fun `should handle same date display as ISO when no human readable text`() {
        val date = LegislationDate(display = "20151105", iso = "20151105")
        
        assertEquals("20151105", date.display)
        assertEquals("20151105", date.iso)
        assertEquals(LocalDate.of(2015, 11, 5), date.localDate)
    }
}