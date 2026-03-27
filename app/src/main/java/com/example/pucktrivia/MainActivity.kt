package com.example.pucktrivia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                        viewModel.choices.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Unable to load question")
                            }
                        }
                        else -> {
                            val scoreColor =
                                when {
                                    !viewModel.answered -> MaterialTheme.colorScheme.onBackground
                                    viewModel.isCorrect -> CorrectGreen
                                    else -> MaterialTheme.colorScheme.error
                                }

                            TriviaQuestionScreen(
                                score = viewModel.score,
                                scoreColor = scoreColor,
                                questionText =
                                    "Which of these players currently has the most points?",
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
