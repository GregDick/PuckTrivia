# Feature: Preserve Game State and High Scores Across Background / Foreground

## Feature Overview

When a player backgrounds Puck Trivia mid-game (Home press, app switch) and the OS later kills the app's process to reclaim memory, the in-progress game is currently lost — on return, the app restarts at the Start screen. This feature persists the in-progress game state so the player resumes exactly where they left off after process death, and confirms that high scores remain durable across the same lifecycle. High-score persistence already exists via DataStore; this feature's net-new work is in-progress game-state survival across process death, plus verification/hardening of the existing high-score store.

**Definition of Done:** A player mid-game who is backgrounded and whose process is then killed by the OS returns to the same question, with the same score, lives, round number, selected answer (if any), and answer feedback intact. High scores survive app restart and process death (already true today; verified and protected by tests). Configuration changes (rotation) continue to preserve state as they do today. The app compiles, `./gradlew test` passes, and behavior on a normal launch is unchanged.

---

## Current-State Findings (grounding)

These observations are drawn from the existing codebase and shape the scope below.

- **All in-progress game state lives in `TriviaViewModel`** (`app/src/main/java/com/example/pucktrivia/TriviaViewModel.kt`) as `mutableStateOf` / `mutableIntStateOf` properties: `selectedMode`, `score`, `roundNumber`, `lives`, `totalAnswered`, `correctAnswered`, `selectedPlayerId`, `choices`, `correctPlayer`, `questionText`, `statUnitLabel`, `usedIds`, `pools`, `statsData`, `goalieStatsData`, `gameOver`, and the error flags.
- **Configuration changes are already handled.** The ViewModel is process-retained, so rotation preserves state. The prior spec (`configuration-change_requirements_20260326.md`) explicitly scoped out `SavedStateHandle` / process death — that is the gap this feature closes.
- **No `SavedStateHandle`, `rememberSaveable`, or `onSaveInstanceState` is used anywhere** (confirmed via grep). The ViewModel does not survive process death; an OS kill while backgrounded resets the entire game.
- **High scores are already durably persisted.** `DataStoreHighScoreRepository` (`data/HighScoreRepository.kt`) writes the full game history to a Preferences DataStore named `high_scores` (`di/PersistenceModule.kt`), encoded by `HighScoreCodec` (`data/HighScoreCodec.kt`) as a versioned `v1;score,endedAt;...` string. The store survives process death and app restart today. The codec already fails safe to an empty history on corrupt/unknown payloads.
- **The leaderboard is only populated at game-over.** `highScores` is empty until a game finishes and `saveScore()` runs; the Start screen does not display the persisted leaderboard. So a restart that lands on the Start screen shows no scores even though they are stored — relevant to the "high scores survive" user perception.
- **State is non-trivial to serialize.** `choices` and `correctPlayer` are `StatLeader` instances (`SkaterStatLeader` / `GoalieStatLeader`), and `pools` / `usedIds` are keyed by the `QuestionType` enum. `pools`, `statsData`, and `goalieStatsData` are large (full fetched datasets). The question of *what minimal state to persist vs. what to re-derive or re-fetch* is the central design decision (see Story 1).

---

## Epic: Survive Process Death With an Active Game

The feature splits into three stories: persist in-progress game state across process death (the core gap), surface the persisted leaderboard so "high scores survived" is observable, and harden/verify the existing high-score store against the same lifecycle. Stories 2 and 3 are independent of Story 1 and can ship in any order.

---

### Story 1: Persist and Restore In-Progress Game State Across Process Death

**As a** trivia player,
**I want to** return to my in-progress game exactly where I left off after the OS kills the app in the background,
**So that** I don't lose my score, lives, and current question when I switch apps and come back later.

**Story Points:** 8
**Priority:** P0
**Dependencies:** None (builds on the existing `TriviaViewModel`)

#### Acceptance Criteria

- [ ] After starting a game and answering at least one question, backgrounding the app, having the process killed by the OS, and relaunching, the player is returned to an active game (not the Start screen).
- [ ] The restored game shows the same `score`, `lives`, `roundNumber`, `totalAnswered`, and `correctAnswered` as before the kill.
- [ ] The restored game shows the same current question: the same question text, the same three answer choices in the same display, and the same correct answer, and the same season mode (Regular Season vs. Playoffs).
- [ ] If the player had selected an answer but not yet advanced ("Next" not tapped), the restored screen shows that selection with its answer feedback (correct/incorrect coloring) and the disabled answer buttons, identical to before the kill.
- [ ] If the player had not yet selected an answer, the restored screen is the un-answered question with all choices selectable.
- [ ] Advancing past the restored question with "Next" continues the game normally: the next question is prepared, and previously-used players are not re-shown (the used-player history is preserved across the kill).
- [ ] A game that had reached Game Over before the kill returns to the Game Over screen with the same final score and summary on relaunch, not a fresh question.
- [ ] Starting a brand-new game (Start screen present, no prior session) behaves exactly as today.
- [ ] Tapping "Play Again" / resetting the game clears any persisted in-progress state so a subsequent kill does not restore the abandoned game.
- [ ] Rotation continues to preserve state with no visible reload or flicker (no regression from today).

