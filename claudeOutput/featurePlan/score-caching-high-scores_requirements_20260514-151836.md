# Score Caching / High Scores — Requirements Spec

**Feature:** Score caching / high scores
**App:** Puck Trivia (Android, Kotlin + Jetpack Compose, Material 3, single `:app` module)
**Branch context:** `high-score`
**Author:** feature-requirements-writer
**Date:** 2026-05-14

---

## Feature Overview

Persist **every** completed game's score to local device storage so the player's full play history survives app restarts. Each saved score is paired with the date/time the game ended. The existing Game Over screen is extended to display the top three scores (derived from the full history) with their timestamps, and to celebrate the player when their just-completed game earns a top-three slot. Scores of `0` are persisted to history but never appear in the top-three leaderboard and never trigger celebration.

Persistence is **local-only** — no server sync, no cloud backup, no cross-device behavior. Data loss on app uninstall or `clear data` is acceptable.

### Definition of Done
- A completed game's score + end timestamp is written to local storage before the Game Over screen renders its high-score section.
- The Game Over screen shows the top three persisted scores (highest first) with formatted date/time, and a celebratory message when the current game placed in the top three.
- High scores survive app process death and cold start.
- Unit tests cover the high-score storage/ranking logic; the Game Over screen's new states are covered by Compose UI tests.
- `./gradlew test` and `./gradlew assembleDebug` pass.

---

## Codebase Grounding

Findings from `app/src/main/java/com/example/pucktrivia/`:

- **Game state** lives in `TriviaViewModel` (`@HiltViewModel`). Relevant fields: `score` (Int, `+100` per correct answer in `selectAnswer`), `correctAnswered`, `totalAnswered`, `lives`, `gameOver` (Boolean), `selectedMode` (`SeasonMode?`).
- **Game over is triggered** only in `TriviaViewModel.nextRound()` when `lives == 0`, which sets `gameOver = true`. `resetGame()` clears all state including `gameOver` and `score`.
- **`MainActivity`** is a single `ComponentActivity` (`@AndroidEntryPoint`) with a Compose `when` block selecting screens. The `viewModel.gameOver` branch renders `GameOverScreen(score, correctAnswered, totalAnswered, onPlayAgain = viewModel::resetGame)`.
- **`GameOverScreen`** (`GameOverScreen.kt`) is a stateless `@Composable` taking primitives + an `onPlayAgain` lambda. It renders "Game Over", the score, the correct/total line, and a "Play Again" button in a centered `Column`.
- **DI**: Hilt is wired (`PuckTriviaApplication` is `@HiltAndroidApp`, `NetworkModule` is `@InstallIn(SingletonComponent::class)`). The project has an `@IoDispatcher` qualifier (`di/`) for `Dispatchers.IO`.
- **No persistence layer exists today** — no DataStore, SharedPreferences, or Room dependency in `gradle/libs.versions.toml` or `app/build.gradle.kts`.
- **Testing**: JUnit4, `kotlinx-coroutines-test`, `hilt-android-testing`, `okhttp-mockwebserver` are on the test classpath. `unitTests.isReturnDefaultValues = true` is set. Compose UI test deps are on `androidTest`.

---

## Open Assumptions (confirm or override)

These were assumed to keep the spec actionable. Each is flagged at the relevant AC.

1. **Persistence mechanism:** Jetpack DataStore (Preferences) — lightest fit for 3 records, async-by-default, aligns with existing coroutine usage. Room would be overkill; SharedPreferences is synchronous and discouraged for new code.
2. **History depth:** Persist **every** completed game's score (full history). The top-three leaderboard is a derived view over that history, not the storage shape.
3. **Zero scores:** A score of `0` is **persisted to history** but is **not eligible** for the top-three leaderboard — it is filtered out of the leaderboard view and never triggers celebration.
4. **Tie-breaking:** A new score must be **strictly greater than** the current 3rd-place score to place in the top three. A score tying 3rd place does **not** place and is **not** celebrated. (The score is still saved to history regardless.)
5. **Per-mode vs global:** **Single global list** across both `SeasonMode`s. Per-mode leaderboards noted as future consideration.
6. **Date/time format:** Device-locale short date + short time (e.g., `May 14, 2026, 3:42 PM`).
7. **Save trigger:** Every game that reaches `gameOver == true` records exactly one score entry.

---

## Structure Decision

**Epic with 3 stories.** The feature introduces a new persistence layer (new dependency, new module, DI wiring), a non-trivial ranking rule, and distinct UI changes. Splitting isolates the testable storage/ranking core (Story 1) from the save integration (Story 2) and the presentation (Story 3), allowing iterative delivery and independent review. Story 1 unblocks both 2 and 3; 2 and 3 can proceed in parallel once 1 lands.

