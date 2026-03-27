package com.example.pucktrivia.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    @StatsUrl
    fun provideStatsUrl(): String =
        "https://api-web.nhle.com/v1/skater-stats-leaders/current?limit=-1"

    @Provides @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