#### Design Notes

- No new screens or visual elements. The restore is invisible: the player simply sees the screen they left.
- Restoration should complete before first frame where feasible so the player does not see the Start screen flash before the game reappears. If an async re-derivation step is unavoidable, show the existing loading spinner rather than the Start screen.
- Accessibility: no new interactive elements, so no new content descriptions required. Restored answer-feedback colors must retain the same semantics they have today (error color for a wrong selection).

#### Engineering Notes

- Persist the *minimal* state needed to reconstruct the current question and counters, rather than the full fetched datasets. Recommended approach: inject a `SavedStateHandle` into `TriviaViewModel` (Hilt supports this automatically for `@HiltViewModel`) and mirror the game's durable fields into it.
- Decide explicitly per field. Two viable strategies — call this out for the implementer to choose during planning:
  - **(A) Persist counters + the current question, re-fetch the datasets.** Save `selectedMode`, `score`, `lives`, `roundNumber`, `totalAnswered`, `correctAnswered`, `selectedPlayerId`, `gameOver`, the current `choices` (as ids + display fields), `correctPlayer` id, `questionText`, `statUnitLabel`, and the `usedIds` map. On restore, the current question renders immediately from the saved snapshot; `pools` are rebuilt lazily (re-fetch on the next `nextRound()` if not yet in memory). This keeps the saved blob small and avoids serializing the entire dataset.
  - **(B) Persist everything including pools/datasets.** Larger blob; `SavedStateHandle` has a binder transaction size limit (~1 MB, and practically less) — the full skater+goalie datasets risk exceeding it, so (A) is preferred. Note this limit explicitly in planning.
- `SavedStateHandle` only accepts types supported by `Bundle`. `StatLeader`, `QuestionType` keys, and the `usedIds`/`pools` maps are not `Bundle`-native. Serialize them to a stable string/primitive form (the project already favors hand-rolled codecs — see `HighScoreCodec` — over adding a JSON dependency; follow that precedent). Keep a schema-version token so a future format change is detectable, as `HighScoreCodec` does.
- The existing `mutableStateOf` properties can remain the read source for Compose; write-through to `SavedStateHandle` in each mutator (`startGame`, `selectAnswer`, `nextRound`, `prepareRound`, `resetGame`). Centralize the write to avoid drift between the live state and the saved snapshot.
- `resetGame()` must clear the saved snapshot (e.g., remove the keys / set a "no active game" marker) so an abandoned game is not restored.
- If strategy (A) is chosen and `nextRound()` needs `pools` that were dropped on process death, trigger a re-fetch (reuse the `startGame` fetch path) and show the loading spinner; preserve the restored counters during that fetch.
- `SavedStateHandle` survives system-initiated process death but is bounded and is *not* a substitute for DataStore for anything that must survive a user-initiated swipe-kill from Recents — clarify expected behavior with the product owner (see Edge Cases).

#### QA / Testing Notes