---

# Epic: Local High-Score Persistence & Display

## Story 1: High-score storage and ranking core

**As a** Puck Trivia player,
**I want to** my best game scores remembered on my device,
**So that** my personal bests are not lost when I close the app.

**Story Points:** 5
**Priority:** P0
**Dependencies:** None

### Acceptance Criteria
- [ ] A score entry consists of a numeric score and the date/time the game ended.
- [ ] Every submitted score is appended to the persisted history — the history is never capped or pruned. *(Assumption 2: full history.)*
- [ ] A derived "top three" view returns the three highest scores from history, ordered highest to lowest.
- [ ] Scores of `0` are persisted to history but are excluded from the top-three view. *(Assumption 3.)*
- [ ] The top-three view contains at most three entries even when history holds more; when history (excluding zeros) holds fewer than three, the view returns only the entries that exist.
- [ ] A submitted score that is not strictly greater than the current 3rd-place score does not change the top-three view. *(Assumption 4.)*
- [ ] When two entries have equal scores, both are retained in history and ordering between them in the top-three view is deterministic (newer-first as the tie-break for display order).
- [ ] Persisted history is readable after the app process is killed and cold-started.
- [ ] Reading history when none has ever been saved returns an empty result without error.
- [ ] Submitting a score reports back whether that score placed in the top-three view.

### Design Notes
- No UI in this story; it is the data/domain layer only.

### Engineering Notes
- **Add dependency:** Jetpack DataStore Preferences (`androidx.datastore:datastore-preferences`). Add a `datastore` version + library entry to `gradle/libs.versions.toml` and wire into `app/build.gradle.kts`. *(Assumption 1.)*
- Suggested shape: a `HighScoreRepository` interface + DataStore-backed implementation, plus a `HighScore` data class (`score: Int`, `endedAt: Long` epoch millis). Store the full history as a serialized list payload (e.g., a single JSON string preference). `org.json` is already available but only on the test classpath — if JSON encoding is used in `main`, add `json` as an `implementation` dependency, or hand-roll a delimited encoding to avoid the new prod dependency.
- Persist epoch millis, not formatted strings — formatting is a presentation concern (Story 3).
- Provide the repository via a new Hilt module (`@InstallIn(SingletonComponent::class)`), mirroring `NetworkModule`. DataStore instance should be a `@Singleton` (a process must hold only one DataStore per file).
- Repository write API: a suspend function that appends to history and returns a "did it place in the top three" boolean (or an enum result). Repository read API: expose the full history and/or the derived top-three view (suspend function or `Flow`).
- Ranking logic (filter out zeros, sort desc, take top 3, strictly-greater placement detection vs. the prior 3rd place) should live in a pure function operating over the history list, unit-testable without DataStore.
- Do all DataStore I/O on `@IoDispatcher`.

### QA / Testing Notes
- Unit-test the pure ranking function: empty history; history with 1–2 non-zero entries; history with >3 non-zero entries (view returns exactly 3, highest); a new score higher than 3rd place (places); a new score equal to 3rd place (does not place); a new score of `0` (saved to history, never in the view, never places); a history of all zeros (view is empty); placement boolean correctness in each case.
- Verify the history list itself is never capped — appending many entries (including zeros and duplicates) retains all of them.
- Test the repository against a temporary/in-memory DataStore (or a temp file `TestScope` DataStore) — verify round-trip persistence of full history and empty-state read.
- Concurrency: two writes in quick succession must not corrupt the history or lose an entry.

### Edge Cases & Risk Analysis
- **Corrupt/garbled stored payload** (e.g., from a future format change): repository must catch deserialization failure and fall back to an empty history rather than crash.
- **Equal scores at the boundary**: three non-zero entries all with the same score, then a fourth equal score — fourth is saved to history but does not enter the top-three view (not strictly greater than 3rd place).
- **History of only zero scores**: all are persisted, but the top-three view is empty and no submission ever reports a placement.
- **Negative scores**: not currently producible (`score` only increments by 100 from 0), but the ranking function should not special-case-crash on them; treat as ordinary integers (and, like `0`, they would never out-rank a positive score).
- **Unbounded history growth**: full history is intentional and acceptable for this app's volume, but storing a versioned payload now (a schema-version field) keeps a later move to pruning, per-mode lists, or a "view all" screen cheap. Recommended as a low-cost extension point.

---

