# Most Goals Question Type — Requirements Specification

**Date:** 2026-03-31 (updated 2026-04-03)
**Feature:** Add a "most goals" question type alongside the existing "most points" question type
**Status:** Implemented

## Feature Overview

The Puck Trivia app currently presents a single question format: "Which of these players currently has the most points?" This feature introduces a second question type that asks "Which of these players currently has the most goals?" Each round, the app randomly selects whether to present a points question or a goals question. Each question type draws from its own independent player pool built from the top 50% of that stat's leaderboard, with independent per-type used-player tracking and per-type pool resets.

### Definition of Done

The app randomly presents either a "most points" or "most goals" question each round. Both question types display 3 choices with no tied stat values, show team abbreviations, reveal the correct stat value after answering, and maintain independent per-type used-player sets that prevent repeat players within each question type while allowing cross-type reuse.

---

## Story 1: Fetch and Store Goals Data from the NHL API

**As a** developer maintaining the trivia engine,
**I want** the app to parse the "goals" key from the existing NHL stats API response,
**So that** goals data is available for generating goals questions.

**Story Points:** 2
**Priority:** P0
**Dependencies:** None

### Acceptance Criteria

- [ ] After the app finishes loading, goals data is stored alongside points data in the ViewModel's `statsData` map under the key `"goals"`
- [ ] Each goals entry contains the same player fields as points entries: player ID, first name, last name, sweater number, team abbreviation, position, and stat value
- [ ] If the API response does not contain a `"goals"` key, the app falls back to presenting only points questions (no crash, no error state)

### Design Notes

- No UI changes in this story.

### Engineering Notes

- The existing `fetchSkaterStats()` already iterates over all keys in the JSON response and builds a `Map<String, List<SkaterStatLeader>>`. The NHL API at `https://api-web.nhle.com/v1/skater-stats-leaders/current?limit=-1` returns a `"goals"` key in addition to `"points"`. This means the data is likely already being parsed and stored — verify this by inspecting the actual API response shape. If so, this story may require zero parsing changes and only needs a test to confirm goals data is present.
- The `SkaterStatLeader` model's `value` field is a `Double`, which works for both points (integers presented as doubles) and goals (same format).

### QA / Testing Notes

- Write a unit test with a mock JSON response containing both `"points"` and `"goals"` keys. Assert that `statsData["goals"]` is populated with the correct number of players and correct values.
- Write a unit test with a mock JSON response containing only `"points"` (no `"goals"` key). Assert that the app loads without error and `statsData["goals"]` is null or empty.

### Edge Cases & Risk Analysis

- The NHL API could change its response shape or remove the `"goals"` key in a future season. The fallback behavior (points-only questions) handles this gracefully.
- The `"goals"` key's player objects should have the same JSON structure as `"points"` player objects. If they differ, `SkaterStatLeader` parsing will throw. The existing parsing code handles optional fields (`sweaterNumber` uses `optInt`) which mitigates partial differences.

---

## Story 2: Randomly Select Question Type Each Round

**As a** trivia player,
**I want** each round to randomly present either a "most points" or "most goals" question,
**So that** the game has more variety and tests broader hockey knowledge.

**Story Points:** 3
**Priority:** P0
**Dependencies:** Story 1

### Acceptance Criteria

- [ ] Each round, the app randomly selects between "most points" and "most goals" as the question type with roughly equal probability
- [ ] When a points question is selected, the question text reads "Which of these players currently has the most points?"
- [ ] When a goals question is selected, the question text reads "Which of these players currently has the most goals?"
- [ ] After answering, the revealed stat values display the unit label "pts" for points questions and "g" for goals questions
- [ ] If goals data is unavailable (API did not return a "goals" key), every round presents a points question instead
- [ ] The 3 player choices for a goals question are drawn from the goals leaderboard, not the points leaderboard
- [ ] No two choices within a single round share the same stat value (same no-tie rule as points questions)
- [ ] The correct answer for a goals question is the player with the highest goals value among the 3 choices

### Design Notes

- The question text and stat unit label are the only visual differences between the two question types. Layout, colors, button styles, answer feedback ("Correct!" / "Incorrect!"), and the "Next" button all remain identical.
- Consider whether the question type should be visually distinguished beyond the question text (e.g., a subtle color accent or icon). For MVP, text differentiation alone is sufficient.

### Engineering Notes

