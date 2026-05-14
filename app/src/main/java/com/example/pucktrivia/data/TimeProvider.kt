package com.example.pucktrivia.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Supplies the current wall-clock time. Injected rather than calling [System.currentTimeMillis]
 * inline so that timestamp-dependent logic stays testable.
 */
interface TimeProvider {
    fun nowMillis(): Long
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeProviderModule {
    @Binds abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider
}
