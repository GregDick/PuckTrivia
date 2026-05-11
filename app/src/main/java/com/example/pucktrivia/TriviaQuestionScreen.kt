package com.example.pucktrivia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
