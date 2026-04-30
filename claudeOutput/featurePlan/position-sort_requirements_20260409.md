# Position-Based Question Types — Requirements Specification

**Date:** 2026-04-09
**Feature:** Split question types by position group (defenders vs. forwards) for more challenging questions
**Status:** Draft
**Type:** Redesign of existing functionality

## Feature Overview

The Puck Trivia app currently has two question types: "most points" and "most goals," each drawing from a single pool of all skaters. Because forwards naturally have higher average points and goals than defenders, the answer is often simply "pick the forward." This makes many questions trivially easy.

This feature splits the two existing question types into four by grouping players by position before building pools. The four question types are:

1. "Which of these **defenders** currently has the most **points**?"
2. "Which of these **forwards** currently has the most **points**?"
3. "Which of these **defenders** currently has the most **goals**?"
4. "Which of these **forwards** currently has the most **goals**?"

By presenting choices who all play the same position group, the positional advantage is removed and the player must rely on knowledge of individual player performance.

### Position Group Definitions

- **Defenders:** players whose `position` field equals `"D"`
- **Forwards:** players whose `position` field equals `"C"`, `"L"`, or `"R"`

### Definition of Done

The app presents four question types, each drawing from its own position-filtered pool built from the top 50% of that group+stat combination. Each of the four pools independently tracks used players and resets independently. Question type selection is uniform random across all available pools. All existing tests are updated and new tests cover the position-based pool construction and four-type selection logic.

### Assumptions

1. The NHL API response structure and the `fetchSkaterStats()` parsing logic are unchanged.
2. The `SkaterStatLeader` data class is unchanged (it already has a `position` field).
3. The `Random` injection pattern for testability is unchanged.
4. Scoring logic (100 points correct, lose a life on incorrect) is unchanged.
5. UI layout and visual design are unchanged beyond question text.
6. The API returns skater positions as `C`, `L`, `R`, `D` (not `LW`/`RW`). No goalie filtering is needed — goalies are not included in the skater stats leaders endpoint.
7. The top-50% pool construction rule, greedy no-tie pick algorithm, and independent per-type reset logic all carry forward unchanged — the only change is **what goes into each pool** and **how many pools there are**.

---

## Story 1: Define Position Groups and Filter Players by Position

**As a** developer building the trivia engine,
**I want** a clear, testable mapping from player positions to position groups (defenders, forwards),
**So that** pool construction can filter players by group before applying the top-50% cut.

**Story Points:** 2
**Priority:** P0
**Dependencies:** None

### Acceptance Criteria

- [ ] Players with `position = "D"` are classified as defenders
- [ ] Players with `position = "C"`, `"L"`, or `"R"` are classified as forwards
- [ ] No player is classified into both groups
- [ ] If a player has a position value not in `{"D", "C", "L", "R"}` (e.g., an unexpected API change), that player is excluded from all pools (fail-safe: do not crash, just skip)
- [ ] The position group classification is available as a utility that can be unit-tested independently of pool construction

### Engineering Notes

- Consider an enum or sealed class for position groups, e.g.:
  ```kotlin
  enum class PositionGroup { DEFENDERS, FORWARDS }

  fun SkaterStatLeader.positionGroup(): PositionGroup? = when (position) {
      "D" -> PositionGroup.DEFENDERS
      "C", "L", "R" -> PositionGroup.FORWARDS
      else -> null
  }
  ```
- The `else -> null` case is defensive. The NHL API currently only returns `C`, `LW`, `RW`, `D` for skaters, but a null return lets pool construction safely skip unknown positions.

### QA / Testing Notes

- Test each known position string maps to the correct group.
- Test an unknown position string (e.g., `"G"`, `"X"`) returns null / is excluded.
- Test that `"L"` and `"R"` both map to forwards (not just `"L"` and `"R"` — verify exact string matching).

### Edge Cases & Risk Analysis

- **API returns `"L"` or `"R"` instead of `"LW"` or `"RW"`:** The current `SkaterStatLeader` stores the raw position string from the API. If the API changes its abbreviation scheme, the position group mapping would need updating. This is low risk — the API has used these abbreviations consistently.
- **All players in a stat leaderboard are the same position:** One position group's pool for that stat would be empty. This is handled in Story 3 (pool availability check).

---

## Story 2: Build Four Position-Filtered Player Pools

**As a** trivia player,
**I want** each question type to draw from players of the same position group,
**So that** I cannot use positional heuristics to guess the answer.

**Story Points:** 5
**Priority:** P0
**Dependencies:** Story 1

### Acceptance Criteria

