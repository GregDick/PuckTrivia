package com.example.pucktrivia.quest

import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.pucktrivia.BuildConfig
import com.example.pucktrivia.R
import com.example.pucktrivia.TriviaRoute
import com.example.pucktrivia.TriviaViewModel
import com.example.pucktrivia.triviaRouteFor
import com.meta.spatial.castinputforward.CastInputForwardFeature
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.DpDisplayOptions
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.toolkit.createPanelEntity
import com.meta.spatial.vr.VRFeature
import horizonosx.os.HorizonOsDetector
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The Horizon OS entry point: Puck Trivia as free-floating panels in the player's real room.
 *
 * ## How this differs from the Android XR flavor
 *
 * `MainActivity` (mobile flavor) is a normal `ComponentActivity` that renders a 2D screen and
 * *optionally* opens a `Subspace` when the system reports spatial capability. The window is the
 * baseline; spatial is the enhancement, and the player toggles between Home Space and Full Space.
 *
 * Horizon OS inverts that. An activity carrying `com.oculus.intent.category.VR` launches straight
 * into an immersive scene — there is no windowed mode to fall back to and no space toggle, so
 * `SpaceModeToggle` has no counterpart here. The activity owns a 3D scene, and 2D UI enters that
 * scene only as explicitly created panel entities.
 *
 * Concretely:
 *
 * |                 |Android XR (mobile)                   |Horizon OS (quest)                                              |
 * |-----------------|--------------------------------------|----------------------------------------------------------------|
 * |Panel declaration|`SpatialPanel { }` inside a `Subspace`|`registerPanels()` blueprint, then `createPanelEntity`          |
 * |Layout           |Compose subspace layout (`SpatialRow`)|explicit `Pose` per entity, in meters                           |
 * |Sizing           |`SubspaceModifier.width(700.dp)`      |`QuadShapeOptions` in meters + `DpDisplayOptions` for resolution|
 * |Show/hide        |recomposition                         |create / destroy the entity                                     |
 * |Chrome           |`Orbiter` anchored to the panel group |just another panel, positioned above the pair                   |
 * |Move / resize    |`.movable()` / `.resizable()`         |grab affordances via Interaction SDK, not wired here            |
 *
 * What is *not* different is the trivia itself: `TriviaViewModel` and every composable in
 * `TriviaPanelContent.kt` are the same code the phone runs.
 *
 * ## Untested
 *
 * Written against the published Meta docs without Quest hardware to run it on. Panel poses in
 * particular are guesses at comfortable reading distance and will want tuning on a device.
 */
class PuckTriviaImmersiveActivity : AppSystemActivity() {

    /**
     * Owned explicitly because `AppSystemActivity` is not a `ViewModelStoreOwner` — see
     * [TriviaViewModelFactory] for why, and for what that costs.
     */
    private val triviaViewModelStore = ViewModelStore()

    /**
     * Hand-rolled because `AppSystemActivity` has no `lifecycleScope` — same root cause as the
     * missing `ViewModelStoreOwner`: `VrActivity` extends `android.app.Activity`, which predates
     * every androidx lifecycle contract. Cancelled in [onSpatialShutdown], the one teardown
     * callback Spatial SDK guarantees.
     */
    private val sceneScope = MainScope()

    private val viewModel: TriviaViewModel by lazy {
        ViewModelProvider(triviaViewModelStore, TriviaViewModelFactory(this))[
            TriviaViewModel::class.java]
    }

    /**
     * The answer panel is the only one whose existence is conditional, so it is the only one held
     * as mutable state. Start and Game Over reuse the question panel's slot rather than opening a
     * second panel the player would have to look away to read.
     */
    private var answerPanel: Entity? = null

    override fun registerFeatures(): List<SpatialFeature> {
        val features =
            mutableListOf<SpatialFeature>(
                // Head tracking, controllers, and hands. Without it there is no pointer, so the
                // answer buttons cannot be pressed at all.
                VRFeature(this),
                // Teaches the panel system how to host a ComposeView, including the ViewTree
                // owners Compose needs to run outside a normal activity window.
                ComposeFeature(),
            )

        if (BuildConfig.DEBUG) {
            // Drives the headset from the desktop mouse over cast, so panel placement can be
            // iterated on without donning the device every time. Opens a local socket — debug only.
            features.add(CastInputForwardFeature(this))

            // Product flavors decide which XR stack is compiled in, but nothing stops this APK
            // being sideloaded onto a plain Android device, where the whole scene silently fails
            // to come up. HorizonOsDetector is the vendor-supported way to ask, and asking here
            // turns that into one obvious line in logcat instead of a blank headset.
            if (!HorizonOsDetector.isOnHorizonOs(this)) {
                Log.w(
                    TAG,
                    "Not running on Horizon OS — the quest flavor needs a Quest headset or the " +
                        "Meta Spatial Simulator. Install the mobile flavor instead.",
                )
            }
        }

        return features
    }

