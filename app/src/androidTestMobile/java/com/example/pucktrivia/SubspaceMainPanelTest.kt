package com.example.pucktrivia

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.runtime.Session
import androidx.xr.scenecore.scene
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Pins the `androidx.xr.compose` behavior that `MainActivity`'s routing depends on.
 *
 * Only the Question screen opens a `Subspace`; every other screen falls through to the 2D branch
 * and renders in the activity's main panel. That is only correct if the main panel comes *back*
 * when the subspace leaves the composition — otherwise leaving the Question screen in Full Space
 * would strand a disabled panel and render nothing.
 *
 * `Subspace.kt:181-197` disables the main panel on first use and re-enables it on dispose,
 * refcounted through `SceneManager.getSceneCount()`. That is library internals we do not control
 * and which is pre-1.0 and churning, so this test fails loudly if a future version changes it
 * rather than leaving us to rediscover it as a blank screen on a headset.
 *
 * Skips on non-XR devices, where there is no session and no main panel to speak of.
 */
class SubspaceMainPanelTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun subspaceDisablesTheMainPanelAndDisposeRestoresIt() {
        var session: Session? = null
        var showSubspace by mutableStateOf(false)

        composeTestRule.setContent {
            session = LocalSession.current
            if (showSubspace) {
                Subspace { SpatialPanel { Text("spatial") } }
            } else {
                Text("flat")
            }
        }
        composeTestRule.waitForIdle()

        val scene = session?.scene
        assumeTrue("Not an XR device — no session to check", scene != null)
        val mainPanel = scene!!.mainPanelEntity

        assertTrue("main panel should start enabled", mainPanel.isEnabled())

        showSubspace = true
        composeTestRule.waitForIdle()
        assertFalse("Subspace should disable the main panel", mainPanel.isEnabled())

        showSubspace = false
        composeTestRule.waitForIdle()
        assertTrue(
            "disposing the last Subspace should re-enable the main panel — MainActivity's 2D " +
                "branch relies on this to render non-Question screens in Full Space",
            mainPanel.isEnabled(),
        )
    }
}
