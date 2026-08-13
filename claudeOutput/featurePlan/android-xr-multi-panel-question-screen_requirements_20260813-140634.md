# Feature: Android XR Support — Multi-Panel Question Screen

## Feature Overview

Puck Trivia today is a single-panel 2D Compose app with two layouts: a stacked portrait layout and a two-column landscape layout, both chosen by `isLandscape()`. On an Android XR headset it would run as a flat window in Home Space — correct, but indistinguishable from a phone.

This feature adds first-class Android XR support by giving the Question screen a **spatial layout built from separate panels**: a wide status panel above, and beneath it a question panel and an answer-choices panel side by side in the user's space. The same APK continues to run unchanged on phones and tablets; the spatial layout appears only when the runtime reports that spatial UI is available.

**Definition of Done:** On an Android XR device, a player can expand Puck Trivia into Full Space and see the Question screen rendered as three distinct spatial panels — status, question, and answers — that they can look between and interact with, with answer selection, feedback coloring, and the Next button all behaving exactly as they do in 2D. On a phone or tablet, portrait and landscape layouts are unchanged in behavior. `./gradlew assembleDebug` and `./gradlew test` pass.

> ⚠️ **Read the Toolchain section before estimating this feature.** The Jetpack XR libraries require `compileSdk 37`, which cascades into AGP, Gradle, and JDK upgrades. That migration is a larger and riskier body of work than the spatial UI itself, and it is why Story 1 exists as a standalone, no-behavior-change slice.

## Approach: Hand-Written Panels, Deliberately

This feature is also a **rehearsal for retrofitting XR into a separate, older, large-scale app**. That goal, not PuckTrivia's own convenience, drives the technical choices below — and it is the reason to reject some options that would be easier here.

Jetpack XR offers a "free" path, and it genuinely works — this was verified, not assumed. Wrapping a Material3 adaptive pane scaffold in `EnableXrComponentOverrides` (from `androidx.xr.compose.material3`) spatializes it automatically; per the official documentation, *"Compose Material 3 Adaptive Layouts in XR have a 1:1 mapping where each pane is placed inside its own XR spatial panel."* A `SupportingPaneScaffold` with the question as the main pane and the answers as the supporting pane would produce the two-panel split with very little custom code, and would degrade to a two-pane 2D layout on tablets for free.

**That path is explicitly not taken**, for one reason: it requires the app to already be built on Material3 adaptive pane scaffolds. The older target app will not be, and restructuring a legacy screen onto a pane scaffold is a larger and riskier change than adding spatial panels beside what is already there. A technique that only pays off in a modern codebase teaches nothing about the retrofit this work is rehearsing.

The plan therefore builds every panel by hand with `Subspace`, `SpatialColumn`, `SpatialRow`, and `SpatialPanel`, and gates them on an explicit runtime capability check. This is more code than the override path, and that is the point: it is the technique that survives contact with a legacy codebase. It is also the only path that produces the chosen arrangement — a wide status panel above a row of two — which no pane scaffold maps onto.

One caveat worth carrying regardless of path: `androidx.xr.compose` and `androidx.xr.compose.material3` are versioned independently with **no BOM and no published cross-artifact compatibility matrix** (the alpha17 tags on the two artifacts are three months apart). Pin an exact pair and verify it builds before relying on anything from the material3 artifact.

**Corollary for implementers:** when a Jetpack XR convenience API would shortcut something here, prefer the manual equivalent and note the alternative in a comment rather than adopting it.

**A caution learned the hard way on this plan:** "the platform probably already does this" is a claim to verify on a device, not to infer from documentation prose. A research pass concluded the system shell provided a Home ↔ Full Space toggle for free, and Story 4 was cut on that basis. It was wrong — the chrome offers minimize and close only — and the cut would have shipped a spatial layout with no way for a player to reach it. Preferring the manual path is the default here; deviating from it because the platform "gives it to you" requires seeing it with your own eyes first.

---

## Toolchain & Dependency Requirements

Sourced from `claudeOutput/research/android-xr-summary.md` (verified 2026-08-13). Re-check before implementing — these libraries churn every release.

### The upgrade chain

`androidx.xr.compose:compose` 1.0.0-alpha14 raised the required **`compileSdk` to 37**. That single requirement cascades:

| | Project today | Required floor | Gap |
|---|---|---|---|
| `compileSdk` | 36 | **37** | must move |
| AGP | 9.0.1 | **9.2.0+** (needed for compileSdk 37) | must move |
| Gradle | 9.1.0 | **9.4.1+** (needed by AGP 9.2) | must move |
| JDK / `compileOptions` | Java 11 | **17** | must move |
| Compose BOM | 2024.09.00 | current-era BOM (samples use 2026.08.00) | must move |
| Kotlin | 2.0.21 | 2.0.0+ documented floor | **already clears it** |
| `minSdk` | 30 | 24 (library floor) | **no change — stays 30** |

**On `minSdk`:** XR imposes no meaningful floor here. The library minimum is 24, the official `xr-samples` repo ships `minSdk = 24`, and spatial capability is gated at runtime by `LocalSpatialCapabilities` rather than by API level — real XR devices run API 34+ regardless of what the app declares. Raising `minSdk` would only cost phone reach and buy nothing, so it stays at 30.

The official `xr-samples` repo runs Kotlin 2.4.10 / AGP 9.3.1 / Compose BOM 2026.08.00 — well ahead of the documented minimums. The further behind the floor the project sits, the more friction to expect.

### Artifacts

**There is no `androidx.xr` BOM** — every artifact is versioned independently and must be pinned individually in `gradle/libs.versions.toml`.

