# Feature: Preserve Score on Configuration Change

## Feature Overview

Migrate the trivia game's state management from Compose `remember` to an Android `ViewModel`, and introduce Hilt as the dependency injection framework. The primary user-facing goal is that the player's score (and all game state) survives configuration changes such as screen rotation, which currently resets everything to zero. Hilt is introduced to manage the creation and wiring of the ViewModel and its dependencies, establishing an architectural foundation for the app going forward.

**Definition of Done:** The player's score, current round, used-player history, and selection state all survive a screen rotation without resetting. Hilt is fully integrated and is responsible for providing the ViewModel. The app compiles, runs, and behaves identically to the current version except that configuration changes no longer destroy game state.

---

## Story 1: Add Hilt to the Project

**As a** developer,
**I want** Hilt configured in the build system with an `@HiltAndroidApp` application class,
**So that** I have a working DI graph to wire dependencies throughout the app.

**Story Points:** 2
**Priority:** P0
**Dependencies:** None

### Acceptance Criteria

- [ ] Hilt dependencies (`hilt-android` and `hilt-compiler`) are declared in the version catalog and `app/build.gradle.kts`.
- [ ] The KSP Gradle plugin is applied for annotation processing (not kapt).
- [ ] The `com.google.dagger.hilt.android` Gradle plugin is applied.
- [ ] A custom `Application` subclass annotated with `@HiltAndroidApp` exists.
- [ ] The custom `Application` class is registered in `AndroidManifest.xml` via the `android:name` attribute.
- [ ] `MainActivity` is annotated with `@AndroidEntryPoint`.
- [ ] The project builds successfully with `./gradlew assembleDebug`.

### Engineering Notes

- Add to `gradle/libs.versions.toml`:
  - `hilt = "2.59.2"` and `ksp = "2.0.21-1.0.26"` in `[versions]`
  - `hilt-android`, `hilt-compiler`, and `hilt-android-testing` in `[libraries]`
  - `hilt-android` and `ksp` in `[plugins]`
- In root `build.gradle.kts`, add:
  ```kotlin
  alias(libs.plugins.hilt.android) apply false
  alias(libs.plugins.ksp) apply false
  ```
- In `app/build.gradle.kts`, apply plugins and add dependencies:
  ```kotlin
  plugins {
      alias(libs.plugins.hilt.android)
      alias(libs.plugins.ksp)
  }
  dependencies {
      implementation(libs.hilt.android)
      ksp(libs.hilt.compiler)
      androidTestImplementation(libs.hilt.android.testing)
      kspAndroidTest(libs.hilt.compiler)
      testImplementation(libs.hilt.android.testing)
      kspTest(libs.hilt.compiler)
  }
  ```
- Create `com.example.pucktrivia.PuckTriviaApplication`:
  ```kotlin
  @HiltAndroidApp
  class PuckTriviaApplication : Application()
  ```
- Register in `AndroidManifest.xml`: `<application android:name=".PuckTriviaApplication" ...>`.
- Annotate `MainActivity` with `@AndroidEntryPoint`.
- No manual `@Component` interface is needed — Hilt auto-generates the component hierarchy.

### QA / Testing Notes

- Run `./gradlew assembleDebug` and confirm it succeeds with no annotation-processing errors.
- Launch the app and confirm existing functionality is unchanged (data loads, trivia game works).

### Edge Cases & Risk Analysis

- **KSP compatibility:** KSP `2.0.21-1.0.26` is the correct version for Kotlin `2.0.21`. If version conflicts arise, verify the KSP version matches the Kotlin version prefix.
- **Proguard/R8:** Hilt/Dagger generates concrete classes at compile time, so no special Proguard rules are needed for debug builds. Release builds may need rules if minification is enabled in the future.

---

## Story 2: Create TriviaViewModel with Hilt Injection

**As a** developer,
**I want** a `TriviaViewModel` that holds all game state and is injected by Hilt,
**So that** game state survives configuration changes and dependencies are injected rather than manually constructed.

**Story Points:** 5
**Priority:** P0
**Dependencies:** Story 1 (Hilt must be integrated)

