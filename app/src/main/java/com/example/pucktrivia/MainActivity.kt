package com.example.pucktrivia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pucktrivia.model.SeasonMode
import com.example.pucktrivia.ui.theme.PuckTriviaTheme
import dagger.hilt.android.AndroidEntryPoint

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
                // Only the Question screen opens a subspace. Every other screen falls through to
                // the 2D branch and renders in the activity's main panel, which the system sizes —
                // including while in Full Space. Subspace disables the main panel entity on first
                // use and re-enables it when the last subspace disposes (androidx.xr.compose
                // Subspace.kt:181-197, refcounted via SceneManager.getSceneCount), so leaving the
                // Question screen restores the flat panel on its own.
                if (isSpatialUiEnabled() && route == TriviaRoute.Question) {
                    SpatialQuestionRoute(viewModel)
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            TriviaContent(
                                route = route,
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding),
                            )
                        }

                        // Persistent on every 2D screen, because it is the only path into Full
                        // Space — the XR system chrome offers minimize and close but no expand.
                        if (isXrDevice()) {
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

/**
 * Renders whichever screen [route] selects.
 *
 * Extracted from the `setContent` lambda so the spatial branch above can reuse the same routing
 * decision without duplicating these eight cases.
 */
@Composable
private fun TriviaContent(
    route: TriviaRoute,
    viewModel: TriviaViewModel,
    modifier: Modifier = Modifier,
) {
    when (route) {
        TriviaRoute.Start ->
            StartScreen(
                onModeSelected = viewModel::startGame,
                modifier = modifier,
                highScores = viewModel.startScreenHighScores,
            )

        TriviaRoute.Loading ->
            Box(
                modifier = Modifier.fillMaxSize().then(modifier),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

        TriviaRoute.LoadError ->
            Box(
                modifier = Modifier.fillMaxSize().then(modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Failed to load data. Please try again.",
                    color = MaterialTheme.colorScheme.error,
                )
            }

        TriviaRoute.PlayoffsUnavailable ->
            PlayoffsUnavailableScreen(
                onPlayRegularSeason = { viewModel.startGame(SeasonMode.RegularSeason) },
                modifier = modifier,
            )

        TriviaRoute.FatalError ->
            Box(
                modifier = Modifier.fillMaxSize().then(modifier),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Something went wrong preparing the next question.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = viewModel::resetGame) { Text("Reset Game") }
                }
            }

        TriviaRoute.NoQuestion ->
            Box(
                modifier = Modifier.fillMaxSize().then(modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text("Unable to load question")
            }

        TriviaRoute.GameOver ->
            GameOverScreen(
                score = viewModel.score,
                correctAnswered = viewModel.correctAnswered,
                totalAnswered = viewModel.totalAnswered,
                highScores = viewModel.highScores,
                placedInTopThree = viewModel.placedInTopThree,
                currentGameEntry = viewModel.currentGameHighScore,
                onPlayAgain = viewModel::resetGame,
                modifier = modifier,
            )

        TriviaRoute.Question -> {
            val livesColor =
                if (viewModel.answered && !viewModel.isCorrect) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onBackground
                }

            TriviaQuestionScreen(
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
                modifier = modifier,
            )
        }
    }
}