| Artifact | Version | Date | Stability | Used here? |
|---|---|---|---|---|
| `androidx.xr.compose:compose` | 1.0.0-alpha17 | 2026-08-12 | alpha | **yes** |
| `androidx.xr.scenecore:scenecore` | 1.0.0-beta02 | 2026-08-12 | beta | **yes** |
| `androidx.xr.runtime:runtime` | 1.0.0-beta02 | 2026-08-12 | beta | **yes** |
| `androidx.xr.compose:compose-testing` | (matches compose) | — | alpha | yes, Story 5 |
| `com.android.extensions.xr:extensions-xr` | — | — | `compileOnly` | only if minification is enabled (it isn't) |
| `androidx.xr.compose.material3:material3` | 1.0.0-alpha17 | 2026-05-19 | alpha | **no** — serves the declined override path |
| `androidx.xr.arcore` | — | — | beta | **no** — world tracking, unused by panel UI |

⚠️ **Do not confuse these three similarly-named artifacts:**

| Coordinate | What it is | Status here |
|---|---|---|
| `androidx.compose.material3:material3` | Standard Material 3 for Compose | **already a dependency, unchanged.** Every panel's content is ordinary Material3 — `MaterialTheme`, `Button`, `Text`. Nothing about this feature touches it. |
| `androidx.xr.compose.material3:material3` | Material *for XR* — `EnableXrComponentOverrides`, `SpaceToggleButton` | not added — see Approach |
| `androidx.compose.material3.adaptive:*` | Large-screen/foldable adaptive layouts (pane scaffolds). **Not an XR library** — it has zero XR awareness on its own | not added |

Note the version trap on the XR one: `androidx.xr.compose:compose` and `androidx.xr.compose.material3:material3` both read `1.0.0-alpha17` but are **three months apart** and independently released. Not adding it sidesteps the problem entirely.

### Corrected API surface

Several names differ from what a reasonable guess would produce. Use these:

- Manifest feature string is **`android.software.xr.api.spatial`** (not `android.software.xr.immersive`).
- Space-mode switching is **`session.scene.requestFullSpace()` / `session.scene.requestHomeSpace()`** on a SceneCore `Session` — there is no Compose CompositionLocal for it.
- **`LocalHasXrSpatialFeature` does not appear in current official docs** and may have been removed. Use `PackageManager.hasSystemFeature("android.software.xr.api.spatial")` for the "is this an XR device at all?" question.
- The spacer is **`SpatialSpacer`** (not `SpatialLayoutSpacer`).
- Curving a row is `SpatialCurvedRow` plus the `curveRadius(825.dp)` subspace modifier.
- `SubspaceModifier` takes **`Dp`, not meters**: `.width()`, `.height()`, `.depth()`, `.offset()`, `.movable()`, `.resizable()`, `.rotate()`, `.curveRadius()`.
- `Session.create` **must run on a worker thread**.

### Tooling

The XR AVD form factor and the spatial Layout Inspector require the **latest Canary build of Android Studio** — they are not in the stable channel. Whoever implements this needs Canary installed alongside their stable Studio.

---

## Current-State Findings (grounding)

These observations are drawn from the existing codebase and shape the scope below.

- **There is no XR code of any kind today.** A repo-wide grep for `xr`, `spatial`, `subspace`, `SceneCore` returns zero hits. No `androidx.xr.*` entries in `gradle/libs.versions.toml`, no XR properties in `AndroidManifest.xml`.
- **`TriviaQuestionScreen` is already shaped for a third layout.** `app/src/main/java/com/example/pucktrivia/TriviaQuestionScreen.kt` is a stateless 13-parameter composable that branches on `isLandscape()` and forwards every parameter to one of two private variants (`TriviaQuestionScreenPortrait`, `TriviaQuestionScreenLandscape`). A spatial variant slots into that exact pattern.
- **The screen is fully state-hoisted.** No ViewModel reference, no internal state — all state arrives as parameters and all interaction leaves via `onAnswerSelected` / `onNextRound`. **`TriviaViewModel` requires no changes whatsoever for this feature.**
- **`AnswerButton` is the reusable unit.** `TriviaQuestionScreen.kt:303-350` is a `private` composable already shared by both existing layouts, including the correct/incorrect container-color logic and the `CorrectGreen` constant at line 28. The spatial answer panel reuses it as-is.
- **`isLandscape()` is the precedent for capability branching.** `OrientationUtils.kt` is an 18-line `@Composable` helper reading `LocalConfiguration`, deliberately shared so "landscape" means the same thing on every screen. An XR equivalent should follow the same shape and live beside it.
- **`MainActivity` routes with a single `when` block inside a `Scaffold`.** `MainActivity.kt:37-134` evaluates eight ViewModel-derived conditions in priority order. There is no NavHost and no navigation dependency. This `when` block is the only routing point in the app, and it is where a spatial shell has to interpose (see Story 3 Engineering Notes — the one non-trivial structural decision in the feature).
- **The build already carries a JDK-version workaround.** `app/build.gradle.kts:64-72` forces a JDK 21 launcher for `testJourneys*` tasks because the Journeys engine ships Java 21 class files, while the rest of the build runs on Java 11. The Story 1 move to JDK 17 interacts with this block — it should get simpler, not more complex.
- **Testing has three layers already in place:** ~181 JVM unit tests under `app/src/test/`, Compose UI tests under `app/src/androidTest/` (only `GameOverScreenTest.kt` — **`TriviaQuestionScreen` has no Compose test today**), and natural-language Journey tests under `app/src/journeysTest/journeys/`, including `landscape_two_column_layout.journey.xml`, which is the direct template for a spatial journey.

---

## Epic: Spatialize the Question Screen

Five stories, of which two are user-facing.

**Worth stating up front, because it changes how this feature should be read: Puck Trivia already runs on an Android XR headset today.** Android XR runs standard Android apps as flat windows in Home Space, so "support Android XR" in the install-and-play sense is already true with zero code — verified by installing the unmodified app on the XR emulator. What the platform does *not* give you is any way to leave Home Space; the system chrome offers minimize and close only.

So the user-facing deliverable is narrower than "XR support" and comes in two halves, both required: **the Question screen rendered as separate spatial panels** (Story 3), and **the control that gets the player into Full Space so they can see them** (Story 4). Neither is worth shipping without the other.

Stories 1, 2, and 5 are engineering-only scaffolding. Story 1 is a pure toolchain migration with zero behavior change, isolated because it is the riskiest part of the feature and has nothing to do with XR UI. Story 2 adds the XR dependencies, manifest declarations, and the capability checks Stories 3 and 4 branch on. Story 5 adds test coverage.

---

### Story 1: Raise the Toolchain to the Jetpack XR Floor ⚙️

> ⚙️ **Engineering-only story.** No user-facing result — the app behaves identically on every device before and after. It is split out because the XR libraries force a four-part toolchain migration (compileSdk, AGP, Gradle, JDK) that carries far more regression risk than the spatial UI work, and it must be provably safe on its own before anything depends on it. Normally a story should deliver a user-facing outcome; this one is an explicit exception.

**As a** developer,
**I want** the build upgraded to the versions the Jetpack XR libraries require, with the existing test suite proving nothing broke,
**So that** XR work can begin without the toolchain migration and the feature work failing together and being hard to untangle.

**Story Points:** 5
**Priority:** P0
**Dependencies:** None

#### Acceptance Criteria

- [ ] `compileSdk` is 37, AGP is 9.2.0 or later, the Gradle wrapper is 9.4.1 or later, and `compileOptions` source/target compatibility is 17.
- [ ] The Compose BOM is raised to a current version compatible with `compileSdk 37`, and all resulting deprecations and signature changes are resolved.
- [ ] `minSdk` remains 30 and `targetSdk` remains 36 — neither changes.
- [ ] `./gradlew assembleDebug`, `./gradlew assembleRelease`, `./gradlew test` (all ~181 tests), and `./gradlew connectedAndroidTest` all pass.
- [ ] Manual regression on a phone shows no behavioral difference: portrait, landscape, rotation mid-question, and process-death restore all work exactly as before.
- [ ] No XR dependencies are added in this story.

#### Engineering Notes

- Order the upgrade Gradle wrapper → AGP → compileSdk → JDK → Compose BOM, committing and running `./gradlew test` between steps so a failure points at one variable.
- `app/build.gradle.kts` uses AGP 9's block syntax `compileSdk { version = release(36) }` — keep that form and change the number.
- **Do not bump Kotlin.** `claudeOutput/research/agp9-builtin-kotlin-parcelize-summary.md` documents that AGP 9 puts Kotlin on the classpath *unversioned*, which makes Kotlin-plugin version changes hazardous here. Kotlin 2.0.21 already clears the XR floor. If the AGP bump changes the bundled Kotlin version, verify `kotlin.compose` and `ksp = "2.0.21-1.0.26"` still resolve — a KSP/Kotlin mismatch breaks Hilt code generation, which is the failure mode most likely to appear as a confusing Dagger error rather than a version error.
- **Do not touch `minSdk` or `targetSdk`.** Only `compileSdk` moves (36 → 37). This is worth stating because the three are easy to change together by reflex, and raising `minSdk` would drop phone devices for no XR benefit.
- **Leave `app/build.gradle.kts:64-72` working.** That block forces a JDK 21 launcher for `testJourneys*` tasks. Journey testing is out of scope for this feature (see Out of Scope), but the block is still in the build and must not break it — with the baseline moving to JDK 17 it may simplify. Verify the build still configures; do not spend time on the journeys suite beyond that.
- Likely Compose BOM fallout, in order of probability: `LocalConfiguration` is deprecated in newer Compose (used by `OrientationUtils.kt:18`, and by `isLandscape()` transitively on every screen); `ButtonDefaults.buttonColors` signatures in `AnswerButton`; `Scaffold` / `enableEdgeToEdge` interaction in `MainActivity`. `GameOverScreenTest` is the only existing Compose test and is the canary for the `ui-test-junit4` upgrade that rides along with the BOM.
- Note the required Android Studio Canary version in the PR description — the next contributor will need it for Story 2 onward.

#### QA / Testing Notes

- Full manual phone regression is mandatory despite "no behavior change": play a complete game portrait, repeat landscape, rotate mid-question at both an answered and unanswered state, background and `adb shell am kill com.example.pucktrivia`, relaunch and confirm the game restores.
- Verify the release build still assembles — `isMinifyEnabled` is currently `false`, so R8 is not exercised, but the AGP jump can still break `assembleRelease`.

#### Edge Cases & Risk Analysis

- **This is the highest-risk story in the feature by a wide margin.** Four coupled version bumps plus a roughly two-year Compose BOM jump. If it turns out larger than 5 points, that is a signal to split it further (Gradle+AGP, then compileSdk+JDK, then Compose BOM) rather than to push through.
- **`compileSdk 37` may surface new lint errors or behavior changes** in targeted APIs even with `targetSdk` held at 36. Treat new lint failures as in-scope for this story.
- **Hilt/KSP is the fragile dependency.** Hilt 2.59.2 with KSP pinned to Kotlin 2.0.21 sits underneath every screen via `@HiltViewModel`. If Dagger errors appear after the AGP bump, suspect the Kotlin/KSP pairing first.
- **Escape hatch.** If the toolchain migration proves infeasible on the current AGP 9 setup, the whole feature is blocked — the XR libraries have a hard `compileSdk 37` requirement with no workaround. Surface that immediately rather than attempting to make XR work on compileSdk 36.

---

### Story 2: Add the XR Dependencies and Spatial Capability Detection ⚙️

> ⚙️ **Engineering-only story.** No user-facing result, and worth being blunt about why: **Puck Trivia already installs and plays on an Android XR headset today, with zero changes.** Android XR runs any standard Android app as a flat window in Home Space. There is no "make it run on XR" work to do — the platform does that for free. What this story adds is the dependencies, the manifest declarations, and the capability check that Story 3 needs in order to render anything spatial. It is separated from Story 3 so that adding the XR libraries — which are pre-1.0 and could destabilize the build on their own — is verified independently of the UI work that consumes them.

**As a** developer,
**I want** the XR libraries on the classpath and a shared way to ask whether spatial UI is currently available,
**So that** the spatial layout in Story 3 has something to branch on, and any build fallout from the pre-1.0 XR dependencies surfaces before UI work is layered on top.

**Story Points:** 3
**Priority:** P0
**Dependencies:** Story 1

#### Acceptance Criteria

- [ ] The XR dependencies are added, pinned to exact versions, and `./gradlew assembleDebug`, `./gradlew assembleRelease`, and `./gradlew test` all pass.
- [ ] A shared helper answers "is spatial UI available right now?", and a separate helper answers "is this device XR-capable at all?" — both usable from any composable, mirroring how `isLandscape()` is shared today. Both are needed; see Engineering Notes for why they cannot be the same check.
- [ ] The manifest declares XR support as *optional*, and the app remains installable on a non-XR phone (verified by an actual `adb install`, not by inspection).
- [ ] The manifest states the launch space mode explicitly rather than relying on the platform default.
- [ ] **Regression only:** the app still installs and plays start-to-finish on an XR device in Home Space, and still behaves identically on a phone and tablet. This is a guard against the new dependencies breaking something that already worked — not a new capability.

#### Design Notes

No visual change on any device. On a headset the app looks exactly as it does today, which is exactly as it does on a tablet.

#### Engineering Notes

- **Dependencies** — add to `gradle/libs.versions.toml` and `app/build.gradle.kts`, pinned to exact versions (no ranges) per the Toolchain section: `androidx.xr.compose:compose`, `androidx.xr.scenecore:scenecore`, `androidx.xr.runtime:runtime`. Add `com.android.extensions.xr:extensions-xr` as `compileOnly` only when minification is turned on — it is off today.
- **Do not add `androidx.xr.compose.material3:material3` or `androidx.xr.arcore`.** The XR material3 artifact exists only to serve `EnableXrComponentOverrides` and `SpaceToggleButton` — the first is declined (see Approach), and the second is declined in favour of a hand-written toggle (see Story 4). Adding it would pull in an artifact with an independent version cadence and no compatibility matrix for no benefit. `arcore` is for world tracking, which panel UI does not use.
- **The app's existing `androidx.compose.material3:material3` dependency stays exactly as it is** (`libs.androidx.compose.material3`, `app/build.gradle.kts:82`). That is standard Material 3, not the XR artifact, and it is what every panel's content is built from. Only its version moves, via the Compose BOM bump in Story 1. See the disambiguation table in the Toolchain section — the two are one namespace segment apart and easy to conflate.
- **Manifest** (`app/src/main/AndroidManifest.xml`):
  - `<uses-feature android:name="android.software.xr.api.spatial" android:required="false" />` — note the exact string; `android.software.xr.immersive` is **not** current.
  - On `<activity .name=".MainActivity">`: `<property android:name="android.window.PROPERTY_XR_ACTIVITY_START_MODE" android:value="XR_ACTIVITY_START_MODE_HOME_SPACE" />` so the launch mode is explicit rather than implicit. The property is optional and only affects the *initial* launch space; it is expected to be inert on a phone.
  - ⚠️ **Verify that constant string empirically.** developer.android.com documents `XR_ACTIVITY_START_MODE_HOME_SPACE`, but the official `xr-samples` manifest declares `XR_ACTIVITY_START_MODE_HOME_SPACE_MANAGED`, which matches no documented value. The research doc flags this discrepancy. Confirm on-device which one the platform honors before relying on it — and since the platform default is already Home Space, dropping the property entirely is an acceptable outcome if neither resolves cleanly.
  - **No `android:configChanges` and no `android:resizeableActivity`.** Neither appears in the XR docs or the official sample's manifest. Space-mode transitions do not require them.
  - No XR runtime permissions. Panel UI requires none; do not add hand tracking, eye tracking, or scene understanding.
- **New file `app/src/main/java/com/example/pucktrivia/SpatialUtils.kt`**, beside `OrientationUtils.kt` and following its shape (short, `internal`, `@Composable`, KDoc explaining why it is shared):
  ```kotlin
  /** True when the runtime can currently render spatial UI — false in Home Space, even on a headset. */
  @Composable
  internal fun isSpatialUiEnabled(): Boolean =
      LocalSpatialCapabilities.current.isSpatialUiEnabled

  /** True when the device supports XR spatial features at all, regardless of current space mode. */
  @Composable
  internal fun isXrDevice(): Boolean {
      val context = LocalContext.current
      return remember(context) {
          context.packageManager.hasSystemFeature("android.software.xr.api.spatial")
      }
  }
  ```
  **Both helpers are needed, and the distinction is load-bearing.** `isSpatialUiEnabled()` gates the panel layout (Story 3). `isXrDevice()` gates the Full Space toggle (Story 4) — and it must be a separate check, because `isSpatialUiEnabled()` is **false in Home Space even on a headset**, so using it to gate the expand button would hide the button in exactly the state it exists to escape. **`LocalHasXrSpatialFeature` is not in current docs** — use the `PackageManager` check, not that.
- **`scenecore` and `runtime` are direct dependencies, not transitive.** Story 4 needs a SceneCore `Session` for `requestFullSpace()` / `requestHomeSpace()`; there is no Compose CompositionLocal for space-mode switching. `Session.create` must run on a worker thread, and `LocalSession`-derived CompositionLocals can transiently resolve null before initialization completes — never dereference one without a null guard.
- Install the Android XR system image via the SDK Manager in Android Studio Canary.

#### QA / Testing Notes

- Regression pass on a phone before touching a headset: portrait, landscape, rotation, background/kill/restore, full game to Game Over.
- Confirm the app is still installable on a non-XR device after the `uses-feature` addition (a plain `adb install` on a phone is sufficient proof).
- Install on the XR emulator, play a full game in Home Space, confirm no crash and no layout clipping.

#### Edge Cases & Risk Analysis

- **`required="false"` is not optional.** Omitting it — or setting `required="true"` — makes the app uninstallable on phones. That one attribute is the difference between "adds XR support" and "becomes an XR-only app".
- **Every XR artifact is pre-1.0** (alpha17 / beta02), with documented renames and removals almost every alpha. Pin exact versions and state the stability level in the PR so a future upgrade is a deliberate act with a changelog review, not a routine bump.
- **Verify the `uses-feature` string.** `android.software.xr.api.spatial` is what current docs show, but the research flagged older strings still in circulation. Since it is declared `required="false"`, a wrong string fails silently rather than loudly — it will not block installation anywhere, it just means the declaration is meaningless. Check it against current docs at implementation time rather than trusting this plan.
- **Null session during startup.** A composable that reads a session-derived local on first frame may see null. Guard rather than assert.

---

### Story 3: Split the Question Screen Into Status, Question, and Answer Panels

**As a** player wearing an Android XR headset,
**I want to** see the question and the answer choices on separate panels arranged in my space, with my score and lives on a panel above them,
**So that** the trivia game feels like it lives in the room with me instead of being a flat window.

**Story Points:** 8
**Priority:** P0
**Dependencies:** Story 2

#### Acceptance Criteria

- [ ] When the app is in Full Space on an XR device and a question is showing, the Question screen renders as **three separate panels**: a wide status panel positioned above, and below it a question panel and an answer-choices panel side by side.
- [ ] The status panel shows score, lives (with the same error coloring on a wrong answer), the season mode label, and the `Correct!` / `Incorrect!` feedback text — the same information the 2D header shows today.
- [ ] The question panel shows the question text and, once answered, the `Next` button.
- [ ] The answers panel shows one answer button per choice, using the same labels, the same correct/incorrect container colors (`CorrectGreen` for the correct choice, error color for a wrong selection), the same disabled-after-answer behavior, and the same stat-value reveal after answering.
- [ ] Tapping an answer updates all three panels together: feedback appears on the status panel, lives decrement on the status panel, `Next` appears on the question panel, and the answer buttons recolor and disable.
- [ ] Tapping `Next` advances to a new question with all three panels updating together, and the game reaches Game Over normally after lives are exhausted.
- [ ] Panels remain legible and non-overlapping at a comfortable viewing distance, with the answer buttons reachable without the player having to move.
- [ ] On a phone or tablet — and on an XR device in Home Space — the existing portrait and landscape layouts render exactly as they do today.
- [ ] Screens other than the Question screen (Start, loading, Game Over, error states) continue to render correctly in Full Space as a single normal panel; they are not split, but they are not broken either.

#### Design Notes

- Target layout — a `SpatialColumn` holding the status panel, then a `SpatialRow` holding question and answers:

  ```
   ┌──────── Score 400 · Lives 3 · Regular Season ────────┐
   └──────────────────────────────────────────────────────┘
   ┌────────────────────┐      ┌────────────────────┐
   │  Who leads the     │      │   ( ) Player A     │
   │  NHL in points?    │      │   ( ) Player B     │
   │                    │      │   ( ) Player C     │
   │      [ Next ]      │      │                    │
   └────────────────────┘      └────────────────────┘
  ```

- Panel content is ordinary 2D Compose. Inside each panel, reuse the existing Material3 typography and `PuckTriviaTheme` — the panels should look like the app, not like a new design language.
- Suggested starting sizes, expressed in `Dp` on `SubspaceModifier` (tune on-device): status panel roughly 1024 × 160, question and answer panels roughly 640 × 640, with generous spacing between the two lower panels. Treat these as tablet-scale pixel dimensions, not physical measurements.
- Keep the question text at `headlineSmall` and answer buttons at `bodyLarge` as today. Text comfortable on a tablet is comfortable on a panel at default distance; resist enlarging type "because it's XR" until verified on-device.
- Optional polish: swap the lower `SpatialRow` for `SpatialCurvedRow` with `curveRadius(825.dp)` so both panels face the player. Nice-to-have, not an acceptance criterion.
- Do **not** make panels resizable in this story — the layout assumes known proportions, and user resizing needs its own design pass. `.movable()` on the panel group is acceptable if it comes for free.
- Accessibility: the answer buttons keep their existing semantics — still Material3 `Button`s with text content, so screen-reader behavior carries over. Verify reading order is status → question → answers.

#### Engineering Notes

- **The one structural decision: where the spatial branch lives.** The spatial layout must emit a top-level `Subspace`, and today `TriviaQuestionScreen` renders *inside* `Scaffold` in `MainActivity`. Nesting a subspace inside a 2D `Scaffold` is supported but is the wrong shape for a room-scale three-panel layout — the documented pattern branches at the top of `setContent`, above the `Scaffold`.

  **Recommended approach:** extract `MainActivity`'s routing `when` block into a small `internal` route value, then branch once at the top:

  ```kotlin
  setContent {
      PuckTriviaTheme {
          val route = triviaRouteFor(viewModel)          // new: the existing when{}, as a value
          if (isSpatialUiEnabled() && route == TriviaRoute.Question) {
              TriviaQuestionScreenSpatial(/* same 13 params */)   // emits Subspace { ... }
          } else {
              Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                  TriviaContent(route, viewModel, Modifier.padding(innerPadding))
              }
          }
      }
  }
  ```

  This keeps routing in exactly one place (no duplicated `when`), leaves the non-question screens on their current 2D path untouched, and puts the subspace where the XR libraries expect it. The refactor is mechanical: the eight existing branches move verbatim into `TriviaContent`, and the conditions become a sealed `TriviaRoute` — no logic changes. It also makes the layout-selection logic unit-testable, which Story 5 depends on.

  If that refactor proves larger than expected, the fallback is a nested subspace emitted from inside `TriviaQuestionScreen` alongside the `isLandscape()` branch. Prefer the recommended approach; record the reason in the PR if the fallback is used.

- **New file `app/src/main/java/com/example/pucktrivia/TriviaQuestionScreenSpatial.kt`.** Keep it out of `TriviaQuestionScreen.kt`, which is already 350 lines. It takes the identical 13-parameter signature so the call site is a drop-in.
- **Composition:** `Subspace { SpatialColumn { SpatialPanel(SubspaceModifier.width(1024.dp).height(160.dp)) { StatusContent(...) }; SpatialRow { SpatialPanel(...) { QuestionContent(...) }; SpatialSpacer(...); SpatialPanel(...) { AnswerContent(...) } } } }`. Each `SpatialPanel` wraps ordinary 2D Compose content unchanged.
- **Shared content, not duplicated content.** Before writing the spatial variant, extract the three repeated content blocks from `TriviaQuestionScreen.kt` into `internal` composables in that file, and have portrait, landscape, *and* spatial all call them:
  - `AnswerButton` — change from `private` to `internal`; the spatial answer panel reuses it verbatim, which is what makes the color and disabled-state ACs hold for free.
  - A status block composable parameterized by typography scale, so portrait (`headlineLarge`, stacked), landscape (`headlineMedium`, row), and the spatial status panel share one implementation.
  - The `Correct!` / `Incorrect!` feedback text, currently written out three times across the two layouts.

  This extraction is the majority of the risk-free work in this story and is what prevents the spatial layout from drifting as the game evolves.
- **Fixed-height spacer boxes do not belong in the spatial layout.** Both existing layouts wrap the feedback text and `Next` button in fixed-height `Box`es purely to stop 2D layout shift (`TriviaQuestionScreen.kt:129`, `:167`, `:235`). Separate panels do not shift each other — show and hide those elements directly. Do not copy the spacer boxes across.
- **The answers panel does not need `verticalScroll`.** The landscape layout scrolls because the landscape viewport is short (see the warning comment at `TriviaQuestionScreen.kt:281`). A panel sized for three choices has no such constraint.
- **Keep `remember{}` state out of `Subspace{}`.** This is the one documented pitfall of space-mode transitions: anything remembered *inside* a `Subspace` block is disposed and recreated on every Home ↔ Full round-trip. This app is already positioned correctly — all real state lives in the process-retained `TriviaViewModel` — so the rule is simply not to introduce transient spatial-only UI state inside the subspace. Hoist anything that needs to survive above it.
- **`TriviaViewModel` is not touched.** No new state, no new callbacks, no persistence change. `GameSnapshot` and the `SavedStateHandle` restore path are unaffected — confirm this is still true at PR time.

#### QA / Testing Notes

- On the XR emulator, switch to Full Space and verify each AC against a live game: read the status panel while answering, confirm all three panels update on a single tap.
- Verify a wrong answer colors the selected button with the error color *and* the correct button with `CorrectGreen`, and that lives on the status panel turn red — the cross-panel update is the highest-value manual check here.
- Play a full game to Game Over in Full Space; confirm Game Over appears as a single panel and `Play Again` works.
- Regression on a phone (portrait, landscape, rotation), then on the XR emulator in Home Space to exercise the `isSpatialUiEnabled() == false` path on an XR device — a distinct case from a phone.

#### Edge Cases & Risk Analysis

- **Mode change mid-question.** Switching between Home Space and Full Space using the Story 4 toggle, while a question is on screen, must not lose the question, the selected answer, or the feedback. Research indicates this is a Compose-level subspace mount/unmount rather than an Activity recreation, and the screen is fully state-hoisted onto a process-retained ViewModel — so it should hold. Verify anyway, at both an unanswered and an answered question, and while `isLoading` is true.
- **Non-question screens in Full Space.** Start, loading, Game Over, and the three error states are out of scope for splitting but still have to *render*. Check each one, especially the Question → Game Over transition, which crosses from the three-panel branch to the single-panel branch.
- **The `correctPlayer!!` non-null assertion.** `MainActivity.kt:126` dereferences `viewModel.correctPlayer!!`, guarded only by the preceding `choices.isEmpty()` branch. The routing refactor must preserve branch ordering exactly, or this becomes a crash. Call it out in review.
- **Panel sizing is guesswork until it is on a device.** Budget an on-device tuning pass; the first numbers that compile are not final.
- **Layout drift between 2D and spatial.** Three layouts now share the same state. The shared-composable extraction is the mitigation; without it, a future change to answer-button behavior gets made in two places and forgotten in the third.
- **`LocalConfiguration` on a headset.** `isLandscape()` still runs in the 2D branch on an XR device. Verify a headset window reports an orientation that selects the landscape layout, not portrait. Note this local may also have been deprecated by Story 1's Compose upgrade.

---

### Story 4: Build a Full Space Toggle Into the App

> 📌 **This story was briefly cut and then restored.** A research pass read Google's Help Center description of an "expand/compact window" control as meaning the system shell offers a Home ↔ Full Space toggle on every app for free. **It does not.** Confirmed on the device: the system window chrome exposes minimize and close, and no path into Full Space. The app must provide its own control. The cut was wrong, and the correction matters more than most — without this story, Story 3's entire payoff is unreachable, because the player can never get into the mode where the panels render.

**As a** player on an Android XR device,
**I want to** expand Puck Trivia into an immersive spatial layout and collapse it back to a window,
**So that** I can actually reach the multi-panel view, and choose when to play in my full space.

**Story Points:** 3
**Priority:** **P0** — not optional. This is the only user path into Full Space, so Story 3 cannot be seen or verified without it.
**Dependencies:** Story 2. **Story 3 depends on this** for its own manual verification.

#### Acceptance Criteria

- [ ] On an XR device in Home Space, the Question screen shows a control to expand into Full Space.
- [ ] Activating it switches the app to Full Space, and the three-panel layout from Story 3 appears.
- [ ] While in Full Space, a control is available to return to Home Space, and activating it restores the windowed 2D layout.
- [ ] The expand/collapse controls are **not shown at all** on a phone or tablet — no dead button, no empty space where one would be.
- [ ] Switching modes in either direction preserves the current question, score, lives, and any selected answer with its feedback.

#### Design Notes

- In Home Space, place the expand control in the Question screen's status header — a small icon button with a content description such as "Expand to full space", visually subordinate to score and lives.
- In Full Space, attach the collapse control to the status panel as floating chrome (an `Orbiter`) rather than embedding it in panel content, so it does not compete with game information for panel space.
- Both controls are icon-only and need content descriptions.
- Consider whether the control belongs on the Start screen too — a player who never reaches a question never sees it. Out of scope for the ACs above, but worth raising once the placement is real.

#### Engineering Notes

- **Gate the controls on `isXrDevice()`, not `isSpatialUiEnabled()`.** This is the single most likely bug in the story: `isSpatialUiEnabled()` is false in Home Space even on a headset, so using it would hide the expand button in exactly the situation it exists for. Story 2 must therefore ship the `PackageManager.hasSystemFeature("android.software.xr.api.spatial")` helper after all — an earlier draft of this plan deleted it as unused, which was a consequence of the mistaken cut.
- Switch modes via SceneCore: **`session.scene.requestFullSpace()`** and **`session.scene.requestHomeSpace()`**. There is no Compose CompositionLocal for this — a `Session` must be obtained and held, which is why `androidx.xr.scenecore` and `androidx.xr.runtime` are direct dependencies in Story 2 rather than transitive ones. `Session.create` must run on a worker thread, and `LocalSession`-derived CompositionLocals can transiently resolve null before initialization — guard, never assert.
- The mode request is asynchronous, and per the research a request only succeeds while the app has focus. Do not hold local state mirroring the current mode — read it from `isSpatialUiEnabled()` so there is one source of truth and a denied request degrades correctly.
- `Orbiter` degrades to plain inline content when not spatialized, so one composable can potentially serve both modes if that reads cleanly. `Orbiter` is in core `androidx.xr.compose`, so this needs no extra artifact.
- **Build the toggle by hand.** `androidx.xr.compose.material3.SpaceToggleButton` is a ready-made control that self-manages both directions. Do not use it — per the Approach section, the manual path is what transfers to the legacy app, and adopting it would mean pulling in the XR material3 artifact with its independent version cadence. Reference it in a comment as the known alternative so the choice reads as deliberate.

#### QA / Testing Notes

- On the XR emulator: expand from Home Space mid-question, confirm the three panels appear with the same question and the same selected answer; collapse back, confirm the 2D layout returns with state intact.
- Expand and collapse repeatedly in quick succession — no crash, no stuck intermediate state.
- Expand while `isLoading` is true — the loading spinner should render in the new mode, not a blank panel.
- On a phone, confirm neither control renders anywhere on the Question screen.

#### Edge Cases & Risk Analysis

- **Mode request denied or ignored.** A request only succeeds while the app has focus, and may not be honored in every system state. Because the layout is driven by the capability check rather than local state, a denied request simply leaves the player where they were — the correct fallback. Do not add a "pending" state.
- **Discoverability is now load-bearing.** With no system-provided path into Full Space, a player who does not notice this control never sees the spatial layout at all. If the icon button proves easy to miss on-device, the fallback is to declare `XR_ACTIVITY_START_MODE_FULL_SPACE_MANAGED` in the manifest and launch spatial by default — a one-value change in Story 2. Raise it rather than absorbing it silently; it reverses an early product decision.
- **Control placement in Home Space.** The status header is already crowded in portrait. Verify the expand icon does not push the season label or lives count into a wrap on the narrowest supported window.
- **Transition mechanics themselves remain free.** The one part of the original research that held up: a transition is not documented as an Activity recreation or configuration change, `LocalSpatialCapabilities` recomposes with no listener setup, and neither `android:configChanges` nor `android:resizeableActivity` is needed. The `remember{}`-inside-`Subspace{}` pitfall (see Story 3) is the only state concern.

---

### Story 5: Test Coverage for the Spatial Question Screen ⚙️

> ⚙️ **Engineering-only story.** No user-facing result — it adds automated coverage for the layout-selection logic and the spatial layout's content. Split out because `TriviaQuestionScreen` has **no Compose test at all** today, so the feature would otherwise ship three layouts with automated coverage of none of them. Normally a story should deliver a user-facing outcome; this one is an explicit exception.

**Story Points:** 3
**Priority:** P1
**Dependencies:** Story 3

#### Acceptance Criteria

- [ ] A Compose UI test verifies the 2D Question screen renders the question text and all three answer buttons, and that tapping an answer invokes `onAnswerSelected` with the right player id — closing the current coverage gap for the screen this feature modifies.
- [ ] A test verifies the answer-button color/disabled logic after answering (correct choice green, wrong selection error-colored, all disabled), asserted once against the shared `AnswerButton` so it holds for every layout.
- [ ] A unit test verifies the routing/layout-selection logic: the spatial layout is chosen only when spatial UI is enabled *and* the route is `Question`, and the existing portrait/landscape layouts otherwise.
- [ ] `./gradlew test` and `./gradlew connectedAndroidTest` pass.
- [ ] No journey tests are added or modified (see Out of Scope). The three existing journey files are left untouched.

#### Engineering Notes

- Follow `GameOverScreenTest.kt`: `createComposeRule()`, `setContent { PuckTriviaTheme { ... } }`, assert with `onNodeWithText`. Call `TriviaQuestionScreen` directly with fixture `StatLeader` values — the screen is stateless, so no ViewModel or Hilt setup is needed.
- **`androidx.xr.compose:compose-testing` exists** and supports Compose UI tests against spatial UI; `scenecore-testing` / `runtime-testing` provide `SessionTestRule` and `XrDeviceTestRule`. Use these for the spatial-layout tests rather than hand-rolling a fake.
- **Prefer testing the *selection logic* as a plain JVM unit test** — that is what the `TriviaRoute` extraction from Story 3 buys, and it is the highest-value assertion in this story since it is the one piece of genuinely new branching logic. A Compose test asserting panel geometry will be brittle; do not chase it.
- With journey testing excluded, **the spatial layout's on-device appearance is verified manually only** (Story 3's QA notes). Accept that explicitly rather than substituting a brittle automated proxy.

