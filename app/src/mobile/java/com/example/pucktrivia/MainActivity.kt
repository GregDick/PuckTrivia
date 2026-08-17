package com.example.pucktrivia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.platform.LocalSpatialConfiguration
import com.example.pucktrivia.ui.theme.PuckTriviaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Entry point for phones, tablets, and Android XR headsets.
 *
 * Horizon OS has its own entry point — `PuckTriviaImmersiveActivity` in the quest flavor — because
 * the two platforms disagree at the activity level, not just at the layout level. Everything
 * routing and content related is shared; only this shell and the spatial container differ.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: TriviaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PuckTriviaTheme {
                val route = triviaRouteFor(viewModel)

                // The spatial branch has to sit above the Scaffold, not inside it: a room-scale
                // panel layout wants a top-level subspace, and nesting one in a 2D Scaffold is the
                // wrong shape for it. Routing still lives in exactly one place (triviaRouteFor), so
                // the 2D path below is unchanged for phones, tablets, and Home Space on a headset.
                //
                // Only the Question screen opens a subspace *for now* — the intent is for every
                // route to get a spatial layout on XR devices, at which point the route check
                // drops out and this becomes a plain capability branch. Deliberately left inline
                // rather than extracted behind a named helper, since the conjunction is a
                // waypoint rather than a rule worth naming.
                //
                // Meanwhile every other screen falls through to the 2D branch and renders in the
                // activity's main panel, which the system sizes — including while in Full Space.
                // Subspace disables the main panel entity on first use and re-enables it when the
                // last subspace disposes (androidx.xr.compose Subspace.kt:181-197, refcounted via
                // SceneManager.getSceneCount), so leaving the Question screen restores the flat
                // panel on its own.
                if (
                    LocalSpatialCapabilities.current.isSpatialUiEnabled &&
                        route == TriviaRoute.Question
                ) {
                    SpatialQuestionRoute(viewModel)
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            TriviaScreenContent(
                                route = route,
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding),
                            )
                        }

                        // Persistent on every 2D screen, because it is the only path into Full
                        // Space — the XR system chrome offers minimize and close but no expand.
                        //
                        // Gated on hasXrSpatialFeature, NOT isSpatialUiEnabled: the latter is
                        // false in Home Space even on a headset, so it would hide the control
                        // in exactly the state it exists to escape.
                        if (LocalSpatialConfiguration.current.hasXrSpatialFeature) {
                            SpaceModeToggle(
                                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The Question screen as spatial panels, plus the toggle back out to Home Space. */
@Composable
private fun SpatialQuestionRoute(viewModel: TriviaViewModel) {
    val livesColor =
        if (viewModel.answered && !viewModel.isCorrect) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onBackground
        }

    TriviaQuestionScreenSpatial(
        score = viewModel.score,
        lives = viewModel.lives,
        livesColor = livesColor,
        seasonMode = viewModel.selectedMode!!,
        questionText = viewModel.questionText,
        statUnitLabel = viewModel.statUnitLabel,
        choices = viewModel.choices,
        selectedPlayerId = viewModel.selectedPlayerId,
        correctPlayerId = viewModel.correctPlayer!!.id,
        answered = viewModel.answered,
        isCorrect = viewModel.isCorrect,
        onAnswerSelected = viewModel::selectAnswer,
        onNextRound = viewModel::nextRound,
    )
}