    /**
     * Blueprints, not instances. Nothing appears in the scene until [onSceneReady] spawns entities
     * that reference these IDs — the split exists so one blueprint can back many panels, and it is
     * also what lets a Spatial Editor composition place a panel by `@id/…` instead of by code.
     */
    override fun registerPanels(): List<PanelRegistration> =
        listOf(
            ComposeViewPanelRegistration(
                R.id.chrome_panel,
                composeViewCreator = { _, ctx ->
                    ComposeView(ctx).apply { setContent { ChromePanel() } }
                },
                settingsCreator = {
                    UIPanelSettings(
                        shape = QuadShapeOptions(width = CHROME_WIDTH_M, height = CHROME_HEIGHT_M),
                        display = DpDisplayOptions(width = 1330f, height = 112f),
                        style = PanelStyleOptions(themeResourceId = R.style.Theme_PuckTrivia_Panel),
                    )
                },
            ),
            ComposeViewPanelRegistration(
                R.id.question_panel,
                composeViewCreator = { _, ctx ->
                    ComposeView(ctx).apply { setContent { QuestionPanel(viewModel) } }
                },
                settingsCreator = {
                    UIPanelSettings(
                        // Meters, and the dp resolution below must hold the same aspect ratio —
                        // a mismatch stretches the rendered UI onto the quad rather than
                        // letterboxing it. 700/525 == 1.0/0.75.
                        shape =
                            QuadShapeOptions(width = CONTENT_WIDTH_M, height = CONTENT_HEIGHT_M),
                        display = DpDisplayOptions(width = 700f, height = 525f),
                        style = PanelStyleOptions(themeResourceId = R.style.Theme_PuckTrivia_Panel),
                    )
                },
            ),
            ComposeViewPanelRegistration(
                R.id.answer_panel,
                composeViewCreator = { _, ctx ->
                    ComposeView(ctx).apply { setContent { AnswerPanel(viewModel) } }
                },
                settingsCreator = {
                    UIPanelSettings(
                        // 560/525 == 0.8/0.75.
                        shape = QuadShapeOptions(width = ANSWER_WIDTH_M, height = CONTENT_HEIGHT_M),
                        display = DpDisplayOptions(width = 560f, height = 525f),
                        style = PanelStyleOptions(themeResourceId = R.style.Theme_PuckTrivia_Panel),
                    )
                },
            ),
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Touching the lazy here rather than in onSceneReady so the network fetch for stats starts
        // while the scene is still coming up, instead of after the first frame.
        viewModel
    }

    override fun onSceneReady() {
        super.onSceneReady()

        // LOCAL_FLOOR puts the origin at the player's floor, so every y below reads directly as a
        // height above the ground rather than as an offset from wherever the head happened to be.
        scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)
        scene.setViewOrigin(0f, 0f, 0f, 0f)

        // Passthrough instead of a skybox: a trivia game is something you play in your living
        // room, and rendering an environment would cost GPU for no gameplay value. Declared
        // required="false" in the manifest, so a headset without it simply shows black here.
        scene.enablePassthrough(true)

        // Flat, generous ambient light. The panels are unlit 2D surfaces, so this only affects
        // any 3D content added later; kept neutral so nothing tints the UI.
        scene.setLightingEnvironment(
            ambientColor = Vector3(0.4f, 0.4f, 0.4f),
            sunColor = Vector3(1.0f, 1.0f, 1.0f),
            sunDirection = -Vector3(1.0f, 3.0f, -2.0f),
            environmentIntensity = 0.3f,
        )