- [ ] After data is fetched, **four** player pools are constructed:
  1. **Defenders Points Pool:** top 50% of defenders from the points leaderboard, sorted by points descending
  2. **Forwards Points Pool:** top 50% of forwards from the points leaderboard, sorted by points descending
  3. **Defenders Goals Pool:** top 50% of defenders from the goals leaderboard, sorted by goals descending
  4. **Forwards Goals Pool:** top 50% of forwards from the goals leaderboard, sorted by goals descending
- [ ] The top-50% rule uses ceiling division (same as current: `ceil(groupSize / 2.0).toInt()`) applied to each group independently
- [ ] Players with tied stat values are all included in their respective pool (no dedup during construction)
- [ ] A player who appears in the points leaderboard as a defender is included in the defenders points pool. The same player appearing in the goals leaderboard is included in the defenders goals pool. Pool membership is determined independently per stat type.
- [ ] If the goals leaderboard is absent from the API response, only the two points pools are constructed
- [ ] If a position group has zero players in a stat leaderboard (e.g., no defenders in the goals leaderboard), that pool is not constructed and its question type is unavailable

### Engineering Notes

- Replace the current `pointsPool` / `goalsPool` pair with a more general structure. Options:
  - A `Map<QuestionType, List<SkaterStatLeader>>` where `QuestionType` is an enum of the four types
  - Four named properties (simple but verbose)
  - Recommendation: use a map keyed by a `QuestionType` enum for easy iteration and extensibility:
    ```kotlin
    enum class QuestionType(val statKey: String, val positionGroup: PositionGroup, val questionText: String, val unitLabel: String) {
        DEFENDERS_POINTS("points", PositionGroup.DEFENDERS, "Which of these defenders currently has the most points?", "pts"),
        FORWARDS_POINTS("points", PositionGroup.FORWARDS, "Which of these forwards currently has the most points?", "pts"),
        DEFENDERS_GOALS("goals", PositionGroup.DEFENDERS, "Which of these defenders currently has the most goals?", "g"),
        FORWARDS_GOALS("goals", PositionGroup.FORWARDS, "Which of these forwards currently has the most goals?", "g"),
    }
    ```
- The `buildPools()` function should be refactored:
  ```
  for each stat key ("points", "goals"):
      if data[statKey] is null, skip
      partition players by position group
      for each position group:
          sort group by value descending
          take top 50% (ceiling)
          store as pools[QuestionType(statKey, group)]
  ```
- Similarly, replace `pointsUsedIds` / `goalsUsedIds` with a `Map<QuestionType, Set<Int>>` for per-type used-player tracking.

### QA / Testing Notes

- Test with a mixed-position points leaderboard (e.g., 10 defenders, 10 forwards): verify defenders points pool has 5, forwards points pool has 5.
- Test with an asymmetric distribution (e.g., 3 defenders, 17 forwards in goals): verify defenders goals pool has 2, forwards goals pool has 9.
- Test with all defenders and no forwards in a stat: verify forwards pool for that stat is empty/null and defenders pool is correctly sized.
- Test with a single defender in a stat leaderboard: pool has 1 player. Insufficient for a round, but pool construction should still succeed.
- Test that the same player (e.g., a defender with ID 1 who appears in both points and goals leaderboards) ends up in both the defenders points pool and the defenders goals pool.

### Edge Cases & Risk Analysis

- **Very small position groups:** If only 2 defenders appear in the goals leaderboard, the pool after top-50% is 1 player. This is insufficient for a 3-choice round. The existing reset/skip logic (Story 3) handles this — the question type is effectively unavailable. No special handling needed during pool construction.
- **Position group entirely absent from a stat:** If no defenders appear in the goals leaderboard (unlikely but possible), that pool is empty. The question type should be excluded from random selection.
- **Performance:** Four pools instead of two. Each pool is at most ~50 players. No performance concern.

---

## Story 3: Select Question Type from Available Pools

**As a** trivia player,
**I want** each round to randomly select from all available question types,
**So that** I encounter a variety of position-based questions.

**Story Points:** 3
**Priority:** P0
**Dependencies:** Story 2

### Acceptance Criteria

- [ ] Each round, the app selects a question type uniformly at random from all question types that have a non-empty pool
- [ ] When a defenders points question is selected, the question text reads: "Which of these defenders currently has the most points?"
- [ ] When a forwards points question is selected, the question text reads: "Which of these forwards currently has the most points?"
- [ ] When a defenders goals question is selected, the question text reads: "Which of these defenders currently has the most goals?"
- [ ] When a forwards goals question is selected, the question text reads: "Which of these forwards currently has the most goals?"
- [ ] The stat unit label is "pts" for points questions and "g" for goals questions (unchanged per stat type)
- [ ] If goals data is unavailable, only the two points question types (defenders points, forwards points) are available for selection
- [ ] If a position group has an empty pool for a stat type, that question type is excluded from selection
- [ ] All other round mechanics (3 choices, no-tie invariant, correct answer = highest value, greedy pick, per-type used-set tracking, per-type reset) operate identically to today — they just operate on the selected pool

