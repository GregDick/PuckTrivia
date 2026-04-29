package com.example.pucktrivia.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val NHL_SEASON = "20252026"
    private const val GAME_TYPE_REGULAR = "2"
    private const val BASE = "https://api-web.nhle.com/v1"

    @Provides @Singleton fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    @StatsUrl
    fun provideStatsUrl(): String =
        "$BASE/skater-stats-leaders/$NHL_SEASON/$GAME_TYPE_REGULAR?limit=-1"

    @Provides
    @GoalieStatsUrl
    fun provideGoalieStatsUrl(): String =
        "$BASE/goalie-stats-leaders/$NHL_SEASON/$GAME_TYPE_REGULAR?limit=-1"

    @Provides @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @Singleton fun provideRandom(): Random = Random
}