- **Process-death simulation (primary):** Use `adb shell am kill com.example.pucktrivia` (or Android Studio's "Terminate Application" while backgrounded), or developer-options "Don't keep activities", to force the saved-state restore path rather than the warm-ViewModel path. Verify each AC after relaunch.
- Test restore at each game phase: un-answered question, answered-but-not-advanced, mid-game after several rounds, and Game Over.
- Verify used-player history survives: after restore, advance several rounds and confirm no immediate repeat of a player already seen pre-kill (within the reset-on-exhaustion rules).
- Verify the saved snapshot stays within `SavedStateHandle`/Bundle size limits with a maxed-out `usedIds` history (play many rounds, then kill) — no `TransactionTooLargeException`.
- Verify `resetGame()` clears the snapshot: finish or reset a game, then kill and relaunch — must land on Start, not restore.
- Regression: rotation still preserves state with no flicker; a cold first launch still shows the Start screen.
- Unit tests: instantiate `TriviaViewModel` with a real `SavedStateHandle`, drive a game, simulate restoration by constructing a new ViewModel from the same handle's contents, and assert all counters/question fields match. Run via `./gradlew test` with no emulator. Follow the existing `Dispatchers.setMain(StandardTestDispatcher())` pattern noted in the prior spec.

#### Edge Cases & Risk Analysis

- **Swipe-kill from Recents vs. OS background-kill.** `SavedStateHandle` is preserved for system-initiated death but is cleared when the user swipe-dismisses the task from Recents. The feature goal mentions "OS kills/recreates the activity" — confirm with the product owner whether resuming after a *user* swipe-kill is also required. If yes, the in-progress snapshot must go to DataStore, not `SavedStateHandle`. Default assumption: handle OS-initiated death via `SavedStateHandle`; user swipe-kill is out of scope.
- **Stale dataset on restore.** If strategy (A) re-fetches, the live NHL stat values may have changed since the game started, so a re-derived `correctPlayer` could differ. Persisting the resolved current question (choices + correct id) avoids this for the *current* question; only future rounds use fresh data, which is acceptable.
- **`TransactionTooLargeException`.** A long game accumulates a large `usedIds` set. Encode it compactly (ids only) and verify against the Bundle limit; this is the main reason to prefer strategy (A) and not persist full datasets.
- **Mid-fetch process death.** If killed during the initial `startGame` fetch (loading state), there is no question to restore. Restore should resume in a sane state — either re-trigger the fetch for the saved `selectedMode` or fall back to the Start screen. Pick one and make it deterministic.
- **Error states.** `loadError`, `fatalError`, and `playoffsUnavailable` are transient and need not be persisted; on restore, re-deriving from a clean fetch is preferable to restoring a stuck error screen.
- **Double-restore / first-frame flash.** Ensure the `MainActivity` `when` block does not momentarily render the Start screen (`selectedMode == null`) before the snapshot is applied — initialize `selectedMode` from `SavedStateHandle` in the ViewModel's construction, not asynchronously.
- **Future compatibility.** Keep the snapshot codec versioned so a future schema change (e.g., new question types, new counters) can be detected and safely discarded rather than crashing — mirror `HighScoreCodec`'s fail-safe decode.

---

### Story 2: Show the Persisted Leaderboard on the Start Screen

**As a** trivia player,
**I want to** see my saved high scores when I open the app,
**So that** it is evident my best scores persisted across closing and reopening the app.

**Story Points:** 3
**Priority:** P1
**Dependencies:** None (reads the existing `HighScoreRepository`)

#### Acceptance Criteria

- [ ] On launching the app to the Start screen, the player can see the persisted top-three leaderboard (highest score first), loaded from durable storage.
- [ ] After fully closing the app (including process death) and reopening, the leaderboard shown on the Start screen matches what was stored before — scores are not lost.
- [ ] When no games have ever been completed, the Start screen shows an empty-state appropriate to "no scores yet" rather than a broken or blank list.
- [ ] The leaderboard read does not block the player from starting a new game — the Start screen is interactive immediately, and scores populate when available.
- [ ] The Game Over leaderboard behavior is unchanged from today.

#### Design Notes

- Reuse the existing leaderboard presentation used on the Game Over screen (`GameOverScreen.kt`) for visual consistency; do not invent a new component if the existing one can be reused or factored out.
- Empty state: a brief "No high scores yet" message or simply omit the leaderboard block — match the product owner's preference; default to omitting the block when empty to keep the Start screen clean.
- Accessibility: leaderboard rows should be readable in order by a screen reader; reuse existing semantics from the Game Over leaderboard.

#### Engineering Notes

- Add a leaderboard read to `TriviaViewModel` that calls `highScoreRepository.topThree()` (already exists) on init / when on the Start screen, exposing it via a Compose-observable property the Start screen reads.
- Do not block `onCreate` / first frame on the DataStore read; load it in `viewModelScope` and let the list populate reactively.
- Keep the current-game highlight logic (`currentGameHighScore`, `placedInTopThree`) scoped to the Game Over screen; the Start-screen leaderboard is a plain top-three view.

#### QA / Testing Notes

- Complete a few games to seed scores, fully kill the app, relaunch, and confirm the Start screen shows the same top three.
- Fresh install / cleared data: Start screen shows the empty state, not a crash.
- Verify the Start screen is tappable immediately even if the DataStore read is artificially delayed.

#### Edge Cases & Risk Analysis

- **Corrupt store.** `HighScoreCodec.decode` already returns an empty list on malformed data; confirm the Start screen renders the empty state rather than erroring.
- **Read race with a just-finished game.** If Story 1 and the leaderboard read interact, ensure a newly saved score is reflected when returning to the Start screen (re-read on navigation back to Start).

---

### Story 3: Verify and Harden Durable High-Score Persistence

> ⚙️ **Engineering-only story.** This story has no user-facing result — it adds automated test coverage that locks in existing durability behavior. It is intentionally split out from the feature's user-facing work (Stories 1 and 2) so the durability guarantee is independently enforceable and traceable. Normally a story should deliver a user-facing outcome; this one is an explicit exception.

**As a** developer,
**I want** automated confidence that high scores survive process death and app restart,
**So that** the durability guarantee this feature promises cannot silently regress.

**Story Points:** 2
**Priority:** P1
**Dependencies:** None (the persistence layer already exists)

#### Acceptance Criteria

- [ ] A test verifies that a submitted score is present in the leaderboard read back from a freshly constructed repository instance backed by the same DataStore (simulating a restart).
- [ ] A test verifies that the full game history is retained and never pruned, and that the derived top-three is correct after more than three games.
- [ ] A test verifies that a corrupt or unknown-schema payload decodes to an empty history without throwing (codec fail-safe), confirming a corrupt store cannot crash the app on launch.
- [ ] A test verifies that two rapid successive submissions both persist (no lost update), consistent with the serial `DataStore.edit` guarantee documented in the repository.
- [ ] All tests run via `./gradlew test` without an emulator (or as instrumented tests where a real DataStore is required — choose per the existing test setup).

#### Engineering Notes

- Prefer testing `HighScoreCodec` and `HighScoreRanking` as pure units (no Android) for encode/decode/ranking, and the repository against a temporary/in-memory DataStore for the round-trip and concurrency cases.
- No production code change is expected unless a gap is found; this story is primarily test coverage that locks in the existing behavior so the feature's durability claim is enforced.

#### QA / Testing Notes

- Manual sanity: play a game, force `adb shell am kill`, relaunch, complete another game, and confirm both appear in history-derived rankings.
- Confirm `./gradlew test` runtime stays fast (no real network).

#### Edge Cases & Risk Analysis

- **History growth unbounded.** The repository intentionally never prunes history. Over a very large number of games the encoded string grows; note this as a known, accepted characteristic. If it ever becomes a concern, capping history is a future enhancement, not part of this feature.
- **Schema migration.** The `v1` token lets a future format change discard old data safely; tests should assert an unknown version decodes to empty rather than partially parsing.

---

## Summary Table

| Story | Title | Points | Priority | Dependencies |
|-------|-------|--------|----------|--------------|
| 1 | Persist and Restore In-Progress Game State Across Process Death | 8 | P0 | None |
| 2 | Show the Persisted Leaderboard on the Start Screen | 3 | P1 | None |
| 3 | Verify and Harden Durable High-Score Persistence ⚙️ *(engineering-only)* | 2 | P1 | None |

**Total Story Points:** 13

---

## Assumptions

1. **High scores are already durable.** The existing DataStore-backed `HighScoreRepository` persists the full history across process death and app restart. The "durable high-score persistence" goal is therefore largely *already met*; this feature verifies it (Story 3) and surfaces it to the player (Story 2) rather than rebuilding it. If the product owner expected high scores to be newly built, flag this — it is not.
2. **Process death is handled via `SavedStateHandle`, not DataStore.** In-progress game state is restored after *OS-initiated* process death. Resuming after a *user* swipe-kill from Recents is assumed out of scope (it would require persisting the in-progress game to DataStore). Confirm with the product owner if swipe-kill resume is required.
3. **Minimal-state persistence (strategy A).** The implementer persists the resolved current question plus counters and re-derives/re-fetches the rest, to stay within the `SavedStateHandle`/Bundle size limit. The full fetched datasets are not persisted.
4. **Configuration changes already work** via the process-retained ViewModel and must not regress; no new work is needed there beyond ensuring the saved-state write-through does not introduce flicker.
5. **Single-activity, single-screen architecture** is unchanged; no navigation component is introduced.
6. **Hand-rolled, versioned serialization** is used for the in-progress snapshot, following the `HighScoreCodec` precedent, to avoid adding a JSON dependency.

## Out of Scope

This list is included because the high-score work is largely pre-existing and a reader could otherwise assume more is being built than is.

- Rebuilding or migrating the existing high-score DataStore (it already exists and works).
- Resuming an in-progress game after a user swipe-kill from Recents (see Assumption 2 — pending product confirmation).
- Capping or pruning high-score history.
- Cloud / cross-device sync of game state or scores.
- Any change to scoring rules, question generation, lives, or the NHL data fetch beyond what restore requires.
