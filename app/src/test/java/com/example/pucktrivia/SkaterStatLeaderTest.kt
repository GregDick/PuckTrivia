package com.example.pucktrivia

import com.example.pucktrivia.model.SkaterStatLeader
import com.example.pucktrivia.model.StatLeader
import org.junit.Assert.assertEquals
import org.junit.Test

class SkaterStatLeaderTest {

    @Test
    fun `displayValue returns integer string of value`() {
        val skater = SkaterStatLeader(1, "Alice", "Player", 10, "TST", "C", 80.0)
        assertEquals("80", skater.displayValue)
    }

    @Test
    fun `displayValue truncates decimal`() {
        val skater = SkaterStatLeader(2, "Bob", "Player", 11, "TST", "C", 42.9)
        assertEquals("42", skater.displayValue)
    }

    @Test
    fun `implements StatLeader interface`() {
        val skater: StatLeader = SkaterStatLeader(5, "Carol", "Player", null, "EDM", "L", 100.0)
        assertEquals(5, skater.id)
        assertEquals("100", skater.displayValue)
    }
}
