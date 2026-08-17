package com.example.pucktrivia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pucktrivia.model.SeasonMode

/**
 * Renders whichever screen [route] selects.
 *
 * Lives in the shared source set rather than beside `MainActivity` because both headset flavors
 * need it: Android XR falls through to it for every non-Question route inside the activity's main
 * panel, and Horizon OS renders it into the persistent question panel entity for the same routes.
 * Duplicating these eight cases per flavor is exactly how the two platforms would drift.
 */
@Composable
internal fun TriviaScreenContent(
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
