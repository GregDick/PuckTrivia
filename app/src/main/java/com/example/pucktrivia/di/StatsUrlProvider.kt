package com.example.pucktrivia.di

import com.example.pucktrivia.model.SeasonMode
import javax.inject.Inject

open class StatsUrlProvider @Inject constructor() {
    open fun skaterUrl(mode: SeasonMode): String =
        "$BASE/skater-stats-leaders/$NHL_SEASON/${mode.gameType}?limit=-1"

    open fun goalieUrl(mode: SeasonMode): String =
        "$BASE/goalie-stats-leaders/$NHL_SEASON/${mode.gameType}?limit=-1"

    companion object {
        private const val BASE = "https://api-web.nhle.com/v1"
        private const val NHL_SEASON = "20252026"
    }
}
