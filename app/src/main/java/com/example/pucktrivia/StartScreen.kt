package com.example.pucktrivia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pucktrivia.model.HighScore
import com.example.pucktrivia.model.SeasonMode
import com.example.pucktrivia.ui.theme.PuckTriviaTheme

@Composable
fun StartScreen(
    onModeSelected: (SeasonMode) -> Unit,
    modifier: Modifier = Modifier,
    highScores: List<HighScore> = emptyList(),
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Puck Trivia",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Score points by answering correctly. Game ends after three wrong answers.",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(48.dp))
        Text(text = "Select a time frame for stats:", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onModeSelected(SeasonMode.RegularSeason) },
            modifier =
                Modifier.fillMaxWidth().height(56.dp).semantics {
                    contentDescription = "Regular Season"
                },
        ) {
            Text(text = "Regular Season", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = { onModeSelected(SeasonMode.Playoffs) },
            modifier =
                Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = "Playoffs" },
        ) {
            Text(text = "Playoffs", style = MaterialTheme.typography.bodyLarge)
        }
        if (highScores.isNotEmpty()) {
            Spacer(modifier = Modifier.height(40.dp))
            HighScoreList(highScores = highScores, currentGameEntry = null)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StartScreenPreview() {
    PuckTriviaTheme { StartScreen(onModeSelected = {}) }
}

@Preview(showBackground = true)
@Composable
private fun StartScreenWithLeaderboardPreview() {
    PuckTriviaTheme {
        StartScreen(
            onModeSelected = {},
            highScores =
                listOf(
                    HighScore(score = 1200, endedAt = 1_715_000_000_000L),
                    HighScore(score = 800, endedAt = 1_714_000_000_000L),
                    HighScore(score = 500, endedAt = 1_713_000_000_000L),
                ),
        )
    }
}