## Story 2: Save score on game over

**As a** Puck Trivia player,
**I want to** my score saved automatically when a game ends,
**So that** I don't have to do anything to keep my high scores up to date.

**Story Points:** 3
**Priority:** P0
**Dependencies:** Story 1

### Acceptance Criteria
- [ ] When a game reaches the game-over state, the final score is submitted to high-score storage exactly once, paired with the time the game ended. *(Assumption 7.)*
- [ ] The timestamp recorded reflects the moment the game ended (transition into game-over), not the moment the screen is later recomposed or re-read.
- [ ] Starting a new game after game over ("Play Again") and reaching game over again submits a second, independent entry.
- [ ] Navigating away and back, backgrounding/foregrounding, or rotating the device while on the Game Over screen does not cause the same game's score to be submitted more than once.
- [ ] The result of the submission (whether the score placed in the top three) is available to the Game Over screen for display. *(Consumed by Story 3.)*
- [ ] If the save fails (storage error), the game-over flow still completes and the screen still renders; the failure is logged, not surfaced as a crash or blocking error.

### Design Notes
- No new UI in this story. The Game Over screen continues to render as it does today until Story 3 lands; this story only wires the save + exposes results.

### Engineering Notes
- Inject `HighScoreRepository` into `TriviaViewModel` via the constructor (Hilt already constructs the ViewModel).
- The save must be triggered once per game-over transition. `nextRound()` is the single place `gameOver` becomes `true` — trigger the save there (in `viewModelScope`, on `@IoDispatcher`), or expose a dedicated `onGameOver()` path. Do **not** trigger from a Composable side-effect keyed on `gameOver` alone unless guarded, because recomposition/config-change can re-fire it.
- Use an idempotency guard: a `scoreSaved` flag on the ViewModel reset by `resetGame()`, so the save runs at most once between resets.
- Expose new ViewModel state for Story 3 to consume, e.g. `highScores: List<HighScore>` and `currentGamePlacedInTopThree: Boolean` (or a nullable rank). Populate these when the save completes.
- Capture `endedAt` at the instant `gameOver` is set, before the async write — pass the timestamp into the repository call.
- `resetGame()` must clear the new fields (`scoreSaved`, `highScores`, placement flag) alongside the existing reset.

### QA / Testing Notes
- ViewModel unit test with a fake `HighScoreRepository`: drive a game to `lives == 0`, assert the repository received exactly one submission with the expected score.
- Assert a second game after `resetGame()` produces a second submission.
- Simulate repository throwing — assert `gameOver` still becomes `true` and no exception propagates.
- Assert `resetGame()` clears `scoreSaved` and the placement/high-score fields.
- Verify the recorded timestamp is captured at game-over time (inject a clock/time provider so the test is deterministic — see edge cases).

### Edge Cases & Risk Analysis
- **Double-submit on config change / process recreation**: the primary risk. The `scoreSaved` guard on the ViewModel handles config changes (ViewModel survives rotation). Full process death mid-game-over is acceptable to mishandle (rare, data loss tolerated), but should not crash.
- **Rapid "Play Again" then immediate game over**: each game's submission must carry its own score and timestamp; the guard resets in `resetGame()`.
- **Testability of `now()`**: `System.currentTimeMillis()` called inline is untestable. Inject a time provider (a `() -> Long` or a small `Clock` abstraction) through Hilt so tests are deterministic. Low cost, recommended.
- **Save still in flight when user taps "Play Again"**: `resetGame()` clears state immediately; the in-flight write should still complete against storage. Ensure the write coroutine does not write stale ViewModel-derived state — it should operate on the score/timestamp captured at call time, not re-read mutable fields.

---

## Story 3: Display high scores and celebrate a new top-three on the Game Over screen

**As a** Puck Trivia player,
**I want to** see the top three scores and be told when I just made the list,
**So that** I feel rewarded and can track my personal bests.

**Story Points:** 3
**Priority:** P1
**Dependencies:** Story 1, Story 2

