# Player Selection Pool Redesign -- Requirements Specification

**Date:** 2026-04-02
**Feature:** Redesign player pool construction, per-type used-player tracking, and round selection logic
**Status:** Draft
**Type:** Redesign of existing functionality

## Feature Overview

The Puck Trivia app currently uses a single shared `usedPlayerIds` set across both question types (points and goals), with a global reset triggered when either pool runs low. This design is overly coupled: exhausting one question type's player pool forces a premature reset of the other type's tracking, and prevents players who legitimately lead in both stats from appearing in both question categories.

This redesign introduces three changes: (1) pools are constructed by taking the top 50% of players per stat type rather than using the full API response, (2) each question type maintains its own independent used-player set, and (3) pool resets happen independently per question type based on whether that specific type can still form a valid 3-choice round.

### Definition of Done

Each question type (points, goals) has its own player pool built from the top 50% of that stat's leaderboard. Each type independently tracks which players have been used and resets only when it cannot form a valid 3-player round with distinct stat values. A player appearing in a points question does not prevent that same player from appearing in a goals question. All existing tests are updated to reflect the new behavior, and new tests cover the independent pool and reset logic.

### Assumptions

1. The NHL API response structure and the `fetchSkaterStats()` parsing logic are unchanged.
2. The `SkaterStatLeader` data class is unchanged.
3. The `Random` injection pattern for testability is unchanged.
4. Scoring logic (100 points correct, reset to 0 incorrect) is unchanged.
5. UI/visual design is unchanged.
6. The API endpoint only returns skater positions (C, L, R, D) -- no goalie filtering is needed.

---

## Story 1: Build Per-Type Player Pools from the Top 50% of Each Stat Leaderboard

**As a** trivia player,
**I want** each question type to draw from a curated pool of that stat's top performers,
**So that** questions feature meaningfully competitive players rather than drawing from the full (potentially very long) leaderboard.

**Story Points:** 3
**Priority:** P0
**Dependencies:** None

### Acceptance Criteria

- [ ] After data is fetched, a points player pool is constructed containing the top 50% of players from the points leaderboard, sorted by stat value descending, rounded up (e.g., 7 players yields a pool of 4)
- [ ] After data is fetched, a goals player pool is constructed containing the top 50% of players from the goals leaderboard, using the same top-50%-rounded-up rule
- [ ] Players with tied stat values are all included in their respective pool -- no deduplication by value occurs during pool construction
- [ ] The two pools are built independently; a player's inclusion in the points pool has no bearing on their inclusion in the goals pool
- [ ] If the goals leaderboard is absent from the API response, no goals pool is constructed and the app falls back to points-only questions (existing behavior preserved)
- [ ] Points questions draw choices exclusively from the points pool; goals questions draw exclusively from the goals pool

### Engineering Notes

- Pool construction should happen once per data fetch, not on every `prepareRound()` call. Store the computed pools as ViewModel state (e.g., `pointsPool: List<SkaterStatLeader>` and `goalsPool: List<SkaterStatLeader>`).
- The pool construction logic: take the full list from `statsData[key]`, sort by `value` descending, then take the first `ceil(size / 2.0).toInt()` entries. Do not apply `distinctBy { it.value }` during construction -- that filter only applies at round-selection time.
- The current code uses `statsData["points"]` directly in `prepareRound()`. Replace those references with the pre-computed pool lists.
- Example: API returns 20 points leaders. Pool = top 10. API returns 7 goals leaders. Pool = top 4.

### QA / Testing Notes

- Test with an even-sized list (e.g., 10 players): pool should contain 5.
- Test with an odd-sized list (e.g., 7 players): pool should contain 4 (ceil of 3.5).
- Test with a list of 1 player: pool should contain 1.
- Test with a list where all players have the same stat value: all should be in the pool (no dedup).
- Test that the pool is sorted descending by value.

### Edge Cases & Risk Analysis

- **Single player in leaderboard:** Pool contains 1 player. This is insufficient for a 3-choice round. The reset logic (Story 3) handles this -- the pool simply cannot produce a valid round. This is an existing limitation and outside the scope of this redesign.
- **All players tied at the same value:** All are included in the pool. Round selection (Story 2) will be unable to pick 3 with distinct values if there are fewer than 3 distinct values total. The reset logic handles this gracefully.
- **Player appears in both points and goals leaderboards:** They are independently included in both pools. This is the desired behavior.

---

## Story 2: Select 3 Players per Round with the No-Tie Invariant (Revised Algorithm)