### Engineering Notes

- Replace the current `random.nextBoolean()` (binary choice) with `random.nextInt(availableTypes.size)` to select from the list of available question types.
- The `prepareRound()` function changes:
  ```
  1. Determine available question types = pools.keys.filter { pools[it]!!.isNotEmpty() }
  2. Select one at random
  3. Set questionText and statUnitLabel from the selected QuestionType enum
  4. Get the pool for that type
  5. Attempt greedy pick from unused players (same algorithm)
  6. If <3, reset that type's used set, retry (same logic)
  7. Update that type's used set
  ```
- The question text and unit label are now driven by the `QuestionType` enum rather than hardcoded strings in `prepareRound()`.

### QA / Testing Notes

- Test with all four pools available: verify that over many rounds, all four question types are selected (statistical test or controlled random).
- Test with goals data absent: verify only defenders-points and forwards-points are ever selected.
- Test with a controlled random that always selects a specific type: verify the correct pool is used and the correct question text is displayed.
- Test that each question type's choices are all from the correct position group (e.g., a defenders-goals question never includes a forward).

### Edge Cases & Risk Analysis

- **Only one pool available:** If goals data is missing and all points leaders are forwards, only forwards-points is available. The app selects it every round. This is correct behavior — no crash, just less variety.
- **Zero pools available:** If `statsData` is empty or all pools are empty after filtering, `prepareRound()` should return early (same as the current `if (pPool.isEmpty()) return` guard). This is an existing edge case.

---

## Story 4: Independent Per-Type Used-Player Tracking for Four Pools

**As a** trivia player,
**I want** each of the four question types to independently track which players I have seen,
**So that** I do not see repeat players within a question type, while the same player can appear across different question types.

**Story Points:** 3
**Priority:** P0
**Dependencies:** Stories 2, 3

### Acceptance Criteria

- [ ] Each of the four question types maintains its own independent set of used player IDs
- [ ] A player who appeared in a defenders-points question may still appear in a forwards-points, defenders-goals, or forwards-goals question
- [ ] A player who appeared in a defenders-goals question does not appear in another defenders-goals question until that type's used set resets
- [ ] After a round's 3 players are selected, all 3 IDs are added to that question type's used set only
- [ ] A question type's used set resets only when that specific type cannot produce a valid 3-choice round (greedy pick returns fewer than 3)
- [ ] After a reset, the round's choices are selected from the full pool for that type
- [ ] Resetting one type's used set does not affect any other type's used set
- [ ] On `resetGame()`, all four used sets are cleared

### Engineering Notes

- This is a direct generalization of the current two-set tracking to four sets. If using a `Map<QuestionType, Set<Int>>`, the logic is identical — just keyed by the selected `QuestionType` instead of a boolean.
- `resetGame()` currently clears `pointsUsedIds` and `goalsUsedIds`. Update to clear all entries in the used-set map.

### QA / Testing Notes

- **Cross-type reuse test:** Player X (a defender) appears in both defenders-points and defenders-goals pools. Use X in a defenders-points round. Verify X can still appear in a defenders-goals round.
- **Same-type exclusion test:** Use player X in a defenders-goals round. Verify X does not appear in another defenders-goals round until that type resets.
- **Independent reset test:** Exhaust the defenders-goals pool (small pool). Verify only defenders-goals used set resets; the other three are unaffected.
- **Reset game test:** Play several rounds across all types, accumulating used IDs. Call `resetGame()`. Verify all four used sets are empty.

### Edge Cases & Risk Analysis

- **Player in multiple pools:** A defender who appears in both the points and goals leaderboards will be in both the defenders-points and defenders-goals pools. Independent tracking ensures using them in one type does not block the other.
- **Asymmetric pool exhaustion:** Small pools (e.g., defenders-goals with 4 players) will reset more frequently than large pools. This is expected and correct.

---

## Story 5: Update Existing Tests for Four Question Types

**As a** developer,
**I want** the existing test suite updated to reflect the four-pool architecture,
**So that** tests accurately verify the new behavior and do not encode the old two-pool assumptions.

**Story Points:** 5
**Priority:** P0
**Dependencies:** Stories 1-4

### Acceptance Criteria

