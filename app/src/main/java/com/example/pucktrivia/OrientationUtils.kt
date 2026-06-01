package com.example.pucktrivia

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Returns true when the current device configuration is landscape (width > height).
 *
 * Uses [LocalConfiguration] so it recomposes automatically on rotation. Exact-square configurations
 * (rare) are treated as portrait / single-column.
 *
 * Both [TriviaQuestionScreen] and [GameOverScreen] call this function so the definition of
 * "landscape" is consistent across both screens.
 */
@Composable
internal fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
