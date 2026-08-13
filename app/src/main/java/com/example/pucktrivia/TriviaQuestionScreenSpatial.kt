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
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialColumn
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.SpatialRow
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.padding
import androidx.xr.compose.subspace.layout.width
import com.example.pucktrivia.model.SeasonMode
import com.example.pucktrivia.model.StatLeader

// Panel dimensions are Dp on SubspaceModifier, not meters — treat them as tablet-scale pixel
// sizes. Tuned by eye on the XR emulator; expect to revisit on real hardware.
private val STATUS_PANEL_WIDTH = 1024.dp
private val STATUS_PANEL_HEIGHT = 180.dp
private val CONTENT_PANEL_WIDTH = 640.dp
private val CONTENT_PANEL_HEIGHT = 640.dp
private val PANEL_GAP = 32.dp

/**
 * Spatial layout for the Question screen: a wide status panel above a question panel and an
 * answer-choices panel side by side, each its own [SpatialPanel].
 *
 * Takes the same parameters as [TriviaQuestionScreen] so the call site is a drop-in. Panels are
 * built by hand rather than via `EnableXrComponentOverrides` on a Material3 pane scaffold — see the
 * feature plan's Approach section for why.
 *
 * Only rendered when [isSpatialUiEnabled] is true, which means the app is in Full Space.
 */
@Composable
fun TriviaQuestionScreenSpatial(
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
) {
    Subspace {
        SpatialColumn {
            SpatialPanel(
                modifier =
                    SubspaceModifier.width(STATUS_PANEL_WIDTH)
                        .height(STATUS_PANEL_HEIGHT)
                        .padding(PANEL_GAP)
            ) {
                StatusPanelContent(
                    score = score,
                    lives = lives,
                    livesColor = livesColor,
                    seasonMode = seasonMode,
                    answered = answered,
                    isCorrect = isCorrect,
                )
            }

            SpatialRow {
                SpatialPanel(
                    modifier =
                        SubspaceModifier.width(CONTENT_PANEL_WIDTH)
                            .height(CONTENT_PANEL_HEIGHT)
                            .padding(PANEL_GAP)
                ) {
                    QuestionPanelContent(
                        questionText = questionText,
                        answered = answered,
                        onNextRound = onNextRound,
                    )
                }

                SpatialPanel(
                    modifier =
                        SubspaceModifier.width(CONTENT_PANEL_WIDTH)
                            .height(CONTENT_PANEL_HEIGHT)
                            .padding(PANEL_GAP)
                ) {
                    AnswerPanelContent(
                        statUnitLabel = statUnitLabel,
                        choices = choices,
                        selectedPlayerId = selectedPlayerId,
                        correctPlayerId = correctPlayerId,
                        answered = answered,
                        onAnswerSelected = onAnswerSelected,
                    )
                }
            }
        }
    }
}

/**
 * Score, lives, season, and answer feedback.
 *
 * Unlike the 2D layouts this needs no fixed-height spacer boxes around the feedback text — separate
 * panels cannot shift each other, so the text can simply appear and disappear.
 */
@Composable
private fun StatusPanelContent(
    score: Int,
    lives: Int,
    livesColor: Color,
    seasonMode: SeasonMode,
    answered: Boolean,
    isCorrect: Boolean,
) {
    PuckTriviaPanel {
        // The 2D overlay in MainActivity is not placed while a subspace is active, so the status
        // panel carries the space-mode toggle. Same control, same top-right position as in 2D.
        Box(modifier = Modifier.fillMaxSize()) {
            SpaceModeToggle(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
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
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            if (answered) {
                Text(
                    text = if (isCorrect) "Correct!" else "Incorrect!",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isCorrect) CorrectGreen else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

/** The question text, with the Next button appearing beneath it once answered. */
@Composable
private fun QuestionPanelContent(
    questionText: String,
    answered: Boolean,
    onNextRound: () -> Unit,
) {
    PuckTriviaPanel {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = questionText,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (answered) {
                OutlinedButton(
                    onClick = onNextRound,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                ) {
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
private fun AnswerPanelContent(
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
 */
@Composable
private fun PuckTriviaPanel(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        content()
    }
}