- The ViewModel exposes `questionText` and `statUnitLabel` as Compose state so the UI renders the correct wording. The question text was moved out of `MainActivity.kt` and is now driven by ViewModel state.
- `prepareRound()` uses an injected `Random` instance (`random.nextBoolean()`) to select between points and goals, then draws choices from the corresponding pre-computed pool (see Story 1a below).
- No-tie enforcement uses a greedy pick algorithm: shuffle unused players, then iterate and pick players whose stat value has not already been claimed by a previous pick in the current round. This replaces the previous `distinctBy { it.value }.take(3)` approach, allowing tied-value players to appear across different rounds.
- The `Random` instance is injected via constructor parameter with a default of `kotlin.random.Random`, enabling deterministic testing with seeded randoms.

### QA / Testing Notes

- Unit test: with a seeded/controlled random, verify that when "goals" is selected, choices come from the goals pool and the correct player has the highest goals value.
- Unit test: with a seeded/controlled random, verify that when "points" is selected, choices come from the points pool.
- Unit test: when `statsData` contains only "points" (no "goals"), verify that `prepareRound()` always selects points regardless of the random outcome.
- Manual test: play 20+ rounds and verify that both question types appear with reasonable frequency, and that the question text and stat labels match the question type.

### Edge Cases & Risk Analysis

- If a question type's pool has fewer than 3 players with distinct values after filtering out used players, `prepareRound()` resets that type's used-player set and retries. This reset is independent per type (see Story 3).
- A player can appear in both the points and goals leaderboards. Independent per-type used-player tracking (Story 3) allows the same player to appear in both a points question and a goals question.

---

## Story 1a: Build Per-Type Player Pools from the Top 50% of Each Stat Leaderboard

**As a** trivia player,
**I want** each question type to draw from a curated pool of that stat's top performers,
**So that** questions feature meaningfully competitive players rather than drawing from the full leaderboard.

**Story Points:** 3
**Priority:** P0
**Dependencies:** Story 1

### Acceptance Criteria

- [x] After data is fetched, a points player pool is constructed containing the top 50% of players from the points leaderboard, sorted by stat value descending, rounded up (e.g., 7 players yields a pool of 4)
- [x] After data is fetched, a goals player pool is constructed containing the top 50% of players from the goals leaderboard, using the same top-50%-rounded-up rule
- [x] Players with tied stat values are all included in their respective pool -- no deduplication by value occurs during pool construction
- [x] The two pools are built independently; a player's inclusion in the points pool has no bearing on their inclusion in the goals pool
- [x] If the goals leaderboard is absent from the API response, no goals pool is constructed and the app falls back to points-only questions
- [x] Points questions draw choices exclusively from the points pool; goals questions draw exclusively from the goals pool

### Engineering Notes

- Pool construction happens once per data fetch in `buildPools()`, not on every `prepareRound()` call. Pools are stored as ViewModel state (`pointsPool: List<SkaterStatLeader>` and `goalsPool: List<SkaterStatLeader>?`).
- Construction logic: sort by `value` descending, then take `ceil(size / 2.0).toInt()` entries.

---

## Story 3: Independent Per-Type Used-Player Tracking and Pool Reset

**As a** trivia player,
**I want** each question type to independently track which players I have already seen,
**So that** I do not see the same player repeated in the same question category, while still allowing top players to appear in both points and goals questions.

**Story Points:** 5
**Priority:** P0
**Dependencies:** Story 2

### Acceptance Criteria

- [x] Each question type (points, goals) maintains its own independent set of used player IDs (`pointsUsedIds`, `goalsUsedIds`)
- [x] A player who appeared in a points question may still appear in a subsequent goals question (and vice versa)
- [x] A player who appeared in a goals question does not appear in another goals question until the goals used set resets; same rule applies symmetrically for points
- [x] After a round's 3 players are selected, all 3 of their IDs are added to that question type's used set
- [x] A question type's used set resets only when that specific type cannot produce a valid 3-choice round (i.e., the greedy pick returns fewer than 3 players)
- [x] After a reset, the 3 choices for that round are selected from the now-full pool
- [x] Resetting one type's used set does not affect the other type's used set

### Design Notes

- No UI changes. This is purely game logic.

### Engineering Notes

