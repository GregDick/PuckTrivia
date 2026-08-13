package com.example.pucktrivia

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.xr.compose.platform.LocalSession
import androidx.xr.scenecore.scene

private val TOGGLE_TARGET = 64.dp
private val TOGGLE_GLYPH_SIZE = 30.sp

/** Shown in Home Space — tapping expands the app into Full Space. */
private const val GLYPH_EXPAND = "⛶"

/** Shown in Full Space — tapping returns the app to a Home Space window. */
private const val GLYPH_COLLAPSE = "⊟"

/**
 * Toggles the app between Home Space (a normal window) and Full Space (immersive, where the spatial
 * panel layout renders).
 *
 * This control is required, not a convenience: the Android XR system window chrome offers minimize
 * and close only, with no path into Full Space. Without this button a player can never reach the
 * spatial layout at all, which is why it is rendered persistently on every screen rather than only
 * on the Question screen.
 *
 * Deliberately oversized relative to Material's 48/24dp default — at headset viewing distance the
 * default is easy to miss, and the failure mode being designed against is invisibility.
 *
 * Callers must gate this on [isXrDevice], not [isSpatialUiEnabled]: the latter is false in Home
 * Space, which would hide the button in exactly the state it exists to escape.
 */
@Composable
internal fun SpaceModeToggle(modifier: Modifier = Modifier) {
    val session = LocalSession.current ?: return
    val spatial = isSpatialUiEnabled()
    val description = if (spatial) "Return to home space" else "Expand to full space"

    FilledTonalIconButton(
        onClick = {
            // Drive from the live capability rather than mirrored local state, so a request the
            // system declines simply leaves the player where they were.
            if (spatial) session.scene.requestHomeSpace() else session.scene.requestFullSpace()
        },
        modifier = modifier.size(TOGGLE_TARGET).semantics { contentDescription = description },
    ) {
        Text(text = if (spatial) GLYPH_COLLAPSE else GLYPH_EXPAND, fontSize = TOGGLE_GLYPH_SIZE)
    }
}