### Acceptance Criteria
- [ ] The Game Over screen displays the top three persisted scores, highest first, each shown with its score and a formatted date/time. *(Assumption 6: device-locale short date + short time.)*
- [ ] When fewer than three scores have ever been saved, the screen shows only the entries that exist (e.g., one or two rows), without placeholder rows or errors.
- [ ] When the just-completed game placed in the top three, a celebratory message is shown on the Game Over screen (e.g., "New top-3 score!").
- [ ] When the just-completed game did not place in the top three, no celebratory message is shown.
- [ ] The existing Game Over content (the "Game Over" heading, the current game's score, the correct/total line, and the "Play Again" button) remains present and functional.
- [ ] The high-score section visually distinguishes the just-completed game's entry when it placed (e.g., highlight or marker on the matching row).
- [ ] The screen renders correctly before the high scores have finished loading from storage (brief empty/loading state is acceptable; no crash, no flicker of wrong data).

### Design Notes
- Extend `GameOverScreen.kt`. Keep it a **stateless composable** — add parameters: `highScores: List<HighScore>` (or a UI model) and a placement indicator (`placedInTopThree: Boolean`, or the placed entry / rank). `MainActivity` passes these from `TriviaViewModel`, consistent with how `score`/`correctAnswered`/`totalAnswered` are passed today.
- Use Material 3 components and `MaterialTheme.typography` / `colorScheme` consistent with the existing screen. The high-score list can be a simple `Column` of rows (max 3 items — no `LazyColumn` needed).
- Celebratory message: use an accent from `colorScheme` (e.g., `primary` or `tertiary`), placed prominently near the score. Keep copy short.
- Highlighted row for the current game: subtle background tint or a leading marker, not a jarring color.
- Each row: rank/score on one side, formatted date/time secondary. Format with a locale-aware formatter (`java.time` with `DateTimeFormatter.ofLocalizedDateTime`, or `DateUtils`). Min SDK 30 — `java.time` is fully available.
- Provide a `@Preview` for: full three-entry list with celebration, three-entry list without celebration, one-entry list (early-launch state), empty list.
- Accessibility: the celebratory message and each high-score row must be reachable by TalkBack with a sensible content description (e.g., row reads "Rank 1, 1200 points, May 14 2026 3:42 PM"). Ensure score and date are not announced as disconnected fragments.

### Engineering Notes
- No new persistence work — consume `viewModel.highScores` and the placement flag added in Story 2.
- Date/time formatting belongs in the UI layer (or a small mapper), converting `endedAt` epoch millis to a localized string. Do not format in the repository.
- `MainActivity`'s `viewModel.gameOver` branch is the only call site to update.
- If high scores load asynchronously after the screen appears, drive the list from observable ViewModel state so the screen recomposes when data arrives.

### QA / Testing Notes
- Compose UI tests: three-entry list renders three rows in descending order; one-entry list renders one row; empty list renders no rows and no crash.
- Celebration message present when placement flag is true; absent when false.
- Current-game row highlight appears on the correct row when placed.
- Existing Game Over elements still present and "Play Again" still invokes the reset callback.
- Verify locale-sensitive formatting does not crash under a non-default locale.
- Manual: rotate the device on the Game Over screen — list and celebration state persist (backed by surviving ViewModel state).

### Edge Cases & Risk Analysis
- **Tie with the just-completed score**: if the current game's score equals an existing displayed entry but did not place (Assumption 4), the celebratory message must not show, and the highlight must land on the actual persisted current-game entry only — not on a different entry that happens to share the score value. Match on identity (score + timestamp), not score alone.
- **Two displayed entries share the current game's exact score**: highlight must use the timestamp to disambiguate the current game's row.
- **Very large scores / long formatted dates**: ensure rows do not clip or overflow; allow text to wrap or ellipsize gracefully on narrow screens.
- **Async load race**: high scores arriving after first composition must not flash stale content. Initialize the list state to empty and let recomposition fill it.
- **First-ever game with a non-zero score**: list shows exactly one row (the just-played game) and the celebratory message shows, since a non-zero score into an empty leaderboard always places.
- **First-ever game with a score of `0`**: the score is saved to history, but the top-three view stays empty, no row is shown for it, and no celebratory message appears. Confirm this is the intended early-launch experience.

---

## Cross-Cutting Notes

### New dependency summary
- `androidx.datastore:datastore-preferences` — add version to `gradle/libs.versions.toml`, add `implementation` to `app/build.gradle.kts`.
- Possibly `org.json` promoted from `testImplementation` to `implementation` if JSON encoding is used in `main` (or avoid by hand-rolling a delimited format).
- Optional: a small injectable time provider abstraction (no external dependency) for testable timestamps.

### Future Considerations (not in scope)
- A "view all" screen surfacing the full persisted history (the data is already stored — only the UI is out of scope).
- Pruning very old history if storage size ever becomes a concern.
- Per-`SeasonMode` leaderboards (RegularSeason vs Playoffs).
- Player name / initials per entry.
- Server-side sync or cloud backup.
- "Clear high scores" settings action.

Storing a versioned payload in Story 1 keeps these cheap to add later.
