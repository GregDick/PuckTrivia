package com.example.pucktrivia

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
// The Horizon OS flavor sizes the same two panels in *meters* instead
// (PuckTriviaImmersiveActivity's QuadShapeOptions), which is the sharpest single difference
// between the two stacks.
//
// The question panel is wider than the answer panel because it now carries the status row
// (score / lives / season) that used to have its own panel above the pair.
private val QUESTION_PANEL_WIDTH = 700.dp
private val ANSWER_PANEL_WIDTH = 560.dp
private val CONTENT_PANEL_HEIGHT = 640.dp
private val PANEL_GAP = 32.dp

/**
 * Android XR spatial layout for the Question screen: two [SpatialPanel]s side by side — a question
 * panel carrying the status row (score / lives / season / feedback) above the question text and
 * Next button, and an answer-choices panel beside it.
 *
 * Takes the same parameters as [TriviaQuestionScreen] so the call site is a drop-in. Panels are
 * built by hand rather than via `EnableXrComponentOverrides` on a Material3 pane scaffold — see the
 * feature plan's Approach section for why.
 *
 * Only rendered when `LocalSpatialCapabilities.current.isSpatialUiEnabled` is true, which means the
 * app is in Full Space.
 *
 * Panel *contents* live in `TriviaPanelContent.kt` in the shared source set, because the Horizon OS
 * flavor renders exactly the same two panes inside Meta Spatial SDK panel entities.
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
            // App chrome, floated above the panels rather than taking layout space inside one.
            //
            // Declared on the SpatialRow rather than inside a panel, so it anchors to the panel
            // *group*: it spans the full group width and stays put when the player drags or
            // resizes either panel. An Orbiter's spatial parent is the nearest enclosing spatial
            // component, and an Orbiter cannot exceed that parent's dimensions — so anchoring to
            // the row is also what makes a full-width bar possible at all.
            Orbiter(alignment = OrbiterAlignment.TopCenter()) { SpatialTopAppBar() }

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
 * Full-width top app bar floated above the panel group, carrying the space-mode toggle.
 *
 * Sized to the [SpatialRow] it orbits, so it reads as app chrome spanning the whole layout rather
 * than a control belonging to either panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpatialTopAppBar() {
    TopAppBar(
        title = { Text(text = "Puck Trivia", style = MaterialTheme.typography.titleLarge) },
        actions = { SpaceModeToggle(modifier = Modifier.padding(end = 12.dp)) },
        // The orbiter floats free of the activity window, so system bar insets do not apply and
        // would only add dead space above the title.
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        modifier = Modifier.fillMaxWidth(),
    )
}