### Acceptance Criteria

- [ ] A `TriviaViewModel` class exists that extends `androidx.lifecycle.ViewModel`.
- [ ] The ViewModel holds all game state that currently lives in `TriviaQuestionScreen`: `score`, `roundNumber`, `usedPlayerIds`, `selectedPlayerId`, `choices`, and `correctPlayer`.
- [ ] The ViewModel also holds the `statsData`, `isLoading`, and `loadError` states currently in `MainActivity`, and performs the network fetch internally. The full `Map<String, List<SkaterStatLeader>>` is stored to support future stat categories.
- [ ] MainActivity no longer performs any network calls directly; the fetch is solely triggered by the ViewModel's `init` block.
- [ ] When the pool of unused players has fewer than 3 remaining, `usedPlayerIds` resets so all players become available again (preserving existing behavior).
- [ ] The ViewModel is annotated with `@HiltViewModel` and uses `@Inject constructor`.
- [ ] The ViewModel is obtained in `MainActivity` using the standard `viewModels()` delegate (Hilt handles factory generation automatically).
- [ ] The ViewModel exposes state via Compose-observable types (`StateFlow` or `mutableStateOf` properties).
- [ ] Unit tests can instantiate `TriviaViewModel` directly (no Android framework required) to verify score logic.

### Design Notes

- No UI changes in this story. The screen should look and behave exactly the same.

### Engineering Notes

- Create `com.example.pucktrivia.TriviaViewModel`:
  - Annotate with `@HiltViewModel` and use `@Inject constructor(...)`.
  - Accept an `OkHttpClient` as a constructor parameter (injected by Hilt).
  - Move `statsData`, `isLoading`, `loadError` from `MainActivity` into the ViewModel as `mutableStateOf` properties.
  - Move `score`, `roundNumber`, `usedPlayerIds`, `selectedPlayerId` from `TriviaQuestionScreen` into the ViewModel as `mutableStateOf` / `mutableIntStateOf` properties.
  - Add computed properties or functions for `choices` and `correctPlayer` that derive from the current state.
  - Add a `fetchStats()` function called from `init {}` that launches a coroutine via `viewModelScope`.
  - Add `selectAnswer(playerId: Int)` function that encapsulates the answer logic (set selectedPlayerId, update score).
  - Add `nextRound()` function that increments roundNumber and clears selectedPlayerId.
- Create a Hilt `@Module` annotated with `@InstallIn(SingletonComponent::class)` (e.g., `NetworkModule`) that provides `OkHttpClient` as a `@Singleton` via `@Provides`.
- No manual `ViewModelFactory` is needed — Hilt generates the factory automatically for `@HiltViewModel` classes.
- In `MainActivity`, obtain the ViewModel via the standard `by viewModels()` delegate. Hilt's `@AndroidEntryPoint` annotation ensures the correct factory is used.
- Add `androidx.lifecycle:lifecycle-viewmodel-compose` to dependencies for Compose integration.

### File Changes

| File | Action | Description |
|------|--------|-------------|
| `libs.versions.toml` | Edit | Add `lifecycle-viewmodel-compose` library |
| `app/build.gradle.kts` | Edit | Add viewmodel-compose dependency |
| `TriviaViewModel.kt` | Create | New ViewModel with all game + network state |
| `di/NetworkModule.kt` | Create | Hilt module providing OkHttpClient (`@InstallIn(SingletonComponent)`) |
| `MainActivity.kt` | Edit | Obtain ViewModel via `by viewModels()`, remove local state and network code |

### QA / Testing Notes