#### QA / Testing Notes

- Confirm the new Compose tests fail if `AnswerButton`'s color logic is deliberately broken — a test that cannot fail is not coverage.

#### Edge Cases & Risk Analysis

- **The XR testing artifacts are alpha too.** If `compose-testing` proves unstable or under-documented, fall back to unit-testing the selection logic alone and say so in the PR rather than shipping a flaky suite.
- **Compose test infrastructure after the BOM bump.** `ui-test-junit4` comes from the Compose BOM, so Story 1 moves it. If `GameOverScreenTest` breaks, fix it in Story 1, not here.

---

## Summary Table

| Story | Title | Points | Priority | Dependencies |
|-------|-------|--------|----------|--------------|
| 1 | Raise the Toolchain to the Jetpack XR Floor ⚙️ *(engineering-only)* | 5 | P0 | None |
| 2 | Add the XR Dependencies and Spatial Capability Detection ⚙️ *(engineering-only)* | 3 | P0 | Story 1 |
| 3 | Split the Question Screen Into Status, Question, and Answer Panels | 8 | P0 | Story 2 |
| 4 | Build a Full Space Toggle Into the App | 3 | P0 | Story 2 |
| 5 | Test Coverage for the Spatial Question Screen ⚙️ *(engineering-only)* | 3 | P1 | Story 3 |

