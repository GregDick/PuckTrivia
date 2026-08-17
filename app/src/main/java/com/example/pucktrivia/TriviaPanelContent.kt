package com.example.pucktrivia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/**
 * The Compose content that fills a spatial panel, independent of which XR stack is hosting it.
 *
 * This file is the interesting half of supporting two headset platforms. Android XR
 * (`androidx.xr.compose.subspace.SpatialPanel`) and Meta Horizon OS
 * (`ComposeViewPanelRegistration` + `Entity.createPanelEntity`) disagree about how a panel is
 * declared, positioned, and sized — but neither has an opinion about what goes *inside* one. That
 * is ordinary Compose, so it lives in `main` and both flavors call it:
 *
 * - mobile → `TriviaQuestionScreenSpatial`, inside a `Subspace { SpatialRow { SpatialPanel { … } }
 *   }`
 * - quest → `PuckTriviaImmersiveActivity.registerPanels`, inside a `ComposeView` on a panel entity
 *
 * Keeping it here is what stops the two platforms' trivia UI from drifting apart.
 */

/**
 * Status, question, and the Next button — everything except the answer choices.
 *
 * Score / lives / season sit at the top of this panel rather than in a panel of their own. A third
 * panel made the trio independently draggable once move policies were enabled, which let a player
 * pull the score away from the question it belongs to.
 *
 * Unlike the 2D layouts this needs no fixed-height spacer boxes around the feedback text or the
 * Next button: the answer choices live on a separate panel, so nothing here can shift them.
 */
@Composable
internal fun QuestionPanelContent(
    score: Int,
    lives: Int,
    livesColor: Color,
    seasonMode: SeasonMode,
    questionText: String,
    answered: Boolean,
    isCorrect: Boolean,
    onNextRound: () -> Unit,
) {
    PuckTriviaPanel {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Score: $score",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Lives: $lives",
                    style = MaterialTheme.typography.titleLarge,
                    color = livesColor,
                )
                Text(
                    text =
                        when (seasonMode) {
                            SeasonMode.RegularSeason -> "Regular Season"
                            SeasonMode.Playoffs -> "Playoffs"
                        },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            if (answered) {
                Text(
                    text = if (isCorrect) "Correct!" else "Incorrect!",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isCorrect) CorrectGreen else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = questionText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (answered) {
                OutlinedButton(onClick = onNextRound, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Next", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/**
 * The answer choices, reusing [AnswerButton] verbatim so correct/incorrect coloring and the
 * disabled-after-answer behavior are identical to the 2D layouts.
 *
 * No `verticalScroll` here: the landscape layout needs it because the landscape viewport is short,
 * but a panel sized for three choices has no such constraint.
 */
@Composable
internal fun AnswerPanelContent(
    statUnitLabel: String,
    choices: List<StatLeader>,
    selectedPlayerId: Int?,
    correctPlayerId: Int,
    answered: Boolean,
    onAnswerSelected: (Int) -> Unit,
) {
    PuckTriviaPanel {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
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

/**
 * Shared chrome for panel content: paints the app background so a panel reads as part of Puck
 * Trivia rather than as a transparent cut-out over the passthrough environment.
 *
 * This matters more on Horizon OS than on Android XR. The quest flavor runs the panels over
 * passthrough with a transparent panel theme, so without an explicit background the player would be
 * reading white text against their own living room.
 */
@Composable
internal fun PuckTriviaPanel(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        content()
    }
}
