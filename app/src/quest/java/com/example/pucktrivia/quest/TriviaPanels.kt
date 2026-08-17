package com.example.pucktrivia.quest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pucktrivia.AnswerPanelContent
import com.example.pucktrivia.PuckTriviaPanel
import com.example.pucktrivia.QuestionPanelContent
import com.example.pucktrivia.TriviaRoute
import com.example.pucktrivia.TriviaScreenContent
import com.example.pucktrivia.TriviaViewModel
import com.example.pucktrivia.triviaRouteFor
import com.example.pucktrivia.ui.theme.PuckTriviaTheme
import com.meta.spatial.toolkit.SpatialActivityManager

/**
 * Compose content for the three Horizon OS panels.
 *
 * Each is an independent composition rooted in its own `ComposeView` on its own Android surface —
 * they are not siblings in one tree the way `SpatialRow`'s children are on Android XR. The only
 * thing tying them together is the shared [TriviaViewModel], whose Compose snapshot state each
 * panel reads directly, so answering on the answer panel updates the score on the question panel
 * without any explicit message passing.
 *
 * Every panel wraps itself in [PuckTriviaTheme] for the same reason: with three roots there is no
 * shared ancestor to hang the theme on.
 */

/** Title bar and app-level actions — the Horizon OS stand-in for the mobile flavor's `Orbiter`. */
@Composable
internal fun ChromePanel() {
    PuckTriviaTheme {
        PuckTriviaPanel {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Puck Trivia",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )

                // No Home Space / Full Space toggle exists on Horizon OS — a VR-category activity
                // is immersive or it is not running — so this slot carries a reset action instead.
                //
                // With the panels grabbable this is no longer a nicety. Android XR's `.movable()`
                // panels live inside system chrome that can always retrieve them; a Spatial SDK
                // entity dragged behind the player or through a wall has no such backstop, so the
                // app has to provide the way home itself.
                TextButton(
                    onClick = {
                        // Panel content runs in the panel's own composition, not the VR activity's,
                        // so scene mutations go back through SpatialActivityManager rather than a
                        // direct call. This is the documented panel → scene direction; the reverse
                        // direction here is plain shared ViewModel state.
                        SpatialActivityManager.executeOnVrActivity<PuckTriviaImmersiveActivity> {
                            activity ->
                            activity.resetPanelPoses()
                        }
                    }
                ) {
                    Text(text = "Bring panels to me", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * The persistent panel: the question while a game is in progress, and every other screen (Start,
 * Game Over, loading, errors) the rest of the time.
 *
 * Reusing one panel across routes rather than spawning a panel per screen keeps the player's eyes
 * in one place — the Start screen appears exactly where the question will.
 */
@Composable
internal fun QuestionPanel(viewModel: TriviaViewModel) {
    PuckTriviaTheme {
        when (val route = triviaRouteFor(viewModel)) {
            TriviaRoute.Question ->
                QuestionPanelContent(
                    score = viewModel.score,
                    lives = viewModel.lives,
                    livesColor =
                        if (viewModel.answered && !viewModel.isCorrect) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                    seasonMode = viewModel.selectedMode!!,
                    questionText = viewModel.questionText,
                    answered = viewModel.answered,
                    isCorrect = viewModel.isCorrect,
                    onNextRound = viewModel::nextRound,
                )

            // The flat screens are reused verbatim. They were written for a phone viewport, and a
            // 700x525dp panel is close enough to a small tablet that they lay out sensibly — this
            // is the part of a 2D app that ports to a headset for free.
            else ->
                PuckTriviaPanel {
                    TriviaScreenContent(
                        route = route,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
        }
    }
}

/**
 * The answer choices.
 *
 * Spawned and destroyed by [PuckTriviaImmersiveActivity] with the Question route, so it is only
 * ever composed when a question exists. The empty branch is a guard against the one-frame window
 * where the route has changed but the entity has not been torn down yet, not a real state.
 */
@Composable
internal fun AnswerPanel(viewModel: TriviaViewModel) {
    PuckTriviaTheme {
        val correctPlayer = viewModel.correctPlayer
        if (correctPlayer == null || viewModel.choices.isEmpty()) {
            PuckTriviaPanel { Box(modifier = Modifier.fillMaxWidth()) {} }
            return@PuckTriviaTheme
        }

        AnswerPanelContent(
            statUnitLabel = viewModel.statUnitLabel,
            choices = viewModel.choices,
            selectedPlayerId = viewModel.selectedPlayerId,
            correctPlayerId = correctPlayer.id,
            answered = viewModel.answered,
            onAnswerSelected = viewModel::selectAnswer,
        )
    }
}
