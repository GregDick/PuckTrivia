package com.example.pucktrivia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.pucktrivia.model.SkaterStatLeader

internal val CorrectGreen = Color(0xFF4CAF50)

@Composable
fun TriviaQuestionScreen(
    score: Int,
    scoreColor: Color,
    questionText: String,
    choices: List<SkaterStatLeader>,
    selectedPlayerId: Int?,
    correctPlayerId: Int,
    answered: Boolean,
    isCorrect: Boolean,
    onAnswerSelected: (Int) -> Unit,
    onNextRound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Score: $score",
            style = MaterialTheme.typography.titleMedium,
            color = scoreColor,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )

        Text(
            text = questionText,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        if (answered) {
            Text(
                text = if (isCorrect) "Correct!" else "Incorrect!",
                style = MaterialTheme.typography.titleLarge,
                color = if (isCorrect) CorrectGreen else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

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
                    )
                    if (answered) {
                        Text(
                            text = "${player.value.toInt()} pts",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        if (answered) {
            OutlinedButton(
                onClick = onNextRound,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                Text(text = "Next", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
