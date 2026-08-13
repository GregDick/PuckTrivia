package com.example.pucktrivia

import androidx.compose.runtime.Composable
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.platform.LocalSpatialConfiguration

/**
 * Returns true when the runtime can render spatial UI right now.
 *
 * This is **false in Home Space even on an XR headset** — it only becomes true once the app is in
 * Full Space. Use it to choose between the spatial and 2D layouts, and to decide which direction
 * the space-mode toggle points.
 *
 * Recomposes automatically when the space mode changes; no listener setup is required.
 *
 * Companion to [isLandscape] in OrientationUtils.kt — both are shared so that "what layout are we
 * in" means the same thing on every screen.
 */
@Composable
internal fun isSpatialUiEnabled(): Boolean = LocalSpatialCapabilities.current.isSpatialUiEnabled

/**
 * Returns true when the device supports XR spatial features at all, regardless of the current space
 * mode.
 *
 * Deliberately distinct from [isSpatialUiEnabled]: the space-mode toggle must be visible in Home
 * Space, where [isSpatialUiEnabled] is false. Gating the toggle on that would hide it in exactly
 * the state it exists to escape.
 */
@Composable
internal fun isXrDevice(): Boolean = LocalSpatialConfiguration.current.hasXrSpatialFeature
