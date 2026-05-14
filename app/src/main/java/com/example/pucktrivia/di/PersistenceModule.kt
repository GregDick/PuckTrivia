package com.example.pucktrivia.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Process-wide singleton DataStore for high scores. The [preferencesDataStore] delegate guarantees
 * only one instance is ever created for this file, which DataStore requires.
 */
private val Context.highScoreDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "high_scores")

@Module
@InstallIn(SingletonComponent::class)
object PersistenceModule {

    @Provides
    @Singleton
    fun provideHighScoreDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.highScoreDataStore
}
