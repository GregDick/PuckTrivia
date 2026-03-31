# Most Goals Question Type — Requirements Specification

**Date:** 2026-03-31
**Feature:** Add a "most goals" question type alongside the existing "most points" question type
**Status:** Draft

## Feature Overview

The Puck Trivia app currently presents a single question format: "Which of these players currently has the most points?" This feature introduces a second question type that asks "Which of these players currently has the most goals?" Each round, the app randomly selects whether to present a points question or a goals question. Both question types draw from a single shared pool of used player IDs so that no player appears more than once across any question type until the pool is exhausted.

### Definition of Done

The app randomly presents either a "most points" or "most goals" question each round. Both question types display 3 choices with no tied stat values, show team abbreviations, reveal the correct stat value after answering, and share a single used-player tracking list that prevents repeat players across all question types.

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

- Introduce a concept to represent the current question type. This could be an enum (e.g., `QuestionType.POINTS`, `QuestionType.GOALS`) or a simple string matching the `statsData` map keys.
- The ViewModel needs to expose the current question type (or at minimum, the question text and stat unit label) so the UI can render the correct wording. Currently, the question text is hardcoded in `MainActivity.kt` — it should instead be driven by ViewModel state.
- `prepareRound()` currently hardcodes `statsData["points"]`. It needs to randomly select between `"points"` and `"goals"` and pull choices from the corresponding list.
- The `distinctBy { it.value }` filter in `prepareRound()` already enforces no ties. This logic should apply identically to goals selections.
- Randomization should be injectable/testable. Consider accepting a `Random` instance or a lambda so tests can control which question type is selected.

### QA / Testing Notes

- Unit test: with a seeded/controlled random, verify that when "goals" is selected, choices come from the goals list and the correct player has the highest goals value.
- Unit test: with a seeded/controlled random, verify that when "points" is selected, behavior is unchanged from the current implementation.
- Unit test: when `statsData` contains only "points" (no "goals"), verify that `prepareRound()` always selects points regardless of the random outcome.
- Manual test: play 20+ rounds and verify that both question types appear with reasonable frequency, and that the question text and stat labels match the question type.

### Edge Cases & Risk Analysis

- If the goals leaderboard has fewer than 3 players with distinct values after filtering out used players, `prepareRound()` must handle this. Options: fall back to a points question for that round, or reset the used-player pool. The existing pool-reset logic (when fewer than 3 players remain) should be extended to account for both pools.
- A player could appear in both the points and goals leaderboards. The shared used-player pool (Story 3) prevents the same player from appearing in a goals question after already appearing in a points question. However, the `distinctBy { it.value }` filter operates within one stat type, so a player's goals value being unique does not depend on their points value.

---

## Story 3: Shared Used-Player Pool Across Question Types

**As a** trivia player,
**I want** to never see the same player as a choice in two different rounds, regardless of whether those rounds are points questions or goals questions,
**So that** the game feels fair and does not repeat content.

**Story Points:** 3
**Priority:** P0
**Dependencies:** Story 2

### Acceptance Criteria

- [ ] A single set of used player IDs is maintained across both points and goals questions
- [ ] A player who appeared as a choice in a points question does not appear as a choice in any subsequent goals question (and vice versa) until the pool resets
- [ ] The used-player pool resets when neither question type has enough unused players with distinct stat values to form a valid 3-choice set
- [ ] After a pool reset, previously seen players may appear again in either question type

### Design Notes

- No UI changes. This is purely game logic.

### Engineering Notes

- The existing `usedPlayerIds` set in the ViewModel already tracks used players. This same set must be checked when drawing from either the points or goals player list.
- The pool-reset condition currently checks `pointsPlayers.size - currentUsed.size < 3`. This needs to become smarter: the reset should trigger when the currently selected question type's pool cannot produce 3 distinct-value unused players. Alternatively, reset when both pools are exhausted.
- Consider the reset strategy carefully. Two approaches:
  1. **Per-type check:** If the selected question type cannot produce 3 valid choices, try the other type. If neither can, reset the pool and retry.
  2. **Global check:** Reset the entire pool when either type falls below threshold. This is simpler but resets more aggressively.
- Approach 1 is recommended because it maximizes the number of unique rounds before a reset.
- Since players can appear in both stat leaderboards, marking a player as used after a points question correctly prevents them from appearing in a goals question. This is the desired behavior.

### QA / Testing Notes

- Unit test: provide a mock response where the points list has players A, B, C, D and the goals list has players C, D, E, F. After a points round uses A, B, C, verify that a goals round cannot select C (it is in `usedPlayerIds`).
- Unit test: exhaust all available unique players across both lists and verify the pool resets, allowing players to reappear.
- Unit test: provide a scenario where the goals pool is exhausted but the points pool still has players. Verify that goals questions fall back to points (or the round selects a points question) rather than crashing.

### Edge Cases & Risk Analysis

- **Asymmetric pool sizes:** The goals leaderboard and points leaderboard may have different numbers of players. One pool could exhaust much faster than the other, causing that question type to always fall back or force resets.
- **Overlap between pools:** Many top scorers also lead in goals. Heavy overlap means the shared used-player pool depletes both lists faster than expected.
- **Pool reset with a selected question type:** If a goals round triggers a pool reset, the round should still present a goals question (using the now-reset pool), not silently switch to points.

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
- [ ] The shared used-player pool is tested with at least one cross-type scenario (a player used in a points round is excluded from a goals round)

### Design Notes

- No UI changes.

### Engineering Notes

- The existing `createStatsJson()` helper in tests builds JSON with only a `"points"` key. It needs to be extended (or a new helper added) to produce JSON with both `"points"` and `"goals"` keys.
- If randomization is made injectable (as recommended in Story 2), tests should use a deterministic random to control which question type is selected.
- The `TriviaNoTieTest` tests should be parameterized or duplicated for the goals question type to verify the same `distinctBy` behavior applies.

### QA / Testing Notes

- Run the full test suite after implementation. All tests green is the exit criterion.
- Verify test coverage includes: goals-only data, points-only data, both types present, pool exhaustion for one type, pool exhaustion for both types.

### Edge Cases & Risk Analysis

- Tests that assume `prepareRound()` always draws from points may break if randomization selects goals. Using a controlled random eliminates this flakiness.
- The `TriviaViewModel` constructor currently takes `(OkHttpClient, String, CoroutineDispatcher)`. If a `Random` parameter is added for testability, existing test call sites need updating.

---

## Cross-Cutting Concerns

### Performance

- No additional API calls are needed. The existing single API call already returns all stat categories. Parsing one additional key adds negligible overhead.

### Data Model Impact

- `SkaterStatLeader` requires no changes. The `value` field is already generic enough to hold points or goals.
- The ViewModel gains minimal new state: the current question type (an enum or string) and potentially a `Random` instance.

### Future Compatibility

- This architecture (a `statsData` map keyed by stat type, a shared used-player pool, and random question-type selection) naturally extends to additional stat types (assists, penalty minutes, etc.) with minimal incremental effort.
- If a third question type is added, the random selection logic, pool-reset logic, and fallback logic should be reviewed for generalization (e.g., iterating over all available stat types rather than hardcoding two).

### Assumptions

1. The NHL API at `https://api-web.nhle.com/v1/skater-stats-leaders/current?limit=-1` returns a `"goals"` key with the same player object structure as `"points"`.
2. The selection probability between points and goals is 50/50 (uniform random). No weighting is required.
3. The score mechanic (100 points for correct, reset to 0 for incorrect) is identical for both question types.
4. No visual distinction between question types beyond the question text and stat unit label is needed for this iteration.
