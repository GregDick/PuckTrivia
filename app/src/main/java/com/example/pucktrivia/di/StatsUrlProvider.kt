package com.example.pucktrivia.di

import com.example.pucktrivia.model.SeasonMode
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

interface StatsUrlProvider {
    fun skaterUrl(mode: SeasonMode): String

    fun goalieUrl(mode: SeasonMode): String
}

class DefaultStatsUrlProvider @Inject constructor() : StatsUrlProvider {
    override fun skaterUrl(mode: SeasonMode): String =
        "$BASE/skater-stats-leaders/$NHL_SEASON/${mode.gameType}?limit=-1"

    override fun goalieUrl(mode: SeasonMode): String =
        "$BASE/goalie-stats-leaders/$NHL_SEASON/${mode.gameType}?limit=-1"

    companion object {
        private const val BASE = "https://api-web.nhle.com/v1"
        private const val NHL_SEASON = "20252026"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StatsUrlProviderModule {
    @Binds abstract fun bindStatsUrlProvider(impl: DefaultStatsUrlProvider): StatsUrlProvider
}