        spawnPersistentPanels()
        followRoute()
    }

    /**
     * Chrome and question panels exist for the whole session; only their content changes.
     *
     * This mirrors `MainActivity`'s decision to keep a single main panel across every route, with
     * the difference that here "the main panel" is an entity we place ourselves.
     */
    private fun spawnPersistentPanels() {
        Entity.createPanelEntity(R.id.chrome_panel, Transform(CHROME_POSE))
        Entity.createPanelEntity(R.id.question_panel, Transform(QUESTION_POSE))
    }

    /**
     * Creates and destroys the answer panel as the game enters and leaves the Question route.
     *
     * `triviaRouteFor` is shared with the mobile flavor, so both platforms agree on what screen the
     * player is on; only the reaction differs. On a phone a route change is a recomposition. Here
     * it is an entity lifecycle event, because a panel that recomposes to nothing still occupies
     * physical space in front of the player as a blank rectangle.
     *
     * `distinctUntilChanged` matters more than it looks: `snapshotFlow` re-emits on every observed
     * state write, and re-spawning the panel on each one would destroy and rebuild an Android
     * surface several times per answer.
     */
    private fun followRoute() {
        sceneScope.launch {
            snapshotFlow { triviaRouteFor(viewModel) }
                .distinctUntilChanged()
                .collect { route ->
                    if (route == TriviaRoute.Question) {
                        if (answerPanel == null) {
                            answerPanel =
                                Entity.createPanelEntity(R.id.answer_panel, Transform(ANSWER_POSE))
                        }
                    } else {
                        answerPanel?.destroy()
                        answerPanel = null
                    }
                }
        }
    }

    /**
     * Re-seats the panels in front of the player after a system recenter.
     *
     * There is no equivalent on the mobile flavor — Android XR's system chrome repositions windows
     * for you. Here the panels are entities at fixed world poses, so a player who recenters (or who
     * stands up and turns around) would otherwise be left facing an empty room with the game behind
     * them.
     */
    override fun onRecenter(isUserInitiated: Boolean) {
        super.onRecenter(isUserInitiated)
        // Poses are authored relative to the reference space, which recentering redefines, so
        // re-applying the same constants is enough to bring everything back to arm's length.
        spawnPersistentPanels()
        answerPanel?.destroy()
        answerPanel = null
    }

    /**
     * Guaranteed to run before teardown, unlike `onStop` / `onDestroy`, which is why ViewModel
     * cleanup hangs off it. `ViewModelStore.clear()` is what invokes `onCleared()` and cancels
     * `viewModelScope`; without it an in-flight stats request would outlive the scene.
     */
    override fun onSpatialShutdown() {
        sceneScope.cancel()
        answerPanel?.destroy()
        answerPanel = null
        triviaViewModelStore.clear()
        super.onSpatialShutdown()
    }

    private companion object {
        const val TAG = "PuckTriviaImmersive"

        // Panel sizes in meters. Unlike the mobile flavor's Dp values these are physical: a 1.0m
        // panel is a metre wide in the player's room, so they are sized against reading distance
        // rather than against a screen.
        const val CONTENT_WIDTH_M = 1.0f
        const val ANSWER_WIDTH_M = 0.8f
        const val CONTENT_HEIGHT_M = 0.75f
        const val CHROME_WIDTH_M = 1.9f
        const val CHROME_HEIGHT_M = 0.16f

        /** Comfortable reading distance for text-heavy panels; nearer than this and eyes strain. */
        const val PANEL_DISTANCE_M = -1.5f

        /** Roughly seated eye level above the floor, since the reference space is LOCAL_FLOOR. */
        const val PANEL_HEIGHT_M = 1.25f

        /**
         * The pair is toed inward so both panels face the player rather than sitting flat on one
         * plane — at 1.5m the outer edge of a flat panel is noticeably further away than the inner
         * edge. Quaternion takes Euler degrees (pitch, yaw, roll).
         */
        val QUESTION_POSE =
            Pose(Vector3(-0.55f, PANEL_HEIGHT_M, PANEL_DISTANCE_M), Quaternion(0f, 14f, 0f))
        val ANSWER_POSE =
            Pose(Vector3(0.62f, PANEL_HEIGHT_M, PANEL_DISTANCE_M), Quaternion(0f, -14f, 0f))

        /**
         * Above the pair, and slightly nearer so it does not clip the question panel's top edge.
         */
        val CHROME_POSE = Pose(Vector3(0f, 1.78f, PANEL_DISTANCE_M + 0.05f), Quaternion(0f, 0f, 0f))
    }
}