**Total Story Points:** 22 — of which **11 (Stories 3 and 4) are user-facing** and 11 are scaffolding. That ratio is worth seeing plainly: half this feature is toolchain, dependency, and test work in service of one screen's spatial layout. If that trade looks wrong, the place to challenge it is Story 1 — the toolchain migration is the price of admission, and it does not get cheaper by descoping the UI.

---

## Assumptions

1. **One APK, graceful degradation.** A single artifact runs 2D on phones and tablets and spatially on XR headsets, selected at runtime by a capability check. No `:xr` module, no product flavor, no second release.
2. **Home Space on launch, Full Space by opt-in via the system control.** The app opens as a normal window. The multi-panel layout appears only after the player expands into Full Space using the platform's own window-chrome control. The app builds no toggle of its own.
3. **Question screen only.** Start, Game Over, and the error states render as a single panel in Full Space. They are verified not to break, but they are not spatialized.
4. **Status information gets its own panel**, positioned above the question and answer panels, rather than living in an orbiter or inside the question panel.
5. **No `TriviaViewModel` changes.** The Question screen is fully state-hoisted, so the entire feature is presentation-layer work.
6. **A four-part toolchain migration is in scope and is the dominant risk** — compileSdk 37, AGP 9.2.0+, Gradle 9.4.1+, JDK 17, plus a roughly two-year Compose BOM jump. This is accepted as unavoidable: the XR libraries have a hard `compileSdk 37` floor.
7. **Depending on pre-1.0 libraries is accepted.** `xr-compose` is alpha17 and `scenecore`/`runtime` are beta02, with documented API churn nearly every release. Versions are pinned exactly; upgrades are deliberate, changelog-reviewed acts.
8. **`minSdk` stays at 30 and `targetSdk` stays at 36.** XR imposes no `minSdk` floor above 24 and gates spatial capability at runtime, not by API level, so raising it would cost phone reach and buy nothing. Only `compileSdk` moves.
9. **Kotlin stays at 2.0.21.** It already clears the XR floor, and AGP 9's unversioned-Kotlin classpath behavior makes changing it hazardous here.
10. **Android Studio Canary is required** for the XR AVD and spatial Layout Inspector. Contributors need it alongside stable Studio.
11. **Exact coordinates, versions, and API names come from `claudeOutput/research/android-xr-summary.md`**, re-verified at implementation time rather than trusted from memory.
12. **The Material3-XR override path is rejected on purpose, not for lack of viability.** See the Approach section — `EnableXrComponentOverrides` would spatialize a pane scaffold 1:1 into separate panels and would work in this app. It is declined because it does not transfer to the legacy app this work is rehearsing, and because it cannot produce the chosen status-panel-above-two-panels arrangement.

