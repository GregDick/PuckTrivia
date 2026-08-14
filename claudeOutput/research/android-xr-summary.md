# Android XR / Jetpack Compose for XR Documentation Summary

> **Version Documented**: androidx.xr.compose `1.0.0-alpha17` (Aug 12, 2026), androidx.xr.scenecore/runtime/arcore `1.0.0-beta02` (Aug 12, 2026), androidx.xr.compose.material3 `1.0.0-alpha17` (May 19, 2026)
> **Last Updated**: 2026-08-13
> **Official Documentation**: [Android XR](https://developer.android.com/develop/xr) · [Jetpack XR SDK](https://developer.android.com/develop/xr/jetpack-xr-sdk) · [XR Compose release notes](https://developer.android.com/jetpack/androidx/releases/xr-compose)
> **Status**: The entire Jetpack XR SDK (compose, scenecore, runtime, arcore, material3) is still pre-1.0 (alpha/beta) and under active, frequently-breaking development. Treat every API in this doc as subject to change.

## Overview

Android XR is Google's platform for headsets and glasses (e.g. Samsung Galaxy XR). The **Jetpack XR SDK** lets you extend an existing 2D Jetpack Compose app into spatial UI without a rewrite:

- **Jetpack Compose for XR** (`androidx.xr.compose`) — declarative spatial layout (panels, rows, columns, orbiters) built on ordinary Compose.
- **Jetpack SceneCore** (`androidx.xr.scenecore`) — lower-level 3D scene/entity API (models, anchors, spatial audio) that Compose for XR is built on top of.
- **XR Runtime** (`androidx.xr.runtime`) — session/device abstraction, math types, OpenXR bridge.
- **ARCore for Jetpack XR** (`androidx.xr.arcore`) — plane/anchor/scene-understanding tracking.
- **Material Design for XR** (`androidx.xr.compose.material3`) — Material components/layouts adapted for spatial UI.

A normal 2D Compose app already runs unmodified on an XR device as a flat panel ("Home Space"). The work described here is about *opportunistically* adding spatial panels/orbiters when running on a device that supports it, while falling back to the existing 2D UI everywhere else (phones, tablets, and non-full-space XR contexts).

## Upgrade requirements for this project

**Project's current stack**: AGP 9.0.1, Gradle 9.1.0, Kotlin 2.0.21, Compose BOM 2024.09.00, minSdk 30, compileSdk/targetSdk 36, Hilt 2.59.2, KSP, single module/single Activity.

| Requirement | Documented XR floor | This project has | Verdict |
|---|---|---|---|
| `compileSdk` | **37** (as of `xr-compose:1.0.0-alpha14`, May 19 2026 — "Updated Compose compileSdk to API 37") | 36 | **Must bump to 37.** |
| AGP | **9.2.0+** (required once compileSdk 37 is used; AGP 9.2 note: "a minimum AGP version of 9.2.0 is required when using Compose" at API 37) | 9.0.1 | **Must upgrade.** |
| Gradle | **9.4.1+** (AGP 9.2.0's own minimum/default Gradle requirement) | 9.1.0 | **Must upgrade.** |
| JDK | 17 (AGP 9.2.0 requirement) | not stated in CLAUDE.md, verify | Confirm JDK 17 toolchain. |
| Kotlin / KGP | **2.0.0+** ("projects released with Kotlin 2.0 require KGP 2.0.0 or newer") | 2.0.21 | **Meets the documented floor** — but see note below. |
| minSdk | **24** (library floor); real XR devices run **API 34+** at runtime; Play Store submission for XR requires targeting API 34+ | 30 | Fine as-is — no change required (30 > 24 floor and already ≥ 34 isn't needed at minSdk, only at target). |
| targetSdk | Recommend 34+ (Play requirement for XR-track submission); aligning to 37 with compileSdk is simplest | 36 | Bump alongside compileSdk to 37 for consistency. |
| Compose BOM | No single documented "minimum BOM" is published, but the XR sample repo (android/xr-samples) currently pins **Compose BOM `2026.08.00`** alongside `xr-compose:1.0.0-alpha17` | 2024.09.00 | **Must bump substantially** — a ~2-year-old BOM will not satisfy compileSdk 37 / Compose-compiler alignment with Kotlin 2.0.21+. Plan to move to a BOM from mid/late-2026. |
| Android Studio | **Latest Canary build only** — XR tooling (AVD "XR" form factor, Layout Inspector for subspace, etc.) is *not* in stable Android Studio as of this writing | unspecified | Install a Canary build side-by-side with your stable Studio just for XR work. |
| Kotlin (real-world reference) | The official `android/xr-samples` repo itself currently uses **Kotlin 2.4.10** and **AGP 9.3.1** | 2.0.21 / 9.0.1 | Not a hard floor, but shows the ecosystem has moved well past the documented minimums — expect friction the further behind you stay. |

**Practical upgrade path for this project**, in order:
1. Bump Gradle to 9.4.1+ (required by AGP 9.2.0).
2. Bump AGP to 9.2.0+ (9.3.x is what the official samples use).
3. Bump `compileSdk`/`targetSdk` to 37.
4. Bump Compose BOM to a 2026.x release compatible with compileSdk 37 (e.g. 2026.06.01 or later; samples use 2026.08.00).
5. Kotlin 2.0.21 clears the documented `KGP 2.0.0+` floor, so it is not a blocking upgrade, but consider moving to a newer 2.x given how far ahead the ecosystem (Kotlin 2.4.10 in samples) has moved, especially since Compose compiler is now bundled with the Kotlin Gradle plugin.
6. Add the Google Maven repository if not already present (required for all `androidx.xr.*` artifacts).
7. Install the latest **Canary** Android Studio for XR-specific tooling (emulator "XR" device type, Compose for XR previews); keep your existing stable Studio for everyday work.
8. If you enable ProGuard/R8 minification, add `compileOnly("com.android.extensions.xr:extensions-xr:1.3.0")` — using `implementation`/`api` for this artifact will break the app at runtime.

## Artifacts & versions

There is **no dedicated `androidx.xr` BOM** as of this writing — each `androidx.xr.*` artifact is versioned and pinned independently (unlike `androidx.compose:compose-bom`).

| Artifact | Purpose | Latest version | Channel |
|---|---|---|---|
| `androidx.xr.compose:compose` | Jetpack Compose for XR (Subspace, SpatialPanel, etc.) | `1.0.0-alpha17` (Aug 12, 2026) | alpha |
| `androidx.xr.compose:compose-testing` | Compose-for-XR test rules | `1.0.0-alpha17` | alpha |
| `androidx.xr.scenecore:scenecore` | 3D scene/entity API | `1.0.0-beta02` (Aug 12, 2026) | **beta** |
| `androidx.xr.scenecore:scenecore-testing` | SceneCore test rules | `1.0.0-beta02` | beta |
| `androidx.xr.scenecore:scenecore-guava` | `ListenableFuture` wrappers for Java callers | `1.0.0-beta02` | beta |
| `androidx.xr.runtime:runtime` | Session/device abstraction, math (Pose, Vector3, Matrix4) | `1.0.0-beta02` (Aug 12, 2026) | **beta** |
| `androidx.xr.runtime:runtime-testing` | `SessionTestRule`, `XrDeviceTestRule` | `1.0.0-beta02` | beta |
| `androidx.xr.arcore:arcore` | Plane/anchor/scene-understanding tracking | `1.0.0-beta02` (Aug 12, 2026) | **beta** |
| `androidx.xr.arcore:arcore-guava` / `arcore-rxjava3` / `arcore-testing` | Java/RxJava/testing helpers | `1.0.0-beta02` | beta |
| `androidx.xr.compose.material3:material3` | Material Design for XR components (`SpaceToggleButton`, etc.) | `1.0.0-alpha17` (May 19, 2026 — **older** than the alpha17 tag on `compose`, different release cadence) | alpha |
| `androidx.xr.glimmer:glimmer` | Compose toolkit for display glasses | `1.0.0-alpha16` | alpha |
| `androidx.xr.projected:projected` | Support for "augmented"/AI-glasses form factor | `1.0.0-alpha09` | alpha |
| `com.android.extensions.xr:extensions-xr` | Platform extensions needed for R8/ProGuard correctness | `1.3.0` | stable-ish (platform extension, not androidx) |

Note: `androidx.compose.material3.adaptive` (the foldable/large-screen "adaptive" library) is **unrelated** to XR — don't confuse it with `androidx.xr.compose.material3`. The official XR sample repo does list an "Adaptive Android" dependency (`1.3.0`) alongside XR artifacts, but that's the general large-screen adaptive library, used for responsive layout decisions, not an XR-specific artifact.

### Gradle coordinates (Kotlin DSL)

```kotlin
dependencies {
    // Core Compose for XR
    implementation("androidx.xr.compose:compose:1.0.0-alpha17")
    testImplementation("androidx.xr.compose:compose-testing:1.0.0-alpha17")

    // SceneCore (3D entities, models, anchors)
    implementation("androidx.xr.scenecore:scenecore:1.0.0-beta02")
    testImplementation("androidx.xr.scenecore:scenecore-testing:1.0.0-beta02")

    // XR Runtime (Session, math types)
    implementation("androidx.xr.runtime:runtime:1.0.0-beta02")
    testImplementation("androidx.xr.runtime:runtime-testing:1.0.0-beta02")

    // ARCore for Jetpack XR (scene understanding / anchors)
    implementation("androidx.xr.arcore:arcore:1.0.0-beta02")

    // Material Design for XR
    implementation("androidx.xr.compose.material3:material3:1.0.0-alpha17")

    // Required ONLY if minification/ProGuard is enabled — compileOnly, never implementation/api
    compileOnly("com.android.extensions.xr:extensions-xr:1.3.0")
}
```

Requires the Google Maven repository (`google()` in `repositories {}`), same as any other `androidx.*` artifact.

Source: [XR Compose release notes](https://developer.android.com/jetpack/androidx/releases/xr-compose), [XR SceneCore release notes](https://developer.android.com/jetpack/androidx/releases/xr-scenecore), [XR Runtime release notes](https://developer.android.com/jetpack/androidx/releases/xr-runtime), [XR Compose Material3 release notes](https://developer.android.com/jetpack/androidx/releases/xr-compose-material3), [Set up the Jetpack XR SDK](https://developer.android.com/develop/xr/jetpack-xr-sdk/set-up-sdk).

## Minimum requirements

- **minSdk**: 24 (library floor; lowered from 30 in `xr-compose:1.0.0-alpha02`).
- **compileSdk**: **37**, as of `xr-compose:1.0.0-alpha14` (May 19, 2026) — "Updated Compose compileSdk to API 37. This means a minimum AGP version of 9.2.0 is required when using Compose." Older general guidance in the getting-started docs still says "34 or higher," but the release notes for the concrete artifact you'll depend on supersede that — **37 is the real current floor**.
- **AGP**: 9.2.0+ (forced by the compileSdk 37 requirement above). AGP 9.2.0 itself requires **Gradle 9.4.1+** and **JDK 17**, and supports a maximum API level of 37.0.
- **Runtime API level on real XR devices**: Android XR devices run **API 34 (Android 14)** or higher; Google Play requires targeting API 34+ to submit an XR-track app.
- **Kotlin / KGP**: **2.0.0 or newer** ("Projects released with Kotlin 2.0 require KGP 2.0.0 or newer to be consumed" — from `xr-runtime`/`xr-scenecore` release notes). The libraries use JSpecify nullness annotations; recommended Kotlin compiler flag `-Xjspecify-annotations=strict` (this is the **default** starting with Kotlin 2.1.0).
- **Compose BOM / compose-ui**: No explicit minimum is published per-artifact, but since the Compose compiler is coupled to the Kotlin version (Kotlin 2.0+) and `xr-compose` now forces compileSdk 37, you need a Compose BOM release that supports compileSdk 37 (per Compose's own release notes, that requirement landed with "Compose 1.12.0" requiring compileSdk 37 + AGP 9). The official `android/xr-samples` repo currently pins **Compose BOM `2026.08.00`**.
- **JVM/Java target**: No XR-specific constraint beyond AGP 9.2.0's JDK 17 requirement; SceneCore's Kotlin API is suspend-function based, with `scenecore-guava` provided for Java callers who need `ListenableFuture`.

Sources: [XR Compose release notes](https://developer.android.com/jetpack/androidx/releases/xr-compose), [AGP 9.2.0 release notes](https://developer.android.com/build/releases/agp-9-2-0-release-notes), [XR Runtime release notes](https://developer.android.com/jetpack/androidx/releases/xr-runtime), [Get started with the Jetpack XR SDK](https://developer.android.com/develop/xr/jetpack-xr-sdk/getting-started).

## Multi-panel layout API

All spatial composables live in `androidx.xr.compose.subspace` / `androidx.xr.compose.subspace.layout`; platform/detection APIs live in `androidx.xr.compose.platform`.

### `Subspace { }`

Root of a spatial UI hierarchy. It is placed **inside** your normal Compose tree (typically directly inside your Activity's `setContent {}` alongside/instead of your normal 2D content), and everything spatial (`SpatialPanel`, `SpatialRow`, etc.) must be a descendant of it — calling those outside a `Subspace` throws.

```kotlin
import androidx.xr.compose.subspace.Subspace
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.width
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.movable
import androidx.xr.compose.subspace.layout.resizable

setContent {
    Subspace {
        SpatialPanel(
            SubspaceModifier
                .width(1400.dp)
                .height(824.dp)
                .movable()
                .resizable(),
        ) {
            AppContent() // ordinary 2D Compose content
        }
    }
}
```

There is also `PlanarEmbeddedSubspace { }`, which keeps 2D and spatial content together, honoring the parent's 2D layout constraints and positioning 3D content relative to that 2D-defined area (useful for embedding a small piece of spatial content inside an otherwise 2D screen rather than going fully spatial).

### `SpatialPanel`

```kotlin
@Composable
fun SpatialPanel(
    modifier: SubspaceModifier,
    shape: SpatialShape = SpatialRectangleShape,
    dragPolicy: DragPolicy = DragPolicy.Movable,
    resizePolicy: ResizePolicy = ResizePolicy.Resizable,
    interactionPolicy: InteractionPolicy = InteractionPolicy.Default,
    content: @Composable () -> Unit
)
```

- Must be a descendant of `Subspace` (or `PlanarEmbeddedSubspace`).
- `content` is **ordinary 2D Compose content** — any existing `@Composable` screen can be dropped in unchanged.
- Omitting width/height lets the panel size itself to its content.
- The exact `resizable`/`movable` modifier surface has been churning across recent alphas (see Gotchas) — `ResizePolicy`/`DragPolicy`/`MovePolicy` names and defaults changed in alpha14–alpha17.

```kotlin
@Composable
fun SpatialPanelContent() {
    Box(
        Modifier.background(Color.Black).height(500.dp).width(500.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Spatial Panel", color = Color.White, fontSize = 25.sp)
    }
}
```

### `SubspaceModifier`

`SubspaceModifier` is the spatial analogue of `Modifier`, and **uses `Dp`, not meters**, for all sizing/positioning — consistent with normal Compose so existing dimension resources/constants translate directly.

| Modifier | Signature (approx.) | Notes |
|---|---|---|
| `.width(value: Dp)` | sets panel width | Dp |
| `.height(value: Dp)` | sets panel height | Dp |
| `.depth(value: Dp)` | sets panel depth (z-axis thickness) | Dp |
| `.offset(x: Dp, y: Dp, z: Dp)` | 3D positional offset | Dp |
| `.movable(shouldScaleWithDistance: Boolean = true)` | lets the user drag/reposition the panel; scales the panel as the user moves away by default | boolean |
| `.resizable(minSize: DpVolumeSize, maxSize: DpVolumeSize)` | lets the user resize, with optional min/max constraints | `DpVolumeSize` |
| `.rotate(rotation: Rotation)` | applies a 3D rotation | `Rotation` |
| `.curveRadius(radius: Dp)` | curvature for `SpatialCurvedRow` children | Dp |

> **Note (gotcha):** across alpha15–alpha17, `resizable`/`movable` have been reworked repeatedly — `ResizePolicy` was deprecated in favor of a new `resizable` overload plus `transformingResizable`; `movable`/`transformingMovable` gained a `movePolicy` param; `MovePolicy.default()` was renamed `MovePolicy.system()`. Pin an exact alpha version and expect to revisit this modifier surface on every upgrade.

**Panel sizing/spacing guidance (room-scale layout)**: comfortable panel placement is roughly **1.5 m from the user's eyes**; maintain **56dp minimum touch targets**; use **32dp rounded corners** to match the system's recommended panel styling; default `SpatialExternalSurface` size is 400dp × 400dp if unspecified.

### `SpatialRow`, `SpatialColumn`, `SpatialBox`, `SpatialCurvedRow`, `SpatialSpacer`

```kotlin
import androidx.xr.compose.subspace.SpatialRow
import androidx.xr.compose.subspace.SpatialColumn
import androidx.xr.compose.subspace.SpatialBox
import androidx.xr.compose.subspace.SpatialCurvedRow
import androidx.xr.compose.subspace.SpatialSpacer
import androidx.xr.compose.subspace.layout.SpatialAlignment
import androidx.xr.compose.subspace.layout.SpatialArrangement

@Composable
fun SpatialRow(
    modifier: SubspaceModifier = SubspaceModifier,
    horizontalArrangement: SpatialArrangement.Horizontal = SpatialArrangement.Start,
    depthArrangement: SpatialArrangement.Depth = SpatialArrangement.Back,
    content: @Composable () -> Unit
)

@Composable
fun SpatialColumn(
    modifier: SubspaceModifier = SubspaceModifier,
    horizontalAlignment: SpatialAlignment.Horizontal = SpatialAlignment.Start,
    depthAlignment: SpatialAlignment.Depth = SpatialAlignment.Back,
    verticalArrangement: SpatialArrangement.Vertical = SpatialArrangement.Top,
    content: @Composable () -> Unit
)

@Composable
fun SpatialBox(
    modifier: SubspaceModifier = SubspaceModifier,
    alignment: SpatialAlignment = SpatialAlignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: @Composable () -> Unit
)

@Composable
fun SpatialSpacer(modifier: SubspaceModifier = SubspaceModifier)
```

Three-panel example (documented pattern for "supporting panel / main app / supporting panel"):

```kotlin
Subspace {
    SpatialRow {
        SpatialPanel(SubspaceModifier.width(384.dp).height(592.dp)) {
            StartSupportingPanelContent()
        }
        SpatialPanel(SubspaceModifier.height(824.dp).width(1400.dp)) {
            App()
        }
        SpatialPanel(SubspaceModifier.width(288.dp).height(480.dp)) {
            EndSupportingPanelContent()
        }
    }
}
```

**Curving a row of panels**: the original `SpatialRow` API was split into `SpatialRow` (flat) and **`SpatialCurvedRow`**, which arranges children along an arc using a `curveRadius` (via `SubspaceModifier.curveRadius(...)`). A larger radius gives a gentler arc; a smaller radius wraps panels more tightly around the user. **Documented guidance: use a 825dp curve radius** for a multi-panel row so panels surround the user comfortably:

```kotlin
SpatialCurvedRow(modifier = SubspaceModifier.curveRadius(825.dp)) {
    SpatialPanel(SubspaceModifier.width(400.dp).height(824.dp)) { LeftPanel() }
    SpatialPanel(SubspaceModifier.width(1400.dp).height(824.dp)) { MainPanel() }
    SpatialPanel(SubspaceModifier.width(400.dp).height(824.dp)) { RightPanel() }
}
```

### `Orbiter`

A UI element that "orbits" (anchors to an edge of) a `SpatialPanel`/`SpatialRow`/`SpatialColumn`/`SpatialBox` — used for navigation rails, toolbars, FABs, etc. so they float just outside the panel's bounds rather than overlapping content.

```kotlin
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterAnchorPoint
import androidx.xr.compose.subspace.layout.DpVolumeOffset

@Composable
fun Orbiter(
    anchorPoint: OrbiterAnchorPoint,
    offset: DpVolumeOffset = DpVolumeOffset(),
    shape: SpatialShape = SpatialRectangleShape,
    content: @Composable () -> Unit
)
```

```kotlin
Orbiter(
    anchorPoint = OrbiterAnchorPoint.Bottom,
    offset = DpVolumeOffset(y = 96.dp),
) {
    Surface(Modifier.clip(CircleShape)) {
        Row(
            Modifier.background(Color.Black).height(100.dp).width(600.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Orbiter", color = Color.White, fontSize = 50.sp)
        }
    }
}
```

Key behaviors:
- When declared inside 2D content that's wrapped in a `SpatialPanel`, the `Orbiter` anchors to that panel.
- When declared directly inside a `Subspace` (e.g., alongside a `SpatialRow`), it anchors to the nearest spatial parent.
- **The same code works in a plain 2D layout**: when not spatialized, `Orbiter` renders only its `content` and ignores anchor/offset — this is the mechanism that lets you write one navigation-rail composable that works on both phone and XR.

### `SpatialDialog`, `SpatialPopup`, `SpatialElevation`

Drop-in spatial replacements for `Dialog`/`Popup`, plus an elevation wrapper — all fall back to their normal 2D equivalents automatically when not spatialized.

```kotlin
@Composable
fun SpatialDialog(
    onDismissRequest: () -> Unit,
    properties: SpatialDialogProperties = SpatialDialogProperties(),
    content: @Composable () -> Unit
)

@Composable
fun SpatialPopup(
    alignment: Alignment = Alignment.TopStart,
    offset: IntOffset = IntOffset.Zero,
    onDismissRequest: (() -> Unit)? = null,
    elevationLevel: Dp = 0.dp,
    properties: PopupProperties = PopupProperties(),
    content: @Composable () -> Unit
)

@Composable
fun SpatialElevation(
    elevation: Dp = 0.dp,
    content: @Composable () -> Unit
)
```

- `SpatialDialog`: when spatialized, the dialog appears at the same z-depth and its parent panel is pushed back **125dp**; when not spatialized, it's a standard 2D `Dialog`.
- `SpatialPopup`: spatialized → elevated popup; not spatialized → standard 2D `Popup`.
- `SpatialElevation`: spatialized → adds z-depth elevation (see `SpatialElevationLevel.Level1`..`Level4` presets); not spatialized → renders flat, no-op.

```kotlin
// Straight drop-in replacement pattern used when migrating an existing app
// Before: Dialog(onDismissRequest = ::dismiss) { MyDialogContent() }
SpatialDialog(onDismissRequest = ::dismiss) { MyDialogContent() }

// Before: Popup(onDismissRequest = ::dismiss) { MyPopupContent() }
SpatialPopup(onDismissRequest = ::dismiss) { MyPopupContent() }

SpatialElevation(elevation = SpatialElevationLevel.Level4) {
    ComposableThatShouldElevateInXr()
}
```

Sources: [Develop spatial UI with Jetpack Compose for XR](https://developer.android.com/develop/xr/jetpack-xr-sdk/ui-compose), [Bring your Android app into 3D with XR](https://developer.android.com/develop/xr/jetpack-xr-sdk/add-xr-to-existing).

## Detecting XR / graceful degradation

The core pattern for a single binary that runs 2D on phones/tablets and spatial on XR: **check capabilities, never check device type**.

```kotlin
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.subspace.Subspace
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.width
import androidx.compose.foundation.layout.fillMaxHeight

@Composable
fun Root() {
    if (LocalSpatialCapabilities.current.isSpatialUiEnabled) {
        Subspace {
            SpatialPanel(
                modifier = SubspaceModifier.width(1488.dp).fillMaxHeight()
            ) {
                AppContent()
            }
        }
    } else {
        AppContent()
    }
}
```

### `LocalSpatialCapabilities` / `SpatialCapabilities` (Compose layer)

`androidx.xr.compose.platform.LocalSpatialCapabilities.current` returns a `SpatialCapabilities` with:

```kotlin
val caps = LocalSpatialCapabilities.current
caps.isSpatialUiEnabled          // may create spatial UI (SpatialPanel, etc.)
caps.isContent3dEnabled          // may create 3D objects
caps.isAppEnvironmentEnabled     // may set the environment/skybox
caps.isPassthroughControlEnabled // may control passthrough state
caps.isSpatialAudioEnabled       // may use spatial audio
```

Because `LocalSession` (and everything derived from it, including `LocalSpatialCapabilities`) can briefly resolve to `null`/non-XR defaults before the underlying `Session` finishes initializing (per `xr-compose:1.0.0-alpha16` release notes — "Most APIs will be affected by this"), don't assume this value is stable on first composition; it recomposes once the session becomes available.

### `SpatialCapability` (SceneCore layer)

If you're working directly against SceneCore (outside Compose), check `Session.scene.spatialCapabilities`, an `EnumSet`-like collection of `SpatialCapability`:

```kotlin
import androidx.xr.scenecore.SpatialCapability

if (xrSession.scene.spatialCapabilities.contains(SpatialCapability.PASSTHROUGH_CONTROL)) {
    xrSession.scene.spatialEnvironment.preferredPassthroughOpacity = 1f
}

xrSession.scene.addSpatialCapabilitiesChangedListener { capabilities -> /* react */ }
```

Values: `SPATIAL_3D_CONTENT`, `APP_ENVIRONMENT`, `EMBED_ACTIVITY`, `PASSTHROUGH_CONTROL`, `SPATIAL_AUDIO`, `SPATIAL_UI`.

> Note: the task prompt asked specifically about `LocalHasXrSpatialFeature`; the current official docs surfaced in this research consistently point to `LocalSpatialCapabilities.current.isSpatialUiEnabled` as *the* recommended check and do not document a separate `LocalHasXrSpatialFeature` API as of alpha17 — if you encounter that symbol in sample code it may be from an older/removed preview API. Treat `LocalSpatialCapabilities` as the source of truth.

### Home Space Mode vs Full Space Mode

- **Home Space**: the default. The app runs as one panel among others (multitasking, like a large-screen window). Spatialization (`SpatialPanel`, multiple panels, 3D content) is **not** available here — `isSpatialUiEnabled` will be false.
- **Full Space**: the app takes over, spatialization is available, `isSpatialUiEnabled` becomes true. Sub-modes exist: **Full Space Managed** (system still manages window chrome/affordances) vs **Full Space Unmanaged** (full app control).

Requesting a transition (Compose layer, via `LocalSession`):

```kotlin
import androidx.xr.compose.platform.LocalSession

val session = LocalSession.current ?: return
session.scene.requestFullSpace()
// ...
session.scene.requestHomeSpace()
```

SceneCore layer is identical: `xrSession.scene.requestFullSpace()` / `xrSession.scene.requestHomeSpace()`.

Rules: apps launch in **Home Space by default** unless the manifest says otherwise; a transition request only succeeds while the app has focus; there's also a ready-made `SpaceToggleButton()` composable in `androidx.xr.compose.material3` for a standard "switch space" UI affordance.

### `PROPERTY_XR_ACTIVITY_START_MODE`

Declared once, at `<application>` level (not per-activity in the documented examples), it controls the space the app **starts** in:

```xml
<application>
    <property
        android:name="android.window.PROPERTY_XR_ACTIVITY_START_MODE"
        android:value="XR_ACTIVITY_START_MODE_HOME_SPACE" />
    <!-- or -->
    <property
        android:name="android.window.PROPERTY_XR_ACTIVITY_START_MODE"
        android:value="XR_ACTIVITY_START_MODE_FULL_SPACE_MANAGED" />
</application>
```

Sources: [Check for spatial capabilities](https://developer.android.com/develop/xr/jetpack-xr-sdk/check-spatial-capabilities), [Transition from Home Space to Full Space](https://developer.android.com/develop/xr/jetpack-xr-sdk/transition-home-space-to-full-space), [Bring your Android app into 3D with XR](https://developer.android.com/develop/xr/jetpack-xr-sdk/add-xr-to-existing).

## Manifest / build config

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Declares the app uses the Jetpack XR SDK spatial API surface.
         Set required="false" on the mobile/Play track so the app still installs
         on non-XR devices; set required="true" only on a dedicated XR-only track. -->
    <uses-feature android:name="android.software.xr.api.spatial" android:required="false" />

    <!-- Only if you build against raw OpenXR/Unity instead of (or in addition to) Jetpack XR SDK -->
    <uses-feature android:name="android.software.xr.api.openxr" android:required="false" />

    <!-- Optional hardware capability declarations — mark required="true" only if your
         app cannot function without them -->
    <uses-feature android:name="android.hardware.xr.input.controller" android:required="false" />
    <uses-feature android:name="android.hardware.xr.input.hand_tracking" android:required="false" />
    <uses-feature android:name="android.hardware.xr.input.eye_tracking" android:required="false" />

    <!-- Only declare/request the specific dangerous XR permissions you actually use.
         For basic panel/Subspace UI with no hand/eye/scene APIs, none of these are needed. -->
    <uses-permission android:name="android.permission.HAND_TRACKING" />
    <uses-permission android:name="android.permission.SCENE_UNDERSTANDING_COARSE" />
    <uses-permission android:name="android.permission.SCENE_UNDERSTANDING_FINE" />
    <uses-permission android:name="android.permission.EYE_TRACKING_COARSE" />
    <uses-permission android:name="android.permission.EYE_TRACKING_FINE" />
    <uses-permission android:name="android.permission.FACE_TRACKING" />

    <application>
        <!-- Controls the space (Home vs Full) the app launches into -->
        <property
            android:name="android.window.PROPERTY_XR_ACTIVITY_START_MODE"
            android:value="XR_ACTIVITY_START_MODE_HOME_SPACE" />

        <!-- Optional: recommend a boundary type if the app benefits from room-scale movement -->
        <property
            android:name="android.window.PROPERTY_XR_BOUNDARY_TYPE_RECOMMENDED"
            android:value="XR_BOUNDARY_TYPE_LARGE" />

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Notes:
- **`android.software.xr.immersive` is not a real feature string** in the current docs — the correct one for Jetpack XR SDK apps is **`android.software.xr.api.spatial`**.
- For **basic panel UI** (Subspace/SpatialPanel/Orbiter/multi-panel layout with no controller/hand/eye/scene APIs), **none of the dangerous XR permissions are required** — only add `HAND_TRACKING`, `SCENE_UNDERSTANDING_*`, `EYE_TRACKING_*`, or `FACE_TRACKING` if you actually call those specific APIs. All of them are "dangerous" permissions requiring an explicit runtime request in addition to the manifest declaration.
- `enableOnBackInvokedCallback="true"` should be set on `<application>` for correct back-navigation behavior with `SpatialPanel`s (called out specifically in the Compose-for-XR panel guide).
- `android:resizeableActivity` / `configChanges` are not XR-specific concerns beyond the normal large-screen/foldable guidance that already applies to Home Space (where the app behaves like a resizable windowed app) — no new XR-only manifest attribute is documented for this.

Sources: [Understand permissions for XR](https://developer.android.com/develop/xr/permissions), [Get started building immersive experiences](https://developer.android.com/develop/xr/jetpack-xr-sdk/build-immersive), [Transition from Home Space to Full Space](https://developer.android.com/develop/xr/jetpack-xr-sdk/transition-home-space-to-full-space).

## Testing & emulator

- **Android Studio**: XR tooling (the "XR" device form factor in the Device Manager/AVD Manager, Subspace-aware Layout Inspector, etc.) requires the **latest Canary build of Android Studio**. It is explicitly *not* guaranteed to work in stable/Beta channel Studio ("Other versions might not include Android XR tools"). You can install Canary side-by-side with your existing stable Studio.
- **Creating an XR emulator (AVD)**:
  1. Tools/Device Manager → **Add a new device (+) → Create Virtual Device**.
  2. Form Factor: **XR** → choose **XR Headset** or **XR Glasses**.
  3. Pick a system image: XR Headset AVDs can use any compatible API image; **XR Glasses AVDs must use a Preview API system image**.
  4. Finish and let Android Studio download the system image.
  - Host machine requirements are non-trivial: macOS 13.3+ on Apple Silicon (M1+) with 16GB+ RAM, or Windows 11 with a fairly recent CPU/GPU (Intel 9th gen+/Ryzen 1000+, NVIDIA 10-series+/Radeon RX 5000+, 8GB+ VRAM, VMX enabled) — this is a heavier ask than a typical phone AVD.
- **Compose UI tests against spatial UI**: yes — `androidx.xr.compose:compose-testing:1.0.0-alpha17` exists specifically for this (`testImplementation`). SceneCore and Runtime ship their own testing artifacts too: `androidx.xr.scenecore:scenecore-testing`, `androidx.xr.runtime:runtime-testing` (the latter added `SessionTestRule` in beta01 and an `XrDeviceTestRule` fix landed in beta02). Exact rule/API names for `compose-testing` were not renderable from the live API reference page during this research pass (JS-rendered page); consult `androidx.xr.compose.testing` in the API reference directly, or the release-note changelog entry noting `setSubspaceContent` was removed in favor of plain Compose `setContent { Subspace { ... } }` plus these test rules.

Sources: [Install and configure Android Studio for XR development](https://developer.android.com/develop/xr/jetpack-xr-sdk/get-studio), [Create virtual XR headset and XR glasses devices](https://developer.android.com/develop/xr/jetpack-xr-sdk/run/create-avds/xr-headsets-glasses), [XR Compose release notes](https://developer.android.com/jetpack/androidx/releases/xr-compose), [XR Runtime release notes](https://developer.android.com/jetpack/androidx/releases/xr-runtime).

## Known gotchas

1. **compileSdk 37 / AGP 9.2.0 floor landed recently and is easy to miss.** `xr-compose:1.0.0-alpha14` (May 19, 2026) silently raised the required compileSdk to 37, which cascades into requiring AGP 9.2.0+, which in turn requires **Gradle 9.4.1+** and **JDK 17**. Generic "getting started" prose elsewhere on the site still says "compileSdk 34 or higher," which is stale relative to the actual current `xr-compose` artifact — trust the artifact's own release notes over the general overview pages.
2. **No `androidx.xr` BOM exists.** Every `androidx.xr.*` artifact (compose, scenecore, runtime, arcore, material3, glimmer, projected) is version-pinned independently, and — notably — they are **not all on the same version number even when they look like it** (e.g. `compose:1.0.0-alpha17` is Aug 2026, but `compose.material3:1.0.0-alpha17` is May 2026 — same alpha number, three months apart, different content). Always check each artifact's own release notes page rather than assuming a shared version.
3. **The API surface is actively churning.** Recent alphas of `xr-compose` renamed/removed APIs release-over-release: `Meter` value class removed (alpha17, replaced by `Session.scene.virtualPixelDensity`), `ResizePolicy`/`resizable`/`movable`/`MovePolicy.default()` reworked across alpha14–alpha17, `ComponentOverride` removed (alpha16), `AnchorEntity` renamed to `AnchorSpace` (alpha16), `setSubspaceContent` removed in favor of `setContent { Subspace {...} }`. Pin an exact version and budget real time for API churn on every bump. **Important nuance**: that removed `ComponentOverride` is a *core* `androidx.xr.compose` construct, distinct from the still-live `EnableXrComponentOverrides`/`XrComponentOverrideEnabler` mechanism in `androidx.xr.compose.material3` — see the [Material3-XR component override deep-dive](#material3-xr-component-overrides-deep-dive-follow-up) below for the full reconciliation.
4. **`LocalSession`/`LocalSpatialCapabilities` can resolve to null/non-XR defaults transiently** before the underlying `Session` initializes (since alpha16) — code that reads these at first composition without expecting a later recomposition can misbehave.
5. **`Session.create` is now a suspend function that must run on a worker thread** (as of `xr-runtime:1.0.0-beta01`) — calling it on the main thread is a documented breaking change/gotcha, not just a style preference.
6. **ProGuard/R8 minification requires `compileOnly("com.android.extensions.xr:extensions-xr:1.3.0")`.** Using `implementation` or `api` for this artifact is explicitly called out as breaking the app at runtime.
7. **Kotlin/KGP 2.0.0+ is required to consume these libraries**, and they rely on JSpecify nullness annotations — recommend the `-Xjspecify-annotations=strict` compiler flag (default from Kotlin 2.1.0 onward, so worth moving past 2.0.21 if you hit nullness-related friction).
8. **XR tooling is Canary-Android-Studio-only.** There is currently no stable-channel Android Studio support for the XR device form factor / emulator / XR-aware previews, so CI or teammates without Canary Studio installed won't be able to use the emulator tooling (though the Gradle build itself doesn't require Canary Studio to compile).
9. **The official sample repo runs noticeably ahead of the documented floors** (Kotlin 2.4.10, AGP 9.3.1, Compose BOM 2026.08.00 as observed in `android/xr-samples`), which is a signal that the documented minimums are a "will compile" floor, not necessarily a "well-tested by Google's own samples" floor.

Sources: [XR Compose release notes](https://developer.android.com/jetpack/androidx/releases/xr-compose), [XR SceneCore release notes](https://developer.android.com/jetpack/androidx/releases/xr-scenecore), [XR Runtime release notes](https://developer.android.com/jetpack/androidx/releases/xr-runtime), [AGP 9.2.0 release notes](https://developer.android.com/build/releases/agp-9-2-0-release-notes), [Set up the Jetpack XR SDK](https://developer.android.com/develop/xr/jetpack-xr-sdk/set-up-sdk), `android/xr-samples` `gradle/libs.versions.toml`.

## Material3-XR component overrides deep-dive (follow-up, 2026-08-13)

> Follow-up research triggered by a question about whether the Material3-XR component-override mechanism can spatialize a multi-pane layout automatically, without hand-written `Subspace`/`SpatialPanel` code. **Short answer up front: yes** — see §4 below, which is the load-bearing finding.

### 1. Does `EnableXrComponentOverrides` still exist? Exact name, artifact, signature

**Yes, it exists and is current** as of `androidx.xr.compose.material3:material3:1.0.0-alpha17` (May 19, 2026, the latest release of that artifact). It is documented today on the live ["Implement Material Design for your spatial UI"](https://developer.android.com/develop/xr/jetpack-xr-sdk/material-design) page under the heading **"Use `EnableXrComponentOverrides` to adapt your existing app"**, which states verbatim:

> "All M3 Compose UI inside of the `EnableXrComponentOverrides` wrapper will adapt on XR devices. This wrapper lets you choose any components you want to exclude from this behavior."

- **Fully-qualified name**: `androidx.xr.compose.material3.EnableXrComponentOverrides`
- **Artifact**: `androidx.xr.compose.material3:material3:1.0.0-alpha17`
- **Best-available signature** (reconstructed from indexed reference-doc content; Android's reference site is a JS-rendered app that this research's fetch tooling could not render directly, so treat the parameter *types* below as high-confidence but not verbatim-screenshotted):

```kotlin
@Composable
fun EnableXrComponentOverrides(
    enabler: XrComponentOverrideEnabler,
    content: @Composable () -> Unit
)
```

- It does take an `XrComponentOverrideEnabler` parameter, and per the page text quoted above, that parameter's role is to let you **exclude specific components** from the automatic XR adaptation (an allow/deny mechanism), not to configure new behavior. I could not retrieve a verbatim method-by-method definition of `XrComponentOverrideEnabler` (same JS-rendering limitation) — treat it as "the opt-out lambda/interface for this wrapper" and verify exact member names against the reference page or IDE autocomplete before writing code against it.
- **Minimal usage pattern** (the doc doesn't show a code sample; this is the pattern implied by the prose + artifact's own composable signature):

```kotlin
import androidx.xr.compose.material3.EnableXrComponentOverrides

setContent {
    EnableXrComponentOverrides(enabler = /* default or customized enabler */) {
        MyExistingAppRoot() // ordinary Material3 code, unchanged
    }
}
```

### 2. Reconciling the alpha16/alpha17 date conflict — is it broken?

**No evidence of breakage, and the two "ComponentOverride" mentions are two different things:**

- The bullet "**Remove `ComponentOverride` APIs**" is from the **core** `androidx.xr.compose:compose` changelog, version **alpha16** (July 15, 2026): `Remove ComponentOverride APIs ([I820c4](https://android-review.googlesource.com/#/q/I820c4aec6fad21ed544189b2dfbaeb6d6a6a6964))`. This is a lower-level, core-package construct.
- The `EnableXrComponentOverrides` / `XrComponentOverrideEnabler` pair lives in the **separate** `androidx.xr.compose.material3:material3` artifact, and its own changelog (scanned verbatim from alpha10 through alpha17) shows a multi-release build-out of the underlying per-component override plumbing — e.g. alpha10: *"Create XR implementation and `ComponentOverride` for Horizontal and Vertical Toolbar"*; alpha13: *"Create XR implementation and `ComponentOverride` for `WideNavigationRail` and `ModalWideNavigationRail`"* — but **no bullet in the material3-xr changelog announces removal, deprecation, or renaming of `EnableXrComponentOverrides` or `XrComponentOverrideEnabler`**, through its current latest release (alpha17, May 19, 2026).
- So: the core artifact removed its own internal/public `ComponentOverride` primitive in alpha16 (July 2026); the material3-xr artifact's public wrapper (`EnableXrComponentOverrides`) is a different, still-live API that was last touched (non-breaking additions only) in May 2026 — **before** that core removal happened. It is plausible the material3-xr artifact used the core `ComponentOverride` primitive internally and has since migrated off it (or never depended on the exact public symbol that got removed), but there is no published compatibility note either confirming or denying interaction between the two.
- **There is no official cross-artifact compatibility matrix** for `androidx.xr.compose` vs `androidx.xr.compose.material3` versions (consistent with the "no XR BOM" finding earlier in this doc). Given both are pre-1.0 and independently versioned, **do not assume `material3:1.0.0-alpha17` is guaranteed compatible with `compose:1.0.0-alpha17` just because the version numbers match** — that numeric match is coincidental (see gotcha #2). Recommend: pin the exact pair the team intends to ship, build a throwaway spike that actually exercises `EnableXrComponentOverrides` + a pane scaffold on that pair, and only then commit to it in the real feature branch.

### 3. What exactly gets overridden, and what does it become

Per the live ["Implement Material Design for your spatial UI"](https://developer.android.com/develop/xr/jetpack-xr-sdk/material-design) page (quoted verbatim per section), when wrapped in `EnableXrComponentOverrides`:

| Component | Becomes in XR | Verbatim doc text |
|---|---|---|
| Navigation rail (any Compose layout, incl. inside `NavigationSuiteScaffold`) | XR **Orbiter** | "Navigation rail in any Compose layout, including `NavigationSuiteScaffold` will automatically adapt to XR Orbiter." |
| Navigation bar (any Compose layout, incl. inside `NavigationSuiteScaffold`) | XR **Orbiter** | "Navigation bar in any Compose layout, including `NavigationSuiteScaffold` will automatically adapt to XR orbiter." |
| `BasicAlertDialog` | Same dialog, **gains spatial elevation** | "A `BasicAlertDialog` will adapt to XR, adding elevation to the component." |
| `TopAppBar` | XR **Orbiter** | "A `TopAppBar` will automatically adapt to XR orbiter." |
| `ListDetailPaneScaffold` | **Each pane → its own separate `SpatialPanel`** | "Compose Material 3 Adaptive Layouts in XR have a 1:1 mapping where each pane is placed inside its own XR spatial panel." |
| `SupportingPaneScaffold` | **Each pane → its own separate `SpatialPanel`** | (identical sentence, repeated verbatim under its own heading) |

`NavigationSuiteScaffold` itself isn't given a distinct spatial identity — it's the *rail/bar it contains* that becomes an Orbiter; the scaffold's own release-note history (alpha13, material3-xr) also notes it (along with the two pane scaffolds) started using `recommendedContentBoxInFullSpace` to size itself correctly inside the system's recommended content box in Full Space.

**Caveat on a conflicting claim**: an AI-generated web summary (not a verbatim page quote) asserted "`ListDetailPaneScaffold` and `SupportingPaneScaffold` currently don't support multiple spatial panels" — this phrase does **not** appear verbatim anywhere I could find on the official material-design page, the M3 XR dev-preview blog post, or the Android XR spatial-UI design guide. Given the same official page's actual verbatim text unambiguously says "each pane is placed inside its own XR spatial panel," I'm treating that AI-summary caveat as an unverified/likely-fabricated inference and not carrying it forward as fact — but flagging that I could not find independent confirmation either way beyond the doc's own plain statement, since the JS-rendered API reference pages couldn't be inspected directly for a "known limitations" callout.

### 4. THE CRUX: do pane scaffolds auto-spatialize into separate panels?

**Yes — this is directly and verbatim confirmed by official documentation, twice (once per scaffold), on the current live page.**

> "Compose Material 3 Adaptive Layouts in XR have a 1:1 mapping where each pane is placed inside its own XR spatial panel."
> — stated separately under both the "List-detail layout for XR" and "Support pane layout for XR" headings of [Implement Material Design for your spatial UI](https://developer.android.com/develop/xr/jetpack-xr-sdk/material-design)

This means a standard `ListDetailPaneScaffold` (or `SupportingPaneScaffold`) built with the **general-purpose, non-XR** `androidx.compose.material3.adaptive` library — the same one you'd use for a foldable/tablet responsive layout — gets its list pane and detail pane each rendered as a **separate spatial panel** automatically when the app is running spatialized and the relevant M3-XR override machinery is engaged. Confirmed: the plain `androidx.compose.material3.adaptive` artifact's own release notes (checked through its current version `1.3.0`, Aug 12 2026) contain **zero** mentions of XR/spatial/androidx.xr — meaning the adaptive library itself carries no XR awareness. The spatialization is provided entirely by `androidx.xr.compose.material3`, activated via `EnableXrComponentOverrides` wrapping your existing composable tree.

**Practical implication for a "question pane + answers pane" feature**: yes, you should be able to get a genuine two-panel spatial layout (question panel + answers panel, each its own movable/resizable `SpatialPanel`) by building an ordinary `ListDetailPaneScaffold` (or `SupportingPaneScaffold`) with the standard adaptive library and wrapping the composable tree in `EnableXrComponentOverrides`, **instead of** hand-writing `Subspace { SpatialRow { SpatialPanel { } SpatialPanel { } } }`. This is a real, documented, less-code path to the same visual result.

**Required artifacts/versions** (best current pairing per this research; not an officially blessed matrix — see §2 caveat about pin-testing):

```kotlin
dependencies {
    // Standard, non-XR adaptive layout library — same one used for foldables/tablets
    implementation("androidx.compose.material3.adaptive:adaptive:<latest 1.x>")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:<latest 1.x>")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:<latest 1.x>")

    // XR-aware overrides that spatialize the panes when running on an XR device
    implementation("androidx.xr.compose:compose:1.0.0-alpha17")
    implementation("androidx.xr.compose.material3:material3:1.0.0-alpha17")
}
```

**Illustrative code sample** (pattern synthesized from the documented mechanism — `EnableXrComponentOverrides` wraps the composable tree, standard `ListDetailPaneScaffold` inside it is unchanged from the non-XR version; no official copy-paste sample with this exact combination was found on the docs site, so treat this as "implied by the documented behavior, verify against the pinned versions before shipping"):

```kotlin
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.xr.compose.material3.EnableXrComponentOverrides
import androidx.xr.compose.platform.LocalSpatialCapabilities

@Composable
fun TriviaQuestionScreen() {
    val navigator = rememberListDetailPaneScaffoldNavigator<Nothing>()

    EnableXrComponentOverrides {
        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            value = navigator.scaffoldValue,
            listPane = {
                AnimatedPane { QuestionPane() }   // becomes its own SpatialPanel on XR
            },
            detailPane = {
                AnimatedPane { AnswersPane() }    // becomes its own SpatialPanel on XR
            },
        )
    }
    // On a phone (no spatial UI): renders exactly like a normal adaptive list-detail layout.
    // On an XR device in Full Space: question pane and answers pane render as two
    // separate, independently placed spatial panels — no manual Subspace/SpatialPanel needed.
}
```

Given the documented "you can choose components to exclude" behavior of `XrComponentOverrideEnabler`, and the general codebase-wide pattern of checking `LocalSpatialCapabilities.current.isSpatialUiEnabled` before assuming spatial behavior is active elsewhere in this SDK, it's still good practice to gate any XR-only visual polish (panel sizing tweaks, orbiter placement, etc.) behind that capability check even while `EnableXrComponentOverrides` handles the pane-to-panel mapping automatically.

### 5. `SpaceToggleButton` — corrected name, signature, status

**Correction to this doc's earlier line 431**: the current, live documentation names this composable **`SpaceToggleButton`**, not `SpaceModeToggleButton`. Both names are genuinely attested in Google's own material, which is worth knowing about (naming drift), but only one is current:

- The [material3-xr release notes](https://developer.android.com/jetpack/androidx/releases/xr-compose-material3), alpha12 (Oct 22, 2025): *"Added `SpaceModeToggleButton` for switching between `HomeSpace` and `FullSpace`"* — this is the **original name at introduction**.
- The current, live [Transition from Home Space to Full Space](https://developer.android.com/develop/xr/jetpack-xr-sdk/transition-home-space-to-full-space) guide instead links to and names it **`SpaceToggleButton`**, verbatim: *"To transition between Home Space and Full Space use the `SpaceToggleButton` composable from the Material Design for XR library. This is a composable button that adapts to the current spatial mode and toggles between Full Space and Home Space."* The reference-doc URL fragment it links to is `androidx/xr/compose/material3/SpaceToggleButton.composable#SpaceToggleButton(androidx.compose.ui.Modifier,androidx.compose.material3.IconToggleButtonColors,kotlin.Function1)`, confirming this is the real, current symbol name.
- **Fully-qualified name**: `androidx.xr.compose.material3.SpaceToggleButton`
- **Artifact**: `androidx.xr.compose.material3:material3:1.0.0-alpha17`
- **Signature** (parameter *count and first two types* confirmed directly from the reference-doc URL fragment; the third parameter is a single-argument function type whose exact shape I could not render from the JS-based reference page):

```kotlin
@Composable
fun SpaceToggleButton(
    modifier: Modifier = Modifier,
    colors: IconToggleButtonColors = /* default */,
    /* one Function1-shaped lambda parameter — exact signature unconfirmed, likely an icon-content slot or click callback */
)
```

- **Does it handle both directions on its own?** Yes — per the doc's own description, it "adapts to the current spatial mode and toggles between Full Space and Home Space," i.e. it's self-contained: you don't pass it a current-mode boolean or wire up `requestFullSpace()`/`requestHomeSpace()` yourself, it does both directions internally.
- **Does it hide itself on non-XR devices, or must the caller gate it?** **Not documented either way** on the pages I could retrieve — I could not find an explicit statement confirming automatic self-hiding for this specific composable. Given the SDK's consistent pattern elsewhere (Orbiter, SpatialDialog, SpatialPopup all explicitly documented as self-adapting/no-op on non-spatial), it's plausible `SpaceToggleButton` follows the same convention, but since I couldn't verify that with a direct quote for this specific composable, **the safe recommendation is to gate its visibility yourself** with `LocalSpatialCapabilities.current.isSpatialUiEnabled` (or check whether Full Space is even a meaningful destination on the current device) until you've confirmed the self-hiding behavior empirically against the pinned version.

### 6. minSdk for XR reality — what does `xr-samples` actually use, and what's sane

- Fetched directly from `android/xr-samples`' `app/build.gradle.kts` (`HelloAndroidXR` sample):

```kotlin
defaultConfig {
    applicationId = "com.example.helloandroidxr"
    minSdk = 24
    targetSdk = 37
    // compileSdk = 37 (set at the android {} block level)
}
```

  So the official sample uses **minSdk 24** — exactly the documented library floor, no higher. It also uses a Java 17 toolchain (`JavaLanguageVersion.of(17)`), consistent with AGP 9.2+/9.3+'s JDK 17 requirement.

- **What's actually sane for a phone+XR app**: minSdk **24 is not itself a meaningful constraint** for XR support — real Android XR devices (Galaxy XR etc.) run API 34+ regardless of what minSdk your app declares; minSdk only controls the lower bound of *phone* installability, and is otherwise unrelated to whether spatial features are available at runtime (that's gated by `LocalSpatialCapabilities`/`SpatialCapability`, not by API level checks). So: **pick minSdk based on your phone-side audience/feature needs exactly as you would for a non-XR app** — there is no reason to lower an existing app's minSdk to chase XR compatibility, and no reason a higher minSdk (like this project's current **30**) creates any XR problem. This project's minSdk 30 requires **no change** for XR adoption; the earlier "Upgrade requirements" table in this doc already reflects that correctly.

### Sources (follow-up)

- [Implement Material Design for your spatial UI](https://developer.android.com/develop/xr/jetpack-xr-sdk/material-design)
- [Transition from Home Space to Full Space](https://developer.android.com/develop/xr/jetpack-xr-sdk/transition-home-space-to-full-space)
- [XR Compose Material3 release notes](https://developer.android.com/jetpack/androidx/releases/xr-compose-material3)
- [XR Compose release notes](https://developer.android.com/jetpack/androidx/releases/xr-compose)
- [Compose Material 3 Adaptive release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive)
- [Android XR spatial UI design guide](https://developer.android.com/design/ui/xr/guides/spatial-ui)
- [`android/xr-samples` — `HelloAndroidXRApp.kt`](https://github.com/android/xr-samples/blob/main/app/src/main/java/com/example/helloandroidxr/ui/HelloAndroidXRApp.kt) and `app/build.gradle.kts`

## Home Space ↔ Full Space transition mechanics deep-dive (follow-up, 2026-08-13)

> Follow-up research triggered by the question: how much must an app actually *do* to "support" Home Space ↔ Full Space transitions, versus what the platform handles for free? **Bottom line up front:** the system already gives the user a way to toggle spaces without the app doing anything (a window-chrome button), and the transition itself is **not** documented anywhere as an Activity recreation / configuration change — the one real, officially-documented risk is Compose state placed *inside* a `Subspace {}` block, which is unmounted/remounted across the transition. Everything below is marked **CONFIRMED** (a direct quote or unambiguous statement from an official source) or **INFERRED** (my reasoned conclusion from confirmed facts, not a direct doc statement) so you can weight it accordingly.

### 1. What triggers a transition — every path

| Path | Status | Detail |
|---|---|---|
| **System window-chrome control** | **CONFIRMED** (Google's own end-user documentation, not developer.android.com) | Google's [Android XR Help — "Spaces & multitasking"](https://support.google.com/android-xr/answer/16638859?hl=en) instructs users: *"If applicable, to switch to Full Space, find and select the **expand window button**. If applicable, to exit Full Space, find and select the **compact window button**."* This is a system-drawn control, not something the app renders — it is the spatial-computing equivalent of the desktop-windowing/freeform-multitasking maximize button (see corroborating context below). |
| **Hardware button / gesture** | **PARTIALLY CONFIRMED, but for a different concept** | The Help Center's [device navigation article](https://support.google.com/android-xr/answer/16639048) documents "double tap the button or touchpad on your headset to switch between home and **virtual environment**" — but "environment" (skybox/passthrough) is a documented, distinct concept from "space" (Home Space vs. Full Space multitasking mode). I could not find an official statement of a dedicated hardware button/gesture specifically for the Home↔Full **space** toggle — only the window-chrome buttons above. Do not conflate the two. |
| **System settings** | **NOT FOUND** | No official mention of a system Settings toggle for space mode. |
| **App-initiated** | **CONFIRMED** | `session.scene.requestFullSpace()` / `session.scene.requestHomeSpace()` (Compose, via `LocalSession`, or SceneCore directly via `xrSession.scene`). Renamed from `requestFullSpaceMode()`/`requestHomeSpaceMode()` in `xr-scenecore:1.0.0-alpha07` (Sept 24, 2025) — "Other methods and documentation referring to 'Home Space Mode' and 'Full Space Mode' have been similarly updated." Source-level doc comment (from the AOSP mirror of `Scene.kt`, pre-rename revision, semantics unchanged post-rename): *"If the Activity has focus, causes the Activity to be placed in Full Space Mode... Otherwise, this call does nothing."* — note it says the **Activity** is placed in the mode, not that a new one is created. |
| **Pre-built UI affordance** | **CONFIRMED** | `SpaceToggleButton` (`androidx.xr.compose.material3.SpaceToggleButton`, artifact `androidx.xr.compose.material3:material3:1.0.0-alpha17`) — per the live [Transition guide](https://developer.android.com/develop/xr/jetpack-xr-sdk/transition-home-space-to-full-space): *"a composable button that adapts to the current spatial mode and toggles between Full Space and Home Space."* Self-contained — you don't wire up `requestFullSpace()`/`requestHomeSpace()` yourself if you use it. (Same button was introduced under the name `SpaceModeToggleButton` in material3-xr alpha12 — see naming-drift note in the Material3 deep-dive above.) |
| **Manifest start mode** | **CONFIRMED, but not a runtime "trigger"** | `PROPERTY_XR_ACTIVITY_START_MODE` only controls the space the Activity **launches into**; it is not something that fires again later. Declaring it does not stop the user or the app from transitioning afterward. |

**Does an app need to build its own toggle at all? Answer: No, not strictly.** The system-drawn expand/compact window-chrome buttons (confirmed above) are provided without any app code. The Help Center's "if applicable" phrasing is the one hedge I could not fully resolve — I found no official statement guaranteeing this control is *always* present for *every* app in *every* situation — but I also found nothing suggesting an app can suppress it. Given `SpaceToggleButton` is a single pre-built composable (not custom logic), the practical, low-risk recommendation is: **rely on the system control as the baseline** (it's there for free), and optionally drop in `SpaceToggleButton` for an in-content affordance, since it costs one line and Google's own samples/guides present it as the idiomatic pattern — not because the system control is documented as insufficient.

### 2. Does a transition cause Activity recreation or a configuration change? (the crux)

**No official page states this explicitly, in either direction** — I checked the Transition guide, "Get started building immersive experiences," "Bring your Android app into 3D with XR," both XR Fundamentals codelabs, "Check for spatial capabilities," "Add a subspace to your app," the Foundations and Spatial UI design guides, and the full `xr-compose`/`xr-scenecore`/`xr-runtime` release-note changelogs, and the DP4 blog post. None of them mention `onConfigurationChanged`, activity recreation, or config qualifiers in connection with this transition.

**INFERRED conclusion (high confidence, built from several confirmed facts): the transition is not an Activity-level configuration change. The same Activity instance persists.** Evidence:

1. The `requestFullSpace()`/`requestHomeSpace()` doc comment describes the **Activity being placed into** a mode, not recreated.
2. **CONFIRMED**, [Check for spatial capabilities](https://developer.android.com/develop/xr/jetpack-xr-sdk/check-spatial-capabilities): *"Since `LocalSpatialCapabilities` is a Composition Local, Compose will automatically recompose whenever spatial capabilities change... No manual listener setup is required."* An ordinary Activity-recreation path would make this statement pointless — you'd just re-read the value fresh in a new `onCreate`.
3. **CONFIRMED**, [XR Fundamentals Part 2 codelab](https://developer.android.com/codelabs/xr-fundamentals-part-2): the codelab explicitly instructs readers to *"set up a variable to track the current environment option (outside of the `Subspace` so that the state is maintained when switching between Home Space and Full Space)"* using plain `remember { mutableStateOf(0) }`. Plain `remember` state (not `rememberSaveable`, not a `ViewModel`) does **not** survive real Activity recreation — so the only way this guidance makes sense is if the transition leaves the Activity/Composition alive and only tears down the `Subspace` subtree specifically (see below).
4. SceneCore exposes a dedicated `Scene.setSpatialModeChangedListener(Executor, Consumer<SpatialModeChangeEvent>)` — a push-style listener for reacting to the mode change live within a running session, which would be redundant if the Activity were simply always destroyed and re-created on every transition.

**What is confirmed to happen at the Compose layer: the `Subspace {}` subtree is mounted/unmounted, not the whole Activity.** [Learn Android XR Fundamentals: Part 1](https://developer.android.com/codelabs/xr-fundamentals-part-1) states verbatim: *"When running on a non-XR device, the contents of the `Subspace` composable never enter the Composition. When running on an XR device, the contents only enter the Composition when the app is running in Full Space."* [Add a subspace to your app](https://developer.android.com/develop/xr/jetpack-xr-sdk/add-subspace) corroborates: *"A subspace is rendered only when spatialization is enabled. In Home Space or on non-XR devices, any code within that subspace is ignored."* So: Full→Home disposes everything inside `Subspace`; Home→Full composes it fresh. That is a Compose-level conditional-composition event, not an OS `Configuration` change — no config qualifiers (screen size, density, orientation, etc.) are documented as changing as a result of this specific transition.

**Distinct, unrelated mechanism you may be thinking of:** resizing/maximizing the app's *window while still in Home Space* (ordinary desktop-windowing/freeform multi-window behavior, not the Home↔Full toggle) genuinely does change `screenWidthDp`/`screenHeightDp` config qualifiers and, per the general (non-XR) [Support desktop windowing](https://developer.android.com/develop/ui/compose/layouts/adaptive/support-desktop-windowing) guide, every app in that windowing model gets a system-drawn header bar automatically: *"All apps running in desktop windowing have a header bar, even in immersive mode,"* with *"the system continu[ing] to draw its own interactive caption elements, such as the close and maximize buttons, on top of your app content."* This is a different (and real) Android configuration-change path, but it is **not** the Home Space ↔ Full Space transition itself — don't conflate ordinary window-resize handling with space-mode handling.

### 3. What must the app declare?

- **`<uses-feature android:name="android.software.xr.api.spatial" android:required="false"/>`** — CONFIRMED, standard, already in this project's plan.
- **`PROPERTY_XR_ACTIVITY_START_MODE`** — CONFIRMED optional; only affects initial launch space; can be declared at `<application>` or `<activity>` level (activity-level scopes it to that activity).
- **`android:configChanges`** — **not documented anywhere as required or recommended** for this transition specifically. Consistent with the "not a config change" inference in §2. (It remains relevant for the *unrelated* desktop-windowing resize case above, but that's not this feature.)
- **`android:resizeableActivity`** — **not mentioned in any XR doc page found**, and not declared in the official sample.
- **What the official sample actually declares** — the *entire* `android/xr-samples` repo is a single sample, "HelloAndroidXR." Its manifest (`app/src/main/AndroidManifest.xml`, cross-checked verbatim via two independent mirrors — GitHub's `blob` view and the jsdelivr CDN — to rule out a fetch-tool transcription error), in full:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <!-- Indicates that the app makes use of XR spatial APIs, but this isn't a requirement that
    should block installing the app -->
    <uses-feature android:name="android.software.xr.api.spatial" android:required="false" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.HelloAndroidXR"
        tools:targetApi="31">
        <property
            android:name="android.window.PROPERTY_XR_ACTIVITY_START_MODE"
            android:value="XR_ACTIVITY_START_MODE_HOME_SPACE_MANAGED" />
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.HelloAndroidXR">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

  **Notable discrepancy, flagged rather than resolved:** the sample declares the value `XR_ACTIVITY_START_MODE_HOME_SPACE_MANAGED`. Every developer.android.com prose page I could fetch (the Transition guide, "Get started building immersive experiences," the XR Fundamentals codelab) instead documents the value as plain **`XR_ACTIVITY_START_MODE_HOME_SPACE`** (no `_MANAGED` suffix) alongside `XR_ACTIVITY_START_MODE_FULL_SPACE_MANAGED`/`_UNMANAGED`. I could not find `XR_ACTIVITY_START_MODE_HOME_SPACE_MANAGED` documented anywhere, could not load the platform `android.window.WindowProperties` reference page (404) or the `androidx.xr.runtime.manifest` reference page (JS-rendered, didn't return content) to arbitrate, and could not find it in AOSP source search either. Given this project's own docs elsewhere note the sample repo consistently runs ahead of/differently from documented floors, treat this as **a real, unresolved discrepancy** — verify the exact constant name against the IDE's autocomplete/current SDK stubs before shipping a manifest declaration, rather than trusting either source blindly.
- **No manifest attribute of any kind is required purely to "support" the transition itself** — nothing here is XR-transition-specific configuration; it's either the standard XR feature declaration or the optional start-mode hint.

### 4. How does Compose observe the change?

- **`LocalSpatialCapabilities.current`** — **CONFIRMED** automatic: it's an ordinary `CompositionLocal`, and per the docs, Compose recomposes automatically when the underlying capabilities change; "no manual listener setup is required." This is what the project's existing `isSpatialUiEnabled` branch already relies on — it needs no new code to react to a transition.
- **`LocalSpatialConfiguration.current`** — a second, related `CompositionLocal` (exposes `hasXrSpatialFeature`, used to decide whether to show a space-toggle affordance at all); same auto-recomposition model applies since it's also a `CompositionLocal` sourced from the same `Session`.
- **Caveat already in this doc (still applies):** both of the above can transiently resolve to `null`/non-XR defaults before the `Session` finishes initializing (confirmed via `xr-compose:1.0.0-alpha16` changelog: *"Once the `Session` is available, recomposition will be triggered with the correct state"*) — relevant on cold start, not specific to the Home↔Full toggle itself.
- **SceneCore-level listeners (for code outside Compose, or lower-level reactions):**
  - `Scene.addSpatialCapabilitiesChangedListener(callbackExecutor: Executor, listener: Consumer<Set<SpatialCapability>>)` — CONFIRMED via source (AOSP mirror of `Scene.kt`) and the official [Check for spatial capabilities](https://developer.android.com/develop/xr/jetpack-xr-sdk/check-spatial-capabilities) page, which also shows a simpler single-lambda call form in sample code (`addSpatialCapabilitiesChangedListener { capabilities -> ... }`), implying a convenience overload defaulting the executor.
  - `Scene.setSpatialModeChangedListener(callbackExecutor: Executor, listener: Consumer<SpatialModeChangeEvent>)` — CONFIRMED via source and via the `xr-scenecore` changelog (introduced under this name in alpha07, replacing an earlier `SpatialModeChangeListener` property). Doc comment: "Sets the listener to be invoked when the spatial mode for the scene has changed... Only one listener can be active at a time." This is the specific, dedicated callback for space-mode transitions (as distinct from the broader capabilities-changed listener). I could not retrieve the exact member fields of `SpatialModeChangeEvent` itself (JS-rendered reference page) — verify its shape directly against the SDK before depending on specific fields.
  - **No Flow-based API was found or confirmed** at either layer — only `Consumer`/listener callbacks (Java-interop-friendly) at the SceneCore layer, and `CompositionLocal` reactivity at the Compose layer. If you want a `Flow`, you'd need to wrap one of these listeners yourself (`callbackFlow { ... }`); that wrapper is not provided by the SDK.

### 5. `PROPERTY_XR_ACTIVITY_START_MODE` — per-value semantics

| Value | Status | Meaning |
|---|---|---|
| `XR_ACTIVITY_START_MODE_HOME_SPACE` | CONFIRMED | *"Use this start mode to launch your app in Home Space. In Home Space, multiple apps can run side-by-side, so users can multitask. Any mobile or large screen Android app can operate in Home Space, as well as XR apps built using the Jetpack XR SDK."* |
| `XR_ACTIVITY_START_MODE_FULL_SPACE_MANAGED` | CONFIRMED | *"Use this start mode to launch your app in Full Space. In Full Space, only one app runs at a time, with no space boundaries, and all other apps are hidden."* "Managed" = the **system** still manages window/session chrome and affordances for you (as opposed to unmanaged, below). Docs recommend it "only if it is unlikely that users would like to use another app simultaneously." |
| `XR_ACTIVITY_START_MODE_FULL_SPACE_UNMANAGED` | CONFIRMED | For **OpenXR**-based apps, not Jetpack XR SDK Compose/SceneCore apps: *"Apps built with OpenXR launch in Full Space and must use `XR_ACTIVITY_START_MODE_FULL_SPACE_UNMANAGED` start mode. Unmanaged Full Space signals to Android XR the app uses OpenXR."* Not relevant to a Compose-for-XR app unless it also embeds an OpenXR/game-engine surface. |
| `XR_ACTIVITY_START_MODE_UNDEFINED` | **NOT FOUND** | I could not locate this value on any developer.android.com prose page, any `xr-compose`/`xr-scenecore`/`xr-runtime` release-note changelog, the XR Fundamentals codelabs, or the official sample. The platform reference page (`android.window.WindowProperties`) 404'd and the `androidx.xr.runtime.manifest` API reference page is JS-rendered and didn't yield content through available tooling. Treat its existence/behavior as **unconfirmed** — plausible as a platform-side sentinel/default enum value, but I have no documented evidence either way. |

- **Scope:** CONFIRMED — declarable at either `<activity>` or `<application>` level; activity-level scopes the preference to that activity.
- **Normal-phone install behavior (`FULL_SPACE_MANAGED` on a non-XR phone):** **NOT explicitly documented either way** — I found no compatibility statement addressing this directly, despite specifically searching the "5 things you need to know about publishing... for Android XR" blog post and the immersive-experiences guide. **INFERRED** (from ordinary Android `<property>` manifest-tag semantics — a `<property>` element is inert metadata unless a specific platform component calls `PackageManager.getProperty()` for that exact name, and a normal phone's window manager has no XR "start mode" concept to consult): declaring it and installing on a normal phone should be a silent no-op — the app just launches as an ordinary 2D activity — not an install failure or crash. I'd verify this empirically before relying on it, since it's inference, not a doc guarantee.
- **Is declaring the property alone sufficient to "launch spatial," or is more required?** **CONFIRMED more is required.** The start-mode property only decides which space the (otherwise-ordinary) Activity opens into — it does not create any spatial content by itself. Per confirmed facts elsewhere in this doc, `Subspace{}` content simply isn't composed unless the app is actually in Full Space, and an app with no `Subspace`/`SpatialPanel` content (or no `EnableXrComponentOverrides`-wrapped adaptive panes) will just present its ordinary single 2D panel — now alone in an otherwise-empty Full Space, which is unlikely to be the intended effect. **INFERRED** consequence: don't set `FULL_SPACE_MANAGED` as a way to "turn on" spatial UI — it only chooses the space the app starts in; you still need real `Subspace` content (or Material3-XR overrides) for that space to be visually worth entering.

### 6. Known state-loss / lifecycle pitfalls

- **CONFIRMED, and the one concrete action item:** anything held only in `remember{}` **inside** a `Subspace {}` block is disposed every time the app leaves Full Space and freshly re-created every time it re-enters — because that subtree is documented as not being part of the Composition at all outside Full Space. Officially documented mitigation: hoist state above/outside the `Subspace` call (the codelab's own example: `var currentEnvironmentOptionIndex by remember { mutableStateOf(0) }` declared *outside* `Subspace { ... }`).
- **CONFIRMED, historical, now fixed:** `xr-scenecore:1.0.0-alpha10` (Dec 3, 2025) changelog: *"Fixed a potential crash that can occur when Session is destroyed and a `SpatialModeChangeEvent` is received"* and, same release, *"Fixed a bug which could cause an `IllegalStateException` to be thrown when leaving or re-entering an Activity."* These confirm real races existed between Session teardown and in-flight space-mode-change events, and between Activity backgrounding/foregrounding and SceneCore state — both are reported fixed by alpha10, well before the current beta02, but they're useful evidence that Session/Activity boundary races in this area are a known class of bug, not a hypothetical.
- **CONFIRMED (already in this doc, still relevant here):** `LocalSession` and everything derived from it can transiently be `null`/non-XR-default before the `Session` finishes initializing (alpha16) — a cold-start/process-restart risk that compounds if code reads space-mode state too eagerly, though it is not itself caused by the Home↔Full toggle.
- **No official page documents Compose-state loss, Activity destruction, or session invalidation as a general consequence of the Home↔Full toggle itself** — the only officially documented state-loss mechanism is the `Subspace` mount/unmount behavior above.

### Decision-ready takeaway for this project

Given the app already keeps its real state in a process-retained `ViewModel` (config-change-safe) with a `SavedStateHandle` snapshot (process-death-safe), and already branches on `LocalSpatialCapabilities.current.isSpatialUiEnabled`:

1. **You do not need to build a space-toggle control.** The system provides one for free (the window-chrome expand/compact button, confirmed via Google's own Android XR Help docs). Adding `SpaceToggleButton` from `androidx.xr.compose.material3` is a same-day, one-line nicety, not a requirement.
2. **"Handling the transition" is, on current evidence, free at the Activity/ViewModel level** — nothing found in official docs indicates the transition recreates the Activity, delivers `onConfigurationChanged`, or invalidates your `ViewModel`/`SavedStateHandle`. `LocalSpatialCapabilities` will just recompose your existing `if (isSpatialUiEnabled)` branch automatically.
3. **The one real piece of work:** audit for any `remember { ... }` state declared directly inside a `Subspace { ... }` block (e.g., a spatial-only panel's scroll position or transient UI toggle). Anything living only there will reset on every Home↔Full round-trip and must be hoisted to the ViewModel (or to a `remember`/`rememberSaveable` outside the `Subspace` call) if it needs to survive.
4. **Manifest-wise, there is nothing to add specifically for the transition** — no `configChanges`, no `resizeableActivity`. The only optional manifest touch is `PROPERTY_XR_ACTIVITY_START_MODE` if you want to control the *initial* launch space, and it's inert on a normal phone.

### Sources (transition deep-dive)

- [Transition from Home Space to Full Space](https://developer.android.com/develop/xr/jetpack-xr-sdk/transition-home-space-to-full-space)
- [Check for spatial capabilities](https://developer.android.com/develop/xr/jetpack-xr-sdk/check-spatial-capabilities)
- [Add a subspace to your app](https://developer.android.com/develop/xr/jetpack-xr-sdk/add-subspace)
- [Get started building immersive experiences](https://developer.android.com/develop/xr/jetpack-xr-sdk/build-immersive)
- [Get started with OpenXR for Android XR](https://developer.android.com/develop/xr/openxr/get-started)
- [Bring your Android app into 3D with XR](https://developer.android.com/develop/xr/jetpack-xr-sdk/add-xr-to-existing)
- [Learn Android XR Fundamentals: Part 1 — Modes and Spatial Panels](https://developer.android.com/codelabs/xr-fundamentals-part-1)
- [Learn Android XR Fundamentals: Part 2 — Orbiters and Spatial Environments](https://developer.android.com/codelabs/xr-fundamentals-part-2)
- [Foundations (Android XR design guide)](https://developer.android.com/design/ui/xr/guides/foundations)
- [Spatial UI (Android XR design guide)](https://developer.android.com/design/ui/xr/guides/spatial-ui)
- [Support desktop windowing](https://developer.android.com/develop/ui/compose/layouts/adaptive/support-desktop-windowing)
- [Spaces & multitasking on Android XR (Google Help Center, end-user docs)](https://support.google.com/android-xr/answer/16638859?hl=en)
- [Learn how to navigate on your Android XR device (Google Help Center)](https://support.google.com/android-xr/answer/16639048)
- [XR SceneCore release notes](https://developer.android.com/jetpack/androidx/releases/xr-scenecore)
- [XR Compose release notes](https://developer.android.com/jetpack/androidx/releases/xr-compose)
- [XR Runtime release notes](https://developer.android.com/jetpack/androidx/releases/xr-runtime)
- [`Scene.kt` source (AOSP mirror, `platform/frameworks/support`)](https://android.googlesource.com/platform/frameworks/support/+/3509edb358ab2e073ced187c3b3823a09834a6ca/xr/scenecore/scenecore/src/main/java/androidx/xr/scenecore/Scene.kt)
- [`android/xr-samples` — `AndroidManifest.xml`](https://github.com/android/xr-samples/blob/main/app/src/main/AndroidManifest.xml)

## Sources

- [Android XR (overview)](https://developer.android.com/develop/xr)
- [Develop with the Jetpack XR SDK](https://developer.android.com/develop/xr/jetpack-xr-sdk)
- [Get started with the Jetpack XR SDK](https://developer.android.com/develop/xr/jetpack-xr-sdk/getting-started)
- [Set up the Jetpack XR SDK](https://developer.android.com/develop/xr/jetpack-xr-sdk/set-up-sdk)
- [Create an Android XR project](https://developer.android.com/develop/xr/jetpack-xr-sdk/create-project)
- [Bring your Android app into 3D with XR](https://developer.android.com/develop/xr/jetpack-xr-sdk/add-xr-to-existing)
- [Develop spatial UI with Jetpack Compose for XR](https://developer.android.com/develop/xr/jetpack-xr-sdk/ui-compose)
- [Check for spatial capabilities](https://developer.android.com/develop/xr/jetpack-xr-sdk/check-spatial-capabilities)
- [Transition from Home Space to Full Space](https://developer.android.com/develop/xr/jetpack-xr-sdk/transition-home-space-to-full-space)
- [Get started building immersive experiences](https://developer.android.com/develop/xr/jetpack-xr-sdk/build-immersive)
- [Understand permissions for XR](https://developer.android.com/develop/xr/permissions)
- [Install and configure Android Studio for XR development](https://developer.android.com/develop/xr/jetpack-xr-sdk/get-studio)
- [Create virtual XR headset and XR glasses devices](https://developer.android.com/develop/xr/jetpack-xr-sdk/run/create-avds/xr-headsets-glasses)
- [XR Compose release notes](https://developer.android.com/jetpack/androidx/releases/xr-compose)
- [XR SceneCore release notes](https://developer.android.com/jetpack/androidx/releases/xr-scenecore)
- [XR Runtime release notes](https://developer.android.com/jetpack/androidx/releases/xr-runtime)
- [ARCore for Jetpack XR release notes](https://developer.android.com/jetpack/androidx/releases/xr-arcore)
- [XR Compose Material3 release notes](https://developer.android.com/jetpack/androidx/releases/xr-compose-material3)
- [Android Gradle plugin 9.2.0 release notes](https://developer.android.com/build/releases/agp-9-2-0-release-notes)
- [android/xr-samples (GitHub)](https://github.com/android/xr-samples)
- [Implement Material Design for your spatial UI](https://developer.android.com/develop/xr/jetpack-xr-sdk/material-design)
- [Compose Material 3 Adaptive release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive)
- [Android XR spatial UI design guide](https://developer.android.com/design/ui/xr/guides/spatial-ui)
- [Check for spatial capabilities](https://developer.android.com/develop/xr/jetpack-xr-sdk/check-spatial-capabilities)
- [Add a subspace to your app](https://developer.android.com/develop/xr/jetpack-xr-sdk/add-subspace)
- [Get started with OpenXR for Android XR](https://developer.android.com/develop/xr/openxr/get-started)
- [Learn Android XR Fundamentals: Part 1 — Modes and Spatial Panels](https://developer.android.com/codelabs/xr-fundamentals-part-1)
- [Learn Android XR Fundamentals: Part 2 — Orbiters and Spatial Environments](https://developer.android.com/codelabs/xr-fundamentals-part-2)
- [Foundations (Android XR design guide)](https://developer.android.com/design/ui/xr/guides/foundations)
- [Support desktop windowing](https://developer.android.com/develop/ui/compose/layouts/adaptive/support-desktop-windowing)
- [Spaces & multitasking on Android XR (Google Help Center)](https://support.google.com/android-xr/answer/16638859?hl=en)
- [Learn how to navigate on your Android XR device (Google Help Center)](https://support.google.com/android-xr/answer/16639048)
- [`Scene.kt` source (AOSP mirror)](https://android.googlesource.com/platform/frameworks/support/+/3509edb358ab2e073ced187c3b3823a09834a6ca/xr/scenecore/scenecore/src/main/java/androidx/xr/scenecore/Scene.kt)
