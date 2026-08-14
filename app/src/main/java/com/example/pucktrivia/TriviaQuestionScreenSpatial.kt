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
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterAlignment
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.SpatialRow
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.movable
import androidx.xr.compose.subspace.layout.padding
import androidx.xr.compose.subspace.layout.resizable
import androidx.xr.compose.subspace.layout.width
import com.example.pucktrivia.model.SeasonMode
import com.example.pucktrivia.model.StatLeader

// Panel dimensions are Dp on SubspaceModifier, not meters — treat them as tablet-scale pixel
// sizes. Tuned by eye on the XR emulator; expect to revisit on real hardware.
//
// The question panel is wider than the answer panel because it now carries the status row
// (score / lives / season) that used to have its own panel above the pair.
private val QUESTION_PANEL_WIDTH = 700.dp
private val ANSWER_PANEL_WIDTH = 560.dp
private val CONTENT_PANEL_HEIGHT = 640.dp
private val PANEL_GAP = 32.dp

/**
 * Spatial layout for the Question screen: two [SpatialPanel]s side by side — a question panel
 * carrying the status row (score / lives / season / feedback) above the question text and Next
 * button, and an answer-choices panel beside it.
 *
 * Takes the same parameters as [TriviaQuestionScreen] so the call site is a drop-in. Panels are
 * built by hand rather than via `EnableXrComponentOverrides` on a Material3 pane scaffold — see the
 * feature plan's Approach section for why.
 *
 * Only rendered when `LocalSpatialCapabilities.current.isSpatialUiEnabled` is true, which means
 * the app is in Full Space.
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
        SpatialRow {
            // The space-mode toggle is chrome, not content: an Orbiter floats it outside the panel
            // bounds, so it costs no layout space and cannot collide with the status row.
            //
            // Declared on the SpatialRow rather than inside a panel, so it anchors to the panel
            // *group* and stays at the group's top-right corner even after the player drags or
            // resizes either panel. An Orbiter's spatial parent is the nearest enclosing spatial
            // component, which is the row here.
            Orbiter(alignment = OrbiterAlignment.TopEnd()) { SpaceModeToggle() }

            SpatialPanel(
                modifier =
                    SubspaceModifier.width(QUESTION_PANEL_WIDTH)
                        .height(CONTENT_PANEL_HEIGHT)
                        .padding(PANEL_GAP)
                        .movable()
                        .resizable()
            ) {
                QuestionPanelContent(
                    score = score,
                    lives = lives,
                    livesColor = livesColor,
                    seasonMode = seasonMode,
                    questionText = questionText,
                    answered = answered,
                    isCorrect = isCorrect,
                    onNextRound = onNextRound,
                )
            }

            SpatialPanel(
                modifier =
                    SubspaceModifier.width(ANSWER_PANEL_WIDTH)
                        .height(CONTENT_PANEL_HEIGHT)
                        .padding(PANEL_GAP)
                        .movable()
                        .resizable()
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
private fun QuestionPanelContent(
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

            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
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
