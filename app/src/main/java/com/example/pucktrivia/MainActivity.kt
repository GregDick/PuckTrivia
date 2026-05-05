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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when {
                        viewModel.selectedMode == null -> {
                            StartScreen(
                                onModeSelected = viewModel::startGame,
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
                        viewModel.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        viewModel.loadError -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Failed to load data. Please try again.",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        viewModel.fatalError -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
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
                        }
                        viewModel.choices.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Unable to load question")
                            }
                        }
                        viewModel.gameOver -> {
                            GameOverScreen(
                                score = viewModel.score,
                                correctAnswered = viewModel.correctAnswered,
                                totalAnswered = viewModel.totalAnswered,
                                onPlayAgain = viewModel::resetGame,
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
                        else -> {
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
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
                    }
                }
            }
        }
    }
}