- Verify the app behaves identically to the current version on a normal launch.
- The score, round, and choices should still work correctly — this is a refactor, not a behavior change.
- Verify no state is duplicated (e.g., score shouldn't exist in both ViewModel and Composable).

### Edge Cases & Risk Analysis

- **ViewModel scoping:** The ViewModel is scoped to the Activity. If navigation is added later, it may need to be scoped differently. For now, Activity scope is correct since there's only one screen.
- **Race condition on fetch:** The `init {}` block calls `fetchStats()` immediately. If the Activity is recreated due to configuration change, the ViewModel already exists (that's the point) and `init {}` does NOT re-run, so no duplicate fetch occurs.
- **Thread safety:** `mutableStateOf` is thread-safe for Compose reads. The network fetch runs on `Dispatchers.IO` and writes to state on the main thread via `viewModelScope` (which uses `Dispatchers.Main`). This is safe.

---

## Story 3: Wire TriviaQuestionScreen to ViewModel

**As a** trivia player,
**I want** my score and game progress to survive screen rotation,
**So that** I don't lose my streak when I accidentally rotate my phone.

**Story Points:** 3
**Priority:** P0
**Dependencies:** Story 2 (ViewModel must exist with all state)

### Acceptance Criteria

- [ ] Rotating the device while on the trivia screen does NOT reset the score.
- [ ] Rotating the device does NOT change the current question or answer choices.
- [ ] Rotating the device while an answer is selected preserves the selection and feedback colors.
- [ ] Rotating the device during the loading state continues to show the loading spinner (no duplicate fetch).
- [ ] The `TriviaQuestionScreen` composable no longer contains any `remember { mutableStateOf(...) }` for game state — all state comes from the ViewModel.

### Design Notes

- No visual changes. The screen looks identical. The only user-visible change is that configuration changes no longer reset state.

### Engineering Notes

- Modify `TriviaQuestionScreen` signature to accept individual state values and lambdas (not the ViewModel directly). This is more testable and idiomatic Compose:
  ```kotlin
  @Composable
  fun TriviaQuestionScreen(
      score: Int,
      scoreColor: Color,
      questionText: String,
      choices: List<SkaterStatLeader>,
      selectedPlayerId: Int?,
      correctPlayerId: Int,
      answered: Boolean,
      isCorrect: Boolean,
      onAnswerSelected: (Int) -> Unit,
      onNextRound: () -> Unit,
      modifier: Modifier = Modifier,
  )
  ```
- Remove all `remember { mutableStateOf(...) }` / `remember { mutableIntStateOf(...) }` calls from `TriviaQuestionScreen`. These are replaced by reads from the ViewModel.
- In `MainActivity`, collect ViewModel state and pass it to `TriviaQuestionScreen`.
- The `choices` derivation logic (shuffling, filtering used players) must move to the ViewModel (Story 2) so it's stable across recompositions. It should be triggered by `nextRound()` rather than `remember(roundNumber)`.

### QA / Testing Notes

- **Primary test:** Launch app, answer 2-3 questions correctly (score = 200-300), rotate device. Score must still show 200-300.
- **Mid-answer rotation:** Select an answer but don't tap "Next". Rotate. The answer selection, feedback colors, and score update must all be preserved.
- **Loading rotation:** Rotate while the loading spinner is showing. The spinner should continue (no crash, no duplicate fetch, no error).
- **Error rotation:** If the network fetch fails, rotate. The error message should persist.
- Verify the "Next" button still works after rotation.
- Verify answer buttons are still correctly disabled after answering and rotating.

### Edge Cases & Risk Analysis

- **Choices stability:** The `choices` list must be stable across configuration changes. In the current code, `remember(roundNumber)` recomputes choices when `roundNumber` changes. In the ViewModel, this must be stored as state — not recomputed on access — because the ViewModel survives but `remember` does not. If `choices` were a derived computation, it would reshuffle on recomposition after config change, showing different players.
- **Process death:** The ViewModel does NOT survive process death (only `SavedStateHandle` does). If the system kills the app in the background and the user returns, all state resets. This is acceptable and consistent with the existing "session-scoped" behavior described in the score counter requirements. `SavedStateHandle` integration is out of scope.
- **Compose `remember` remnants:** If any `remember` calls for game state are accidentally left in the composable, the state will be duplicated — the ViewModel holds the source of truth but the composable also has its own copy that resets on config change. This would cause subtle bugs. All game state `remember` calls must be removed.

---

## Story 4: Unit Tests for Score Logic in ViewModel

**As a** developer,
**I want** unit tests verifying the score logic in TriviaViewModel,
**So that** I have confidence the scoring rules are correct and won't regress.

**Story Points:** 2
**Priority:** P1
**Dependencies:** Story 2 (ViewModel must exist)

### Acceptance Criteria

- [ ] A unit test verifies that the initial score is 0.
- [ ] A unit test verifies that selecting the correct answer increments the score by 100.
- [ ] A unit test verifies that selecting a wrong answer resets the score to 0.
- [ ] A unit test verifies that consecutive correct answers accumulate (0 -> 100 -> 200 -> 300).
- [ ] A unit test verifies that after a wrong answer, the next correct answer brings the score to 100.
- [ ] A unit test verifies that `nextRound()` produces 3 new choices.
- [ ] A unit test verifies that choices remain stable across multiple reads (no reshuffling on access).
- [ ] All tests run via `./gradlew test` without needing an emulator.

### Engineering Notes

- Tests should instantiate `TriviaViewModel` directly. Since the ViewModel takes `OkHttpClient` as a dependency, tests can pass a mock or a real client with a mock server (e.g., `MockWebServer` from OkHttp).
- Alternatively, extract the score logic into a testable function/class that doesn't require the ViewModel at all. However, testing through the ViewModel is more realistic and verifies the integration.
- Use `kotlinx-coroutines-test` for controlling coroutine execution in tests if needed (for the network fetch).
- Add `kotlinx-coroutines-test` to `libs.versions.toml` and `build.gradle.kts` as a `testImplementation` dependency.
- **Dispatchers.Main setup:** `viewModelScope` uses `Dispatchers.Main`, which is unavailable in JVM unit tests. Use `Dispatchers.setMain(StandardTestDispatcher())` in a `@Before` method and `Dispatchers.resetMain()` in `@After` to avoid "Module with the Main dispatcher had failed to initialize" crashes.

### QA / Testing Notes

- Run `./gradlew test` and confirm all new tests pass.
- Verify tests run in under 5 seconds (no real network calls).

---

## Summary Table

| Story | Title | Points | Priority | Dependencies |
|-------|-------|--------|----------|-------------|
| 1 | Add Hilt to the Project | 2 | P0 | None |
| 2 | Create TriviaViewModel with Hilt Injection | 5 | P0 | Story 1 |
| 3 | Wire TriviaQuestionScreen to ViewModel | 3 | P0 | Story 2 |
| 4 | Unit Tests for Score Logic in ViewModel | 2 | P1 | Story 2 |

**Total Story Points:** 12

---

## Assumptions

1. **Hilt, not raw Dagger 2.** Hilt is the recommended DI framework for Android, built on top of Dagger 2. It auto-generates the standard component hierarchy and eliminates boilerplate for ViewModel factory wiring.
2. **KSP, not kapt.** KSP is the recommended annotation processor for Dagger/Hilt with Kotlin 2.0+. It is significantly faster than kapt. Version `2.0.21-1.0.26` matches this project's Kotlin `2.0.21`.
3. **ViewModel does not survive process death.** Only configuration changes are handled. Process death (system kills app in background) resets all state. This is consistent with the existing session-scoped score behavior. `SavedStateHandle` integration is out of scope.
4. **No navigation changes.** The app remains a single-activity, single-screen app. The ViewModel is scoped to the Activity.
5. **Network fetch moves to ViewModel.** The `OkHttpClient` usage currently in `MainActivity` is a dependency that should be injected via Hilt. Moving the fetch to the ViewModel is a natural consequence of the DI + ViewModel migration and keeps `MainActivity` thin.
6. **Compose state observation.** The ViewModel exposes state via `mutableStateOf` (Compose state) rather than `StateFlow` + `collectAsState()`. This is simpler for a fully-Compose UI and avoids the `StateFlow` cold-start pitfall. Either approach is valid; `mutableStateOf` is chosen for fewer moving parts.

## Out of Scope

- Raw Dagger 2 (manual `@Component` interfaces, custom `ViewModelProvider.Factory`)
- `SavedStateHandle` / surviving process death
- Repository layer or domain layer abstractions
- Room database or any persistent storage
- Navigation component or multi-screen architecture
- High-score tracking across sessions
