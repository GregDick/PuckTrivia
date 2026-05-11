package com.example.pucktrivia

import com.example.pucktrivia.di.DefaultStatsUrlProvider
import com.example.pucktrivia.model.SeasonMode
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsUrlProviderTest {

    private val provider = DefaultStatsUrlProvider()

    @Test
    fun `skater URL for RegularSeason uses gameType 2`() {
        val url = provider.skaterUrl(SeasonMode.RegularSeason)
        assertTrue("Expected skater URL to contain '/2?' but was: $url", url.contains("/2?"))
        assertTrue(url.contains("skater-stats-leaders"))
    }

    @Test
    fun `skater URL for Playoffs uses gameType 3`() {
        val url = provider.skaterUrl(SeasonMode.Playoffs)
        assertTrue("Expected skater URL to contain '/3?' but was: $url", url.contains("/3?"))
        assertTrue(url.contains("skater-stats-leaders"))
    }

    @Test
    fun `goalie URL for RegularSeason uses gameType 2`() {
        val url = provider.goalieUrl(SeasonMode.RegularSeason)
        assertTrue("Expected goalie URL to contain '/2?' but was: $url", url.contains("/2?"))
        assertTrue(url.contains("goalie-stats-leaders"))
    }

    @Test
    fun `goalie URL for Playoffs uses gameType 3`() {
        val url = provider.goalieUrl(SeasonMode.Playoffs)
        assertTrue("Expected goalie URL to contain '/3?' but was: $url", url.contains("/3?"))
        assertTrue(url.contains("goalie-stats-leaders"))
    }
}