- The previous single `usedPlayerIds` set was replaced with two independent sets: `pointsUsedIds` and `goalsUsedIds`.
- `prepareRound()` attempts a greedy pick from the selected type's pool. If fewer than 3 are returned, it resets only that type's used set to empty and retries.
- The per-type reset strategy avoids the problem where exhausting one question type would prematurely reset the other type's tracking.

### QA / Testing Notes

- Unit test: after a points round uses players A, B, C, verify that a goals round can still select player A if A appears in the goals pool (cross-type reuse is allowed).
- Unit test: after a goals round uses players D, E, F, verify that the next goals round cannot select D, E, or F until the goals used set resets.
- Unit test: exhaust one type's pool and verify that only that type's used set resets, leaving the other type's used set intact.
- Unit test: force two consecutive goals rounds with a pool of exactly 3 entries; verify the second round triggers a goals-only reset and proceeds successfully.

### Edge Cases & Risk Analysis

- **Asymmetric pool sizes:** The goals and points pools may differ in size. Each type resets independently, so asymmetry does not cause cross-type interference.
- **Overlap between pools:** Players appearing in both leaderboards are independently tracked per type. A player used in points is not excluded from goals.
- **Pool reset with a selected question type:** If a goals round triggers a reset, the round still presents a goals question using the now-full pool.

---

## Story 4: Update Existing Tests for Multi-Question-Type Support

**As a** developer,
**I want** the existing test suite to pass and cover the new question-type behavior,
**So that** regressions are caught and the new logic is verified.

**Story Points:** 2
**Priority:** P1
**Dependencies:** Stories 1-3

### Acceptance Criteria

- [ ] All existing tests in `TriviaViewModelTest` and `TriviaNoTieTest` continue to pass (updated as needed to accommodate the new `prepareRound()` signature or behavior)
- [ ] The mock JSON responses in tests include both `"points"` and `"goals"` keys where appropriate
- [ ] The no-tie invariant is tested for goals questions, not only points questions
- [ ] The independent per-type used-player tracking is tested with cross-type scenarios (a player used in a points round can still appear in a goals round)

### Design Notes

- No UI changes.

### Engineering Notes

- The existing `createStatsJson()` helper in tests builds JSON with only a `"points"` key. It needs to be extended (or a new helper added) to produce JSON with both `"points"` and `"goals"` keys.
- If randomization is made injectable (as recommended in Story 2), tests should use a deterministic random to control which question type is selected.
- The `TriviaNoTieTest` tests should be parameterized or duplicated for the goals question type to verify the same `distinctBy` behavior applies.

### QA / Testing Notes

- Run the full test suite after implementation. All tests green is the exit criterion.
- Verify test coverage includes: goals-only data, points-only data, both types present, per-type pool exhaustion, independent reset behavior.

### Edge Cases & Risk Analysis

- Tests that assume `prepareRound()` always draws from points may break if randomization selects goals. Using a controlled random eliminates this flakiness.
- The `TriviaViewModel` constructor currently takes `(OkHttpClient, String, CoroutineDispatcher)`. If a `Random` parameter is added for testability, existing test call sites need updating.

---

## Cross-Cutting Concerns

### Performance

- No additional API calls are needed. The existing single API call already returns all stat categories. Parsing one additional key adds negligible overhead.

### Data Model Impact

- `SkaterStatLeader` requires no changes. The `value` field is already generic enough to hold points or goals.
- The ViewModel gains new state: `questionText`, `statUnitLabel`, `pointsPool`, `goalsPool`, `pointsUsedIds`, `goalsUsedIds`, and a `Random` instance.

### Future Compatibility

- This architecture (per-type pools built from the top 50%, independent used-player tracking, per-type resets, and random question-type selection) naturally extends to additional stat types (assists, penalty minutes, etc.) with minimal incremental effort.
- If a third question type is added, the pool construction, used-set tracking, and reset logic follow the same per-type pattern. The random selection logic should be generalized (e.g., randomly choosing from all available stat types rather than a boolean coin flip).

### Assumptions

1. The NHL API at `https://api-web.nhle.com/v1/skater-stats-leaders/current?limit=-1` returns a `"goals"` key with the same player object structure as `"points"`.
2. The selection probability between points and goals is 50/50 (uniform random). No weighting is required.
3. The score mechanic (100 points for correct, reset to 0 for incorrect) is identical for both question types.
4. No visual distinction between question types beyond the question text and stat unit label is needed for this iteration.
