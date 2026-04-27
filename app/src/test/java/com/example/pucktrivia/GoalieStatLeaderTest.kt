package com.example.pucktrivia

import com.example.pucktrivia.model.GoalieStatLeader
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalieStatLeaderTest {

    @Test
    fun `displayValue formats save percentage as 3-decimal string`() {
        val goalie =
            GoalieStatLeader(
                id = 1,
                firstName = "Marc",
                lastName = "Fleury",
                sweaterNumber = 29,
                teamAbbrev = "MIN",
                value = 0.9254,
            )
        assertEquals("0.925", goalie.displayValue)
    }

    @Test
    fun `displayValue rounds up at 5`() {
        val goalie =
            GoalieStatLeader(
                id = 2,
                firstName = "Carey",
                lastName = "Price",
                sweaterNumber = 31,
                teamAbbrev = "MTL",
                value = 0.9256,
            )
        assertEquals("0.926", goalie.displayValue)
    }

    @Test
    fun `implements StatLeader interface`() {
        val goalie =
            GoalieStatLeader(
                id = 42,
                firstName = "Tuukka",
                lastName = "Rask",
                sweaterNumber = null,
                teamAbbrev = "BOS",
                value = 0.910,
            )
        assertEquals(42, goalie.id)
        assertEquals("Tuukka", goalie.firstName)
        assertEquals("Rask", goalie.lastName)
        assertEquals(null, goalie.sweaterNumber)
        assertEquals("BOS", goalie.teamAbbrev)
        assertEquals(0.910, goalie.value, 0.001)
    }
}
