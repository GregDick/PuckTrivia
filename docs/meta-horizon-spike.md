# Meta Horizon OS support — spike notes

**Status: exploratory, never run.** There is no Quest hardware on this project. Every line here was
written against [Meta's Horizon OS Android docs](https://developers.meta.com/horizon/documentation/android-apps/horizon-os-apps/)
and verified only to the extent that `assembleQuestDebug` compiles and links against the real
published SDK artifacts. Panel poses, passthrough behavior, input, and the panel lifecycle are all
unvalidated.

## What this branch does

Adds a second shipping target — Meta Horizon OS — beside the existing Android XR one, without
forking the game.

```
app/src/main/     shared: TriviaViewModel, routing, models, data, DI, all 2D screens,
                  panel *content* (TriviaPanelContent.kt, TriviaScreenContent.kt)
app/src/mobile/   phones, tablets, Android XR headsets — MainActivity + androidx.xr
app/src/quest/    Meta Horizon OS — PuckTriviaImmersiveActivity + Meta Spatial SDK
```

Build with `./gradlew assembleMobileDebug` or `./gradlew assembleQuestDebug`.

## Why product flavors and not a runtime check

The obvious first instinct is a single APK that asks `HorizonOsDetector.isOnHorizonOs()` and picks a
branch. That does not work here, because the two platforms differ in *dependencies*, not just in
code paths:

- `androidx.xr.compose` / `androidx.xr.scenecore` are **Google's Android XR**. Horizon OS does not
  ship that runtime. Its `SpatialPanel`, `Subspace`, and `LocalSpatialCapabilities` have nothing to
  bind to on a Quest.
- `com.meta.spatial:*` is Horizon-OS-only and pulls in `libossdk.oculus.so`, a native library that
  exists only on Horizon OS.

One APK containing both means every phone user downloads two spatial runtimes, one of which can
never load. So: `flavorDimensions += "platform"`, with `mobileImplementation` / `questImplementation`
splitting the two stacks. `HorizonOsDetector` is still declared on the quest flavor and used for one
narrow thing — warning in logcat when the quest APK is sideloaded somewhere it can't work.

The manifests split the same way. `main/AndroidManifest.xml` now declares **no activity at all**,
because each flavor contributes its own `LAUNCHER`; leaving one in `main` would merge into both and
give the quest build two launcher entries.

## The two spatial models, side by side

| | Android XR (mobile) | Horizon OS (quest) |
|---|---|---|
| Entry point | `ComponentActivity`, windowed by default | `AppSystemActivity` with `com.oculus.intent.category.VR`, immersive from launch |
| Windowed fallback | yes — Home Space, with an in-app toggle to Full Space | none; a VR-category activity is immersive or it isn't running |
| Panel declaration | `SpatialPanel { }` inside a `Subspace` | `registerPanels()` blueprint, then `Entity.createPanelEntity()` |
| Layout | Compose subspace layout (`SpatialRow`) | one explicit `Pose` per entity |
| Sizing | `SubspaceModifier.width(700.dp)` | `QuadShapeOptions` in **meters** + `DpDisplayOptions` for resolution |
| Show / hide | recomposition | create / destroy the entity |
| App chrome | `Orbiter` anchored to the panel group | a panel entity, `TransformParent`-ed to the question panel |
| Move | `SubspaceModifier.movable()` | `Grabbable(type = PIVOT_Y)` component on the entity |
| Resize | `SubspaceModifier.resizable()` | `IsdkPanelResize(resizeMode = Relayout)` component |
| Getting a lost panel back | system chrome always can | app's problem — see `resetPanelPoses()` |
| Background | passthrough, system-managed | `scene.enablePassthrough(true)`, ours to choose |

The sharpest practical difference is **meters vs Dp**. On Android XR a panel is sized like a tablet
and the system places it. On Horizon OS a 1.0m panel is a metre wide in the player's actual room, at
a pose you author, so sizing is a comfort decision rather than a layout one. `QuadShapeOptions` and
`DpDisplayOptions` must also agree on aspect ratio or the UI stretches onto the quad.

The second difference is that **hiding a panel is an entity lifecycle event**. A Compose branch that
renders nothing still leaves a blank rectangle floating in the room, so `PuckTriviaImmersiveActivity`
spawns and destroys the answer panel as the game enters and leaves the Question route.

## Grab and resize

Interaction SDK closes most of the gap against `.movable()` / `.resizable()`. Both are components on
the entity, added at spawn time:

```kotlin
Entity.createPanelEntity(
    R.id.question_panel,
    Transform(QUESTION_POSE),
    Grabbable(enabled = true, type = GrabbableType.PIVOT_Y,
              minHeight = 0.6f, maxHeight = 2.2f),
    IsdkPanelResize(enabled = true, resizeMode = ResizeMode.Relayout,
                    minDimensions = Vector2(0.5f, 0.35f),
                    maxDimensions = Vector2(2.5f, 1.9f),
                    preserveAspectRatio = true),
)
```

Interaction SDK is already running — registering `VRFeature` enables it — so the `meta-spatial-sdk-isdk`
artifact is only needed to put the components on the compile classpath. The edge and corner resize
handles are generated automatically; there is nothing to draw.

Choices worth stating:

- **`GrabbableType.PIVOT_Y`, not `FACE`.** A trivia panel should yaw to follow the player as it's
  dragged around them but stay upright — `FACE` would let it pitch into a tilted reading surface.
- **`ResizeMode.Relayout`, not `Simple`.** `Simple` scales the existing bitmap, which softens text.
  `Relayout` re-renders the Compose content at the new resolution.
- **`preserveAspectRatio = true`.** These panes were laid out for a fixed shape; squashing the answer
  panel to a letterbox would push its buttons out of reach of its own edge.
- **`minHeight` / `maxHeight` on the grab.** Android XR's system move policy enforces comparable
  limits for free. Here nothing does unless asked, so a panel could otherwise be dragged into the
  floor.

Two things followed from making panels movable that weren't obvious up front:

**The chrome bar had to become a child.** It was a free-standing panel above the pair. Once the
question panel could be dragged, the bar stayed behind in mid-air. It's now `TransformParent`-ed to
the question panel with a local offset, which is what the `Orbiter` does on Android XR — chrome
belongs to a panel rather than to the world. It's deliberately not grabbable itself, for the same
reason the `Orbiter` isn't independently movable.

**A reset affordance became mandatory.** An Android XR panel lives inside system chrome that can
always retrieve it. A Spatial SDK entity dragged behind the player or through a wall has no such
backstop. `resetPanelPoses()` is wired to both the chrome bar's "Bring panels to me" button and
`onRecenter`. It restores pose but deliberately not size — the player asked for that resize and
shouldn't lose it by pressing a button about position.

This also fixed a latent bug: `onRecenter` used to call `spawnPersistentPanels()`, which created a
second set of entities every recenter instead of moving the existing ones.

### Two known risks here, both needing hardware

1. **Grab may break the answer buttons.** The ISDK docs state that entities with a `Grabbable`
   component stop receiving `onClick` unless they also have `IsdkPanelDimensions`. For panels,
   `IsdkComponentCreationSystem` is documented to add that automatically — but if it doesn't, the
   answer panel becomes unclickable and the game is unplayable. **Check this first on a device.**
2. **`ComposeViewPanelRegistration` doesn't accept trailing components on 0.13.2.** The resize
   tutorial shows `IsdkPanelResize(...)` passed as a trailing argument to the registration. That
   constructor doesn't exist in the published 0.13.2 artifact (verified by disassembling the AAR) —
   it must be newer. Components go on the entity at `createPanelEntity` instead, which is equivalent
   for our purposes but means the panel can't carry them as part of its blueprint.

Still missing versus Android XR: `Grabbable` has no scale/two-handed transform enabled, there's no
`IsdkGrabConstraints` bounding where in the room panels may go, and resize doesn't persist across
launches.

## What ported for free

Everything that isn't spatial. `TriviaViewModel`, `triviaRouteFor`, the models, the repository, the
DI graph, and every 2D screen are byte-identical across both flavors. The panel *contents* moved
into `main/TriviaPanelContent.kt` and are called by both stacks unchanged — Android XR and Horizon
OS disagree about how a panel is declared and placed, but neither has an opinion about the Compose
inside it.

The flat screens (Start, Game Over, errors) also render as-is in a 700×525dp panel. That is the part
of a 2D Android app that genuinely comes across to a headset with no work.

## The one real seam: Hilt

`AppSystemActivity` extends `VrActivity` extends **`android.app.Activity`** — confirmed by
disassembling the artifact, not assumed. It is therefore not a `ComponentActivity`, which means it is
not a `ViewModelStoreOwner`, `LifecycleOwner`, or `SavedStateRegistryOwner`. That rules out
`by viewModels()`, `hiltViewModel()`, and the `@HiltViewModel` factory path wholesale, and there is
no `lifecycleScope` either.

`TriviaViewModelFactory` works around it without touching the ViewModel: an `@EntryPoint` on
`SingletonComponent` exposes the collaborators, a plain `ViewModelProvider.Factory` constructs the
ViewModel, and the activity owns a `ViewModelStore` it clears in `onSpatialShutdown` (the one
teardown callback Spatial SDK guarantees). A hand-rolled `MainScope` replaces `lifecycleScope`.

**Known loose end:** with no `SavedStateRegistryOwner` there is nothing for `SavedStateHandle` to
restore from, so an in-progress game will not survive process death on a headset the way it does on a
phone. Fixing it means wiring a `SavedStateRegistryController` onto the immersive activity by hand.

## Horizon-OS-specific manifest entries

Worth calling out because none have an Android XR counterpart:

- `<horizonos:uses-horizonos-sdk minSdkVersion targetSdkVersion>` — an OS version range *separate
  from* Android's `minSdk`. Without it, features unavailable on the user's headset fail at runtime
  with `PROVIDER_OPERATION_NOT_SUPPORTED` (1003) instead of being caught at install.
- `com.oculus.intent.category.VR` — the line that makes the app immersive rather than a flat panel.
- `android:configChanges="...uiMode..."` — **mandatory** on Horizon OS v85+. Without it an immersive
  activity exits within a second of launch, cleanly, with no crash log.
- `com.oculus.vr.focusaware` — keep rendering and receiving input while a system overlay is up.
- `com.oculus.supportedDevices` — store-side device filter.
- `<uses-native-library android:name="libossdk.oculus.so">`.

## Deliberately not done

- **Spatial Editor / GLXF scene.** No `.metaspatial` composition, no 3D environment, no skybox.
  Panels are spawned at runtime from code and float over passthrough. Adding an environment means
  adding art assets and the `spatial { }` export block.
- **Platform SDK.** No entitlement check, leaderboards, achievements, or IAP. High scores stay in
  local DataStore. An entitlement check is the first thing the Horizon Store will want.
- **2D panel-app capabilities.** `<layout android:defaultWidth/defaultHeight>` and multi-panel via
  `FLAG_ACTIVITY_LAUNCH_ADJACENT` are for *non-immersive* Horizon OS apps. They don't apply to a
  VR-category activity, so they're mentioned here rather than written as dead code.
- **Release-only dependency scoping.** `meta-spatial-sdk-castinputforward` should be
  `questDebugImplementation`, but flavor+buildType configurations aren't resolvable from the
  `dependencies` block on AGP 9. It's gated on `BuildConfig.DEBUG` at the registration site instead,
  so it ships in release builds unused.
- **Any testing.** No Quest device, and the Meta Spatial Simulator isn't set up. `assembleQuestDebug`
  compiling is the entire extent of verification.

## If this becomes real work

1. Get a device or the Meta Spatial Simulator (bundled with the Meta Horizon Android Studio plugin)
   and **confirm the answer buttons still take clicks with `Grabbable` attached** — risk 1 above is
   the only one that makes the app unplayable.
2. Find out how wrong the panel poses, grab limits, and resize bounds are.
3. Fix saved-state restoration on the immersive activity.
4. Add a Platform SDK entitlement check — table stakes for the store.