- [ ] All tests in `TriviaViewModelTest` pass against the redesigned implementation
- [ ] All tests in `TriviaNoTieTest` pass against the redesigned implementation
- [ ] All tests in `GoalsQuestionTypeTest` pass against the redesigned implementation (with tests updated to reflect four types)
- [ ] New tests verify:
  - Position group classification for all known positions
  - Pool construction filters by position before applying top-50%
  - All four question types can be selected and produce correct question text
  - Choices in a defenders question are all defenders; choices in a forwards question are all forwards
  - Per-type used-set tracking works independently across four types
  - Per-type reset works independently across four types
- [ ] No test relies on the old two-pool behavior

### Engineering Notes

- **Test data construction:** The `createStatsJson()` helpers need to produce player lists with a mix of positions. Currently, test data may not include position variation. Each test helper should include a realistic mix of `"D"`, `"C"`, `"L"`, `"R"` positions.
- **Pool size after position filtering:** Tests that currently use small player lists (e.g., 5 players) may need larger lists to ensure each position group has enough players after the top-50% cut to form a valid 3-choice round. For example, a test with 5 players who are all forwards would produce an empty defenders pool.
  - Recommendation: use at least 6 defenders and 6 forwards per stat type in test data (yields pools of 3 each, just enough for one round).
- **Controlled random for question type selection:** Tests that use a seeded `Random` to control question type selection will need updated seeds or a different approach, since selection now uses `random.nextInt(N)` instead of `random.nextBoolean()`. Consider either:
  - Adjusting seeds to produce desired selection sequences
  - Mocking the random to return specific values
  - Testing each question type explicitly by setting up data where only one type is available
- **Existing `GoalsQuestionTypeTest` changes:**
  - Tests asserting two-type behavior need to be generalized to four types
  - Tests asserting shared-pool behavior between points and goals are already testing independent behavior — these should still pass with the position split, but may need updated pool references

### QA / Testing Notes

- Run the full test suite after all changes. All tests green is the exit criterion.
- Verify that position filtering tests are not vacuously true (e.g., a test that checks "defenders pool contains only defenders" should use mixed-position input data).
- Add a test that plays 20+ rounds with all four types available and verifies that choices always match the expected position group.

### Edge Cases & Risk Analysis

- **Seed sensitivity:** The transition from `nextBoolean()` to `nextInt(N)` changes how the random sequence is consumed. Any test relying on a specific seed to produce a specific question type sequence will break. Fix by recalculating seeds or using a more robust approach to control question type in tests.
- **Test data sizing:** If test helpers produce too few players per position group, pools after the 50% cut may be too small for a round. This will cause tests to fail or behave unexpectedly. Ensure test data is sized with the position split in mind.

---

## Cross-Cutting Concerns

### Performance

Pool construction now involves a position filter step before the sort and top-50% cut. This adds negligible overhead — the NHL API returns ~100 skaters per stat type, and the filter is O(n).

### Data Model Impact

- `SkaterStatLeader` is unchanged (the `position` field is already present).
- New types: `PositionGroup` enum, `QuestionType` enum.
- The ViewModel replaces `pointsPool` / `goalsPool` with a `Map<QuestionType, List<SkaterStatLeader>>` and replaces `pointsUsedIds` / `goalsUsedIds` with a `Map<QuestionType, Set<Int>>`.
- The `questionText` and `statUnitLabel` state properties are unchanged in type but now driven by the `QuestionType` enum.

### State Exposure

The current `pointsUsedIds` and `goalsUsedIds` are exposed as `internal set` properties. Under the new design, these should be replaced with a single `usedIds: Map<QuestionType, Set<Int>>` property (or per-type accessors if preferred). Exposed as `internal` for test inspection.

### UI Impact

No layout or visual design changes. The only user-visible change is the question text, which now specifies the position group (e.g., "defenders" or "forwards") in addition to the stat type.

### Future Compatibility

- The `QuestionType` enum + map-based pool/tracking architecture makes adding new dimensions straightforward. For example, adding "most assists" would mean adding `DEFENDERS_ASSISTS` and `FORWARDS_ASSISTS` to the enum — no structural changes needed.
- If additional position groups are ever needed (e.g., separating centers from wingers), the `PositionGroup` enum and classification function are the only things that change.
- The pool construction, selection, used-tracking, and reset logic are all fully generic over `QuestionType`.

### Migration Notes

This is a non-additive redesign of `buildPools()` and `prepareRound()`. The old two-pool structure (`pointsPool`, `goalsPool`, `pointsUsedIds`, `goalsUsedIds`) is fully replaced. There is no persisted state to migrate.
