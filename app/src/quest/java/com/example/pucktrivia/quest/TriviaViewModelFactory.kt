package com.example.pucktrivia.quest

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.pucktrivia.TriviaViewModel
import com.example.pucktrivia.data.HighScoreRepository
import com.example.pucktrivia.data.TimeProvider
import com.example.pucktrivia.di.IoDispatcher
import com.example.pucktrivia.di.StatsUrlProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient

/**
 * Hands [TriviaViewModel] to the immersive activity without `by viewModels()`.
 *
 * This exists because of a real, unavoidable seam between Hilt and Meta Spatial SDK. The immersive
 * activity must extend `AppSystemActivity`, whose hierarchy is:
 * ```
 * android.app.Activity → VrActivity (Spatial SDK) → AppSystemActivity → PuckTriviaImmersiveActivity
 * ```
 *
 * It is not a `ComponentActivity`, so it is not a `ViewModelStoreOwner`, `LifecycleOwner`, or
 * `SavedStateRegistryOwner`. That rules out `by viewModels()`, `hiltViewModel()`, and the
 * `@HiltViewModel` factory path wholesale — none of which is a Spatial SDK bug, just the cost of an
 * activity base class predating those contracts.
 *
 * The way through is to keep the [ViewModel] itself untouched and replace only the plumbing that
 * would normally construct it: pull its collaborators off the `SingletonComponent` through an
 * [EntryPoint], and let a plain [ViewModelProvider.Factory] do the wiring. `TriviaViewModel` is
 * shared verbatim with the mobile flavor, so every rule about scoring, lives, and question
 * selection stays in one place.
 *
 * The activity owns a `ViewModelStore` and clears it in `onSpatialShutdown`, which is what keeps
 * `onCleared()` — and therefore `viewModelScope` cancellation — honest.
 *
 * `savedStateHandle` falls back to `TriviaViewModel`'s own default. Saved-state restoration is a
 * loose end on this flavor: with no `SavedStateRegistryOwner` there is nothing to restore *from*,
 * so an in-progress game does not survive process death on a headset the way it does on a phone.
 * Wiring a `SavedStateRegistryController` by hand onto the immersive activity is the fix, and is
 * out of scope for this spike.
 */
internal class TriviaViewModelFactory(context: Context) : ViewModelProvider.Factory {

    private val graph =
        EntryPointAccessors.fromApplication(context.applicationContext, TriviaGraph::class.java)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == TriviaViewModel::class.java) {
            "TriviaViewModelFactory only builds TriviaViewModel, was asked for $modelClass"
        }
        return TriviaViewModel(
            client = graph.okHttpClient(),
            urlProvider = graph.statsUrlProvider(),
            highScoreRepository = graph.highScoreRepository(),
            timeProvider = graph.timeProvider(),
            ioDispatcher = graph.ioDispatcher(),
        )
            as T
    }

    /**
     * The subset of the Hilt graph [TriviaViewModel] needs.
     *
     * Every binding here is already installed in `SingletonComponent` for the mobile flavor, so
     * this interface adds an access route rather than a second definition of anything.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface TriviaGraph {
        fun okHttpClient(): OkHttpClient

        fun statsUrlProvider(): StatsUrlProvider

        fun highScoreRepository(): HighScoreRepository

        fun timeProvider(): TimeProvider

        @IoDispatcher fun ioDispatcher(): CoroutineDispatcher
    }
}