**As a** trivia player,
**I want** each round's 3 choices to have distinct stat values,
**So that** there is always exactly one unambiguous correct answer.

**Story Points:** 3
**Priority:** P0
**Dependencies:** Story 1

### Acceptance Criteria

- [ ] Each round presents exactly 3 player choices
- [ ] No two choices in a single round share the same stat value
- [ ] The player with the highest stat value among the 3 choices is the correct answer
- [ ] Player selection is randomized: the same pool state does not always produce the same 3 choices (given non-deterministic random)
- [ ] Players whose IDs are in the current question type's used set are excluded from selection

### Design Notes

- No UI changes. The question screen continues to show 3 choices with the same layout as today.

### Engineering Notes

- Replace the current selection algorithm (`filter -> shuffled -> distinctBy -> take(3)`) with the specified approach: shuffle the unused players, then iterate and pick players whose value has not already been claimed by a previous pick in the current round. Stop after collecting 3.
- The current `distinctBy { it.value }.take(3)` approach is subtly different: it deduplicates the entire unused list by value first, then takes 3. The new approach shuffles first, then greedily picks, which means different players with the same value have a chance of being selected across different rounds (just not within the same round).
- Pseudocode for the new selection:
  ```
  val unused = pool.filter { it.id !in usedSet }.shuffled(random)
  val picked = mutableListOf<SkaterStatLeader>()
  val claimedValues = mutableSetOf<Double>()
  for (player in unused) {
      if (player.value !in claimedValues) {
          picked.add(player)
          claimedValues.add(player.value)
          if (picked.size == 3) break
      }
  }
  ```
- Use the injected `Random` instance for `shuffled(random)` to maintain testability.

### QA / Testing Notes

- Test the no-tie invariant across many rounds (run 50+ rounds in a loop test) to verify it holds under randomization.
- Test with a pool where multiple players share each value tier: verify that different players can appear across rounds even when they share a value.
- Test with a pool that has exactly 3 distinct values spread across many players: verify 3 choices are always produced.

### Edge Cases & Risk Analysis

- **Fewer than 3 distinct values in the unused portion of the pool:** The greedy pick will collect fewer than 3 players. This triggers the reset logic in Story 3.
- **`shuffled(random)` reproducibility:** Using the injected `Random` ensures tests can control selection order. Production uses `kotlin.random.Random` (system default), which is non-deterministic.

---

## Story 3: Independent Per-Type Used-Player Tracking and Pool Reset

**As a** trivia player,
**I want** each question type to independently track which players I have already seen,
**So that** I do not see the same player repeated in the same question category, while still allowing top players to appear in both points and goals questions.

**Story Points:** 5
**Priority:** P0
**Dependencies:** Stories 1, 2

### Acceptance Criteria

- [ ] Each question type (points, goals) maintains its own independent set of used player IDs
- [ ] A player who appeared in a points question may still appear in a subsequent goals question (and vice versa)
- [ ] A player who appeared in a goals question does not appear in another goals question until the goals used set resets; same rule applies symmetrically for points
- [ ] After a round's 3 players are selected, all 3 of their IDs are added to that question type's used set
- [ ] A question type's used set resets only when that specific type cannot produce a valid 3-choice round (i.e., fewer than 3 distinct stat values remain among unused players in that pool)
- [ ] After a reset, the 3 choices for that round are selected from the now-full pool using the same no-tie selection logic
- [ ] Resetting one type's used set does not affect the other type's used set

### Design Notes

- No UI changes. The `usedPlayerIds` state exposed to the UI can be removed or replaced if it is only used for debugging. If it is displayed anywhere, it should reflect the used set for the most recently played question type (or be removed -- confirm with product).

### Engineering Notes

- Replace the single `usedPlayerIds: Set<Int>` with two independent sets: one for points, one for goals. These can be stored as a `Map<String, Set<Int>>` keyed by stat type, or as two separate properties.
- The current global reset condition (`if (pointsUnusedCount < 3 || goalsUnusedCount < 3)`) must be replaced with per-type reset logic that fires only for the selected question type, only when needed.
- Reset detection algorithm for a given pool:
  1. Filter out used players from the pool.
  2. Attempt the greedy no-tie selection (Story 2 algorithm).
  3. If fewer than 3 players are picked, reset that type's used set to empty.
  4. Re-run the selection on the full pool.
