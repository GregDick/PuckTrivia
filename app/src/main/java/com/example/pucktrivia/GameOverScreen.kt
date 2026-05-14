package com.example.pucktrivia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pucktrivia.model.HighScore
import com.example.pucktrivia.ui.theme.PuckTriviaTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun GameOverScreen(
    score: Int,
    correctAnswered: Int,
    totalAnswered: Int,
    highScores: List<HighScore>,
    placedInTopThree: Boolean,
    currentGameEntry: HighScore?,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(top = 64.dp).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Game Over", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))
        Text(text = "Score: $score", style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "$correctAnswered / $totalAnswered correct",
            style = MaterialTheme.typography.displayMedium,
        )

        if (placedInTopThree) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "New top-3 score!",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
            )
        }

        if (highScores.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            HighScoreList(highScores = highScores, currentGameEntry = currentGameEntry)
        }

        Spacer(modifier = Modifier.height(40.dp))
        Button(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Play Again", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun HighScoreList(
    highScores: List<HighScore>,
    currentGameEntry: HighScore?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "High Scores", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        // At most three rows, so a plain Column is sufficient — no LazyColumn needed.
        highScores.forEachIndexed { index, highScore ->
            HighScoreRow(
                rank = index + 1,
                highScore = highScore,
                isCurrentGame = highScore == currentGameEntry,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HighScoreRow(
    rank: Int,
    highScore: HighScore,
    isCurrentGame: Boolean,
    modifier: Modifier = Modifier,
) {
    val formattedDate = formatTimestamp(highScore.endedAt)
    // Subtle tonal tint marks the just-completed game's row without a jarring color shift.
    val background =
        if (isCurrentGame) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val description = buildString {
        append("Rank $rank, ${highScore.score} points, $formattedDate")
        if (isCurrentGame) append(", this game")
    }
    Surface(
        modifier =
            modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        color = background,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "$rank.  ${highScore.score}", style = MaterialTheme.typography.titleMedium)
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val timestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

/** Formats an epoch-millis timestamp using the device's locale and time zone. */
private fun formatTimestamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(timestampFormatter)

@Preview(showBackground = true)
@Composable
private fun GameOverScreenWithCelebrationPreview() {
    val entries =
        listOf(
            HighScore(score = 1200, endedAt = 1_715_000_000_000L),
            HighScore(score = 800, endedAt = 1_714_000_000_000L),
            HighScore(score = 500, endedAt = 1_713_000_000_000L),
        )
    PuckTriviaTheme {
        GameOverScreen(
            score = 1200,
            correctAnswered = 12,
            totalAnswered = 15,
            highScores = entries,
            placedInTopThree = true,
            currentGameEntry = entries.first(),
            onPlayAgain = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GameOverScreenNoCelebrationPreview() {
    val entries =
        listOf(
            HighScore(score = 1200, endedAt = 1_715_000_000_000L),
            HighScore(score = 800, endedAt = 1_714_000_000_000L),
            HighScore(score = 500, endedAt = 1_713_000_000_000L),
        )
    PuckTriviaTheme {
        GameOverScreen(
            score = 300,
            correctAnswered = 3,
            totalAnswered = 8,
            highScores = entries,
            placedInTopThree = false,
            currentGameEntry = null,
            onPlayAgain = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GameOverScreenOneEntryPreview() {
    val entry = HighScore(score = 400, endedAt = 1_715_000_000_000L)
    PuckTriviaTheme {
        GameOverScreen(
            score = 400,
            correctAnswered = 4,
            totalAnswered = 9,
            highScores = listOf(entry),
            placedInTopThree = true,
            currentGameEntry = entry,
            onPlayAgain = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GameOverScreenEmptyPreview() {
    PuckTriviaTheme {
        GameOverScreen(
            score = 0,
            correctAnswered = 0,
            totalAnswered = 5,
            highScores = emptyList(),
            placedInTopThree = false,
            currentGameEntry = null,
            onPlayAgain = {},
        )
    }
}
