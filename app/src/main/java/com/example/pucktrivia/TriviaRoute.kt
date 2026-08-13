package com.example.pucktrivia

/**
 * Which screen the app should be showing, derived from [TriviaViewModel] state.
 *
 * Extracted from `MainActivity`'s routing `when` so the decision exists as a value rather than only
 * as control flow. Two things need that: the spatial branch has to know whether the current screen
 * is the Question screen *before* deciding whether to open a subspace, and the routing logic
 * becomes unit-testable without a device.
 */
internal enum class TriviaRoute {
    Start,
    Loading,
    LoadError,
    PlayoffsUnavailable,
    FatalError,
    NoQuestion,
    GameOver,
    Question,
}

/**
 * Resolves the current [TriviaRoute].
 *
 * Branch order is significant and matches the original `when` exactly. In particular [NoQuestion]
 * must be evaluated before [Question], because the Question screen dereferences
 * `viewModel.correctPlayer!!` and only the empty-choices check guards it.
 *
 * Call this from a composable: the `viewModel` property reads are Compose snapshot reads, so they
 * subscribe the calling composition to changes.
 */
internal fun triviaRouteFor(viewModel: TriviaViewModel): TriviaRoute =
    when {
        viewModel.selectedMode == null -> TriviaRoute.Start
        viewModel.isLoading -> TriviaRoute.Loading
        viewModel.loadError -> TriviaRoute.LoadError
        viewModel.playoffsUnavailable -> TriviaRoute.PlayoffsUnavailable
        viewModel.fatalError -> TriviaRoute.FatalError
        viewModel.choices.isEmpty() -> TriviaRoute.NoQuestion
        viewModel.gameOver -> TriviaRoute.GameOver
        else -> TriviaRoute.Question
    }