## Out of Scope

- Spatializing the Start, Game Over, Playoffs-unavailable, or error screens.
- 3D content of any kind: models, `SpatialEnvironment` / skyboxes, spatial audio, player photos or team logos rendered as objects.
- Hand tracking, eye tracking, gaze-based selection, anchoring to real-world surfaces, or scene understanding — panel UI needs none of these, and each carries a permission cost. `androidx.xr.arcore` is not added.
- User-resizable or repositionable panels beyond whatever the platform provides for free.
- Any change to scoring, lives, question generation, the NHL data fetch, high scores, or the `GameSnapshot` persistence path.
- A separate XR build variant, module, or Play Store listing.
- Kotlin version upgrades (see Assumption 9). Enabling R8/minification, which would pull in the `compileOnly extensions-xr` requirement.
- **Journey testing, entirely.** No new journey file is written for the spatial layout, and the three existing journey files (`regular_season_quiz`, `landscape_two_column_layout`, `preserve_game_state`) are neither modified nor run as part of this feature's verification. Consequence to accept knowingly: the spatial Question screen has **no automated end-to-end coverage** — its on-device behavior is verified by the manual passes in Story 3 only.

---

## Verification (end-to-end)

Run in this order; each step gates the next.

```bash
./gradlew assembleDebug                       # compiles with XR deps + upgraded toolchain
./gradlew assembleRelease                     # AGP jump can break release-only config
./gradlew test                                # 181 existing unit tests — must stay green
./gradlew connectedAndroidTest                # Compose tests, incl. GameOverScreenTest + new ones
```

Journey tests are deliberately excluded (see Out of Scope) — do not run or add them.

Then, by hand:

1. **Phone regression** — install on a phone, play a full game in portrait and landscape, rotate mid-question, background and `adb shell am kill com.example.pucktrivia`, relaunch and confirm restore. Nothing should differ from today.
2. **XR, Home Space** — install on the Android XR emulator (Android Studio Canary), confirm the app opens windowed and is fully playable in the 2D layout.
3. **XR, Full Space** — expand using the in-app toggle from Story 4, confirm the three panels appear, answer a question and watch all three panels update on a single tap, tap Next, and play through to Game Over.
4. **Mode round-trip** — expand and collapse mid-question at both an unanswered and an answered question; confirm state survives both ways.

Steps 2–4 are the *only* verification the spatial layout gets, since journey testing is excluded. Treat them as required, not optional.