- The reset check and selection can be combined into a single function to avoid running the selection algorithm twice in the common (non-reset) case. Only run it a second time if the first attempt yields fewer than 3.
- Suggested refactor of `prepareRound()`:
  ```
  1. Select question type (random boolean, fallback to points if goals unavailable)
  2. Get the pool for that type
  3. Attempt selection from unused players
  4. If result.size < 3, reset that type's used set, re-attempt from full pool
  5. Set choices, correctPlayer, add IDs to that type's used set
  ```
- The `usedPlayerIds` public property currently exists as Compose state. Decide whether to keep it (changing its semantics) or remove it. If tests rely on inspecting it, consider exposing per-type used sets or a combined view for test assertions.

### QA / Testing Notes

- **Cross-type independence test:** Set up a scenario where player X appears in both pools. Use player X in a points round. Verify player X can still appear in a subsequent goals round.
- **Same-type exclusion test:** Use player X in a goals round. Verify player X does not appear in any subsequent goals round until the goals pool resets.
- **Independent reset test:** Set up goals pool with exactly 3 distinct-value players and points pool with 6. Play one goals round (exhausting goals). Play a goals round again -- goals resets. Verify points used set is unaffected (still tracks its previously used players).
- **Reset timing test:** Set up a pool where after using 3 players, only 2 distinct values remain among unused players. Verify reset triggers on the next round for that type.
- **Example walkthrough test:** Replicate the exact scenario from the requirements:
  - Goals pool: Alice (20g), Bob (20g), Carol (20g), Dave (15g), Eve (10g), Frank (8g), Grace (5g)
  - Round 1 (goals): picks 3 with distinct values, e.g., one of {Alice,Bob,Carol} at 20g, Dave at 15g, Eve at 10g
  - Round 2 (goals): picks 3 more with distinct values from remaining unused
  - Round 3 (goals): only one unused player at value 20g remains, plus possibly Frank (8g) and Grace (5g) -- but need to check if 3 distinct values are achievable. If not, reset fires.

### Edge Cases & Risk Analysis

- **Both pools reset on the same round:** If by coincidence both pools are exhausted, and the randomly selected type triggers a reset, only that type resets. The other type will reset independently when it is next selected and found to be exhausted. This is correct behavior.
- **Pool with zero unused players:** After every player in a pool has been used, the next round for that type triggers a reset. The used set becomes empty and selection proceeds normally.
- **Interaction with question type randomization:** It is possible for one question type to be selected many times in a row by random chance, exhausting its pool while the other type's used set is nearly empty. This is acceptable -- each type manages itself independently.
- **`usedPlayerIds` public property removal:** If any UI code or test reads `usedPlayerIds`, removing it is a breaking change. Tests that assert on `usedPlayerIds` (e.g., `GoalsQuestionTypeTest.usedPlayerIds tracks players from both question types`) must be rewritten to assert on per-type used sets or on observable behavior (player not appearing in subsequent rounds of the same type).

---

## Story 4: Update Existing Tests for Redesigned Pool Logic

**As a** developer,
**I want** the existing test suite updated to reflect the new independent-pool behavior,
**So that** tests accurately verify the redesigned logic and do not encode the old shared-pool assumptions.

**Story Points:** 3
**Priority:** P0
**Dependencies:** Stories 1-3

### Acceptance Criteria

- [ ] All tests in `TriviaViewModelTest` pass against the redesigned implementation
- [ ] All tests in `TriviaNoTieTest` pass against the redesigned implementation
- [ ] Tests in `GoalsQuestionTypeTest` are updated: tests that assert shared-pool behavior (cross-type exclusion) are replaced with tests that assert independent-pool behavior (cross-type inclusion is allowed)
- [ ] Tests that assert on `usedPlayerIds` are updated to reflect whatever public API replaces or augments it
- [ ] No test relies on the old global reset behavior

### Engineering Notes

- **Tests that need semantic changes (not just mechanical fixes):**
  - `GoalsQuestionTypeTest.player from points question cannot appear in goals question` -- this test asserts the OLD behavior (cross-type exclusion). Under the new design, this overlap is explicitly allowed. Rewrite this test to assert the OPPOSITE: a player used in a points round CAN appear in a goals round.
  - `GoalsQuestionTypeTest.player from goals question cannot appear in points question` -- same issue. Rewrite to assert cross-type inclusion.
  - `GoalsQuestionTypeTest.usedPlayerIds tracks players from both question types` -- update to assert per-type tracking instead of a single combined set.
  - `GoalsQuestionTypeTest.global pool reset when neither type has enough unused players` -- rewrite to test independent reset: exhausting goals does not reset points.
  - `GoalsQuestionTypeTest.pool resets globally when either type runs out of players` -- rewrite to test that only the exhausted type resets.
  - `GoalsQuestionTypeTest.after global reset previously seen players may reappear` -- rewrite to test per-type reset and reappearance.
