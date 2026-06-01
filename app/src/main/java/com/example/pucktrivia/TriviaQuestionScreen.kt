package com.example.pucktrivia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.pucktrivia.model.SeasonMode
import com.example.pucktrivia.model.StatLeader

internal val CorrectGreen = Color(0xFF4CAF50)

@Composable
fun TriviaQuestionScreen(
    score: Int,
    lives: Int,
    livesColor: Color,
    seasonMode: SeasonMode,
    questionText: String,
    statUnitLabel: String,
    choices: List<StatLeader>,
    selectedPlayerId: Int?,
    correctPlayerId: Int,
    answered: Boolean,
    isCorrect: Boolean,
    onAnswerSelected: (Int) -> Unit,
    onNextRound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isLandscape()) {
        TriviaQuestionScreenLandscape(
            score = score,
            lives = lives,
            livesColor = livesColor,
            seasonMode = seasonMode,
            questionText = questionText,
            statUnitLabel = statUnitLabel,
            choices = choices,
            selectedPlayerId = selectedPlayerId,
            correctPlayerId = correctPlayerId,
            answered = answered,
            isCorrect = isCorrect,
            onAnswerSelected = onAnswerSelected,
            onNextRound = onNextRound,
            modifier = modifier,
        )
    } else {
        TriviaQuestionScreenPortrait(
            score = score,
            lives = lives,
            livesColor = livesColor,
            seasonMode = seasonMode,
            questionText = questionText,
            statUnitLabel = statUnitLabel,
            choices = choices,
            selectedPlayerId = selectedPlayerId,
            correctPlayerId = correctPlayerId,
            answered = answered,
            isCorrect = isCorrect,
            onAnswerSelected = onAnswerSelected,
            onNextRound = onNextRound,
            modifier = modifier,
        )
    }
}

/** Portrait layout — unchanged from the original implementation. */
@Composable
private fun TriviaQuestionScreenPortrait(
    score: Int,
    lives: Int,
    livesColor: Color,
    seasonMode: SeasonMode,
    questionText: String,
    statUnitLabel: String,
    choices: List<StatLeader>,
    selectedPlayerId: Int?,
    correctPlayerId: Int,
    answered: Boolean,
    isCorrect: Boolean,
    onAnswerSelected: (Int) -> Unit,
    onNextRound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        // Score, lives, and feedback pinned to top
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Score: $score",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Lives: $lives",
                style = MaterialTheme.typography.headlineLarge,
                color = livesColor,
            )
            Text(
                text =
                    when (seasonMode) {
                        SeasonMode.RegularSeason -> "Regular Season"
                        SeasonMode.Playoffs -> "Playoffs"
                    },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            // Fixed-height container so feedback doesn't shift other elements
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (answered) {
                    Text(
                        text = if (isCorrect) "Correct!" else "Incorrect!",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isCorrect) CorrectGreen else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // Question, answers, next — centered on the full screen
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = questionText,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            choices.forEach { player ->
                AnswerButton(
                    player = player,
                    statUnitLabel = statUnitLabel,
                    answered = answered,
                    correctPlayerId = correctPlayerId,
                    selectedPlayerId = selectedPlayerId,
                    onAnswerSelected = onAnswerSelected,
                )
            }

            // Fixed-height container so the Next button doesn't shift other elements
            Box(
                modifier = Modifier.fillMaxWidth().height(72.dp).padding(top = 24.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (answered) {
                    OutlinedButton(onClick = onNextRound, modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Next", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/**
 * Landscape layout — status header spans full width above a two-column row. Left column: question
 * text (vertically centered when unanswered; shifted to the top with the Next button pinned beneath
 * it once answered). Right column: scrollable answer buttons.
 */
@Composable
private fun TriviaQuestionScreenLandscape(
    score: Int,
    lives: Int,
    livesColor: Color,
    seasonMode: SeasonMode,
    questionText: String,
    statUnitLabel: String,
    choices: List<StatLeader>,
    selectedPlayerId: Int?,
    correctPlayerId: Int,
    answered: Boolean,
    isCorrect: Boolean,
    onAnswerSelected: (Int) -> Unit,
    onNextRound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        // Full-width status header (Score / Lives / Season / feedback)
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text(
                    text = "Score: $score",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Lives: $lives",
                    style = MaterialTheme.typography.headlineMedium,
                    color = livesColor,
                )
                Text(
                    text =
                        when (seasonMode) {
                            SeasonMode.RegularSeason -> "Regular Season"
                            SeasonMode.Playoffs -> "Playoffs"
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            // Fixed-height container so feedback doesn't shift layout
            Box(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (answered) {
                    Text(
                        text = if (isCorrect) "Correct!" else "Incorrect!",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCorrect) CorrectGreen else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Two-column content row
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Left column: question text + Next button. The question is always top-aligned so it
            // doesn't shift when the Next button appears, and the Next button is pinned near the
            // bottom of the column (with bottom margin) so it never clips off the short landscape
            // viewport.
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(text = questionText, style = MaterialTheme.typography.headlineSmall)
                }
                if (answered) {
                    OutlinedButton(
                        onClick = onNextRound,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
                    ) {
                        Text(text = "Next", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            // Right column: scrollable answer buttons only.
            // Do not apply fillMaxHeight here — verticalScroll needs the column to be
            // able to grow beyond the constrained height of the parent Row.
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
            ) {
                choices.forEach { player ->
                    AnswerButton(
                        player = player,
                        statUnitLabel = statUnitLabel,
                        answered = answered,
                        correctPlayerId = correctPlayerId,
                        selectedPlayerId = selectedPlayerId,
                        onAnswerSelected = onAnswerSelected,
                    )
                }
            }
        }
    }
}

/** Shared answer button used by both portrait and landscape layouts. */
@Composable
private fun AnswerButton(
    player: StatLeader,
    statUnitLabel: String,
    answered: Boolean,
    correctPlayerId: Int,
    selectedPlayerId: Int?,
    onAnswerSelected: (Int) -> Unit,
) {
    val containerColor =
        when {
            !answered -> MaterialTheme.colorScheme.primary
            player.id == correctPlayerId -> CorrectGreen
            player.id == selectedPlayerId -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }

    Button(
        onClick = { onAnswerSelected(player.id) },
        enabled = !answered,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = containerColor,
                disabledContainerColor = containerColor,
                disabledContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${player.firstName} ${player.lastName}  ${player.teamAbbrev}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (answered) {
                Text(
                    text =
                        "${player.displayValue}${if (statUnitLabel.isNotEmpty()) " $statUnitLabel" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