- **Tests that should pass with minimal or no changes:**
  - All `TriviaViewModelTest` tests -- these only test points-only scenarios with 5 players. The pool will be `ceil(5/2) = 3` players, which is exactly enough for one round. Behavior should be equivalent. However, if these tests do not pass an explicit `Random`, they may need updating depending on whether the constructor signature changes.
  - All `TriviaNoTieTest` tests -- these test points-only scenarios with small player sets. Pool construction (top 50%) will reduce the available players. Verify that each test's player list is large enough after the 50% cut to still exercise the intended scenario. Some tests use exactly 3 players; top 50% of 3 = 2, which is insufficient for a 3-choice round. These tests need their player lists expanded.
- **`TriviaNoTieTest` player list sizing issue (critical):**
  - `all choices must have distinct point values when all players have same points`: 3 players, all value 50.0. Top 50% = ceil(1.5) = 2 players. Cannot form 3 choices. Test will break. Fix: expand to 6 players (pool = 3) or adjust the test expectation.
  - `correct answer must not tie with any other choice`: 3 players. Same issue. Expand to 6+.
  - `non-correct choices must not tie with each other`: 3 players. Same issue.
  - `choices have distinct values when pool has many duplicates`: 3 players. Same issue.
  - For all of these, the test intent is to verify the no-tie invariant with duplicate values. Expanding the player lists (while preserving the duplicate-value patterns) preserves the test intent.

### QA / Testing Notes

- Run the full test suite after all changes. All tests green is the exit criterion.
- Verify that the updated `GoalsQuestionTypeTest` tests actually exercise the new behavior (independent pools) rather than being vacuously true.
- Consider adding a dedicated test that plays 10+ rounds alternating between question types and verifies that cross-type player reuse occurs when the same player is in both pools.

### Edge Cases & Risk Analysis

- **Test helper changes:** The `createStatsJson()` helpers in `TriviaViewModelTest` and `TriviaNoTieTest` produce points-only JSON. If pool construction requires at minimum N players to form a valid pool and round, these helpers may need to produce larger player lists.
- **Seed sensitivity:** Tests that use seeded `Random` for deterministic question type selection may need seed values re-evaluated if the number of `random.nextBoolean()` or `random.shuffled()` calls changes (since the new selection algorithm may consume random values differently).

---

## Cross-Cutting Concerns

### Performance

Pool construction (sort + take) runs once per data fetch on lists of at most ~100 players. Negligible cost. The per-round selection algorithm (shuffle + greedy pick) is O(n) where n is the pool size (~50). No performance concern.

### Data Model Impact

- `SkaterStatLeader` is unchanged.
- The ViewModel gains new state: two pool lists and two used-ID sets. The single `usedPlayerIds` property is removed or replaced.
- No changes to the API call, response parsing, or `statsData` map.

### State Exposure

The current `usedPlayerIds` is exposed as public Compose state. Options for the redesign:
1. **Remove it entirely** if nothing in the UI reads it. This is the cleanest option.
2. **Replace with per-type accessors** (e.g., `pointsUsedPlayerIds`, `goalsUsedPlayerIds`) if tests need to inspect internal state.
3. **Keep a combined view** for backward compatibility, but this is misleading under the new semantics.

Recommendation: expose per-type used sets as `@VisibleForTesting` internal properties. Do not expose them as public Compose state unless the UI needs them.

### Future Compatibility

- The pool construction logic (top 50%, independent per stat type) and per-type tracking generalize naturally to additional stat types (assists, penalty minutes, etc.). Adding a new type requires: adding its pool construction in the post-fetch step, adding a used-ID set, and extending the question-type random selection.
- The 50% threshold is currently hardcoded. If it needs to be tunable in the future, extracting it as a constant or constructor parameter is straightforward.
- The no-tie selection algorithm is generic over any stat type and does not need modification for new types.

### Migration Notes

This redesign replaces existing logic in `TriviaViewModel.prepareRound()` and the `usedPlayerIds` state. It is not additive -- the old shared-pool behavior must be fully removed. There is no need for a migration path or backward compatibility since this is an internal logic change with no persisted state.
