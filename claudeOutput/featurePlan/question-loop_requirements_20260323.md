# Feature: Trivia Question Loop

## Feature Overview

After a user answers a trivia question and sees the correct/incorrect result, the game automatically presents a new question using three different players that have not yet appeared in the current session. The question text remains the same ("Which of these players currently has the most points?"), but each round draws from the pool of unused players so the user never sees a repeated player until the pool is exhausted. When there are fewer than three unused players remaining, the pool resets and all players become available again. There is no end-game screen, score summary, or session termination -- the loop continues indefinitely.

**Definition of Done:** After answering a trivia question, the user can tap a "Next" button to advance to a new question with three previously-unseen players. Players do not repeat across rounds until the available pool is exhausted, at which point the pool silently resets and the loop continues.

---

## Story 1: Advance to Next Question After Answering

**As a** trivia player,
**I want to** move on to a new question after I see whether my answer was correct,
**So that** I can keep playing without restarting the app.

**Story Points:** 3
**Priority:** P0
**Dependencies:** None (builds on the existing `TriviaQuestionScreen` implementation)

### Acceptance Criteria

- [ ] After the user selects an answer and the correct/incorrect result is displayed, a "Next" button appears on screen.
- [ ] The "Next" button is not visible before the user has answered the current question.
- [ ] Tapping "Next" replaces the current question with a new question that shows three different player names on the answer buttons.
- [ ] The question text ("Which of these players currently has the most points?") remains the same on every round.
- [ ] The correct answer for each new round is whichever of the three newly-selected players has the highest `value` in the points category.
- [ ] The three answer buttons are in a randomized order each round so the correct answer does not consistently appear in the same position.
- [ ] After tapping "Next", the result feedback ("Correct!" / "Incorrect!") from the previous round is no longer visible and the answer buttons are re-enabled for the new question.

### Design Notes

- Place the "Next" button below the answer buttons, visually separated with sufficient spacing (e.g., 24dp top margin).
- The "Next" button should use a distinct visual treatment from the answer buttons so it is not confused with an answer choice. Consider using `OutlinedButton` or a secondary/tertiary color from the Material 3 theme while the answer buttons use filled `Button`.
- The "Next" button should be full-width to match the answer button layout, or centered with a narrower width -- either is acceptable as long as it is clearly tappable.

### Engineering Notes

- Introduce a round counter or key (e.g., `roundNumber: Int`) in a `remember`/`mutableStateOf` variable. Incrementing this value on "Next" tap triggers recomposition with new data.
- Reset `selectedPlayerId` to `null` when advancing to the next round.
- The three players for each round should be derived using `remember` keyed on the round number (e.g., `remember(roundNumber) { ... }`) so that player selection is stable within a round but recalculated when the round advances.
- This story does not yet require tracking which players have been used. Story 2 adds the non-repeat constraint. For this story, selecting three random players from the full pool each round is acceptable.

### QA / Testing Notes

- Answer a question correctly, verify "Next" appears, tap it, and confirm a new set of three players is displayed with buttons re-enabled and no residual feedback text.
- Answer a question incorrectly, verify "Next" appears, tap it, and confirm the same fresh-round behavior.
- Tap "Next" rapidly multiple times in succession and verify the app does not crash or display a blank/corrupt state.
- Verify the "Next" button is never visible before an answer is selected.

### Edge Cases

- **Rapid tap on "Next":** The round counter increments by 1 per tap. Even if tapped rapidly, each tap should produce a valid new round. No debounce is required as long as state updates are atomic via Compose state.
- **Configuration change (rotation) mid-round:** Consistent with the existing behavior, rotation will reset activity state and re-randomize the question. This is acceptable for this iteration.

---

## Story 2: Track Used Players and Prevent Repeats Across Rounds

**As a** trivia player,
**I want to** see different players each round,
**So that** the game feels varied and I am tested on a wider range of NHL players.

**Story Points:** 3
**Priority:** P0
**Dependencies:** Story 1

### Acceptance Criteria

- [ ] Each round's three players are different from all players shown in every previous round of the current session.
- [ ] A player who appeared as an answer choice in round N does not appear again in any subsequent round until a pool reset occurs.
- [ ] This applies to all three players per round, not just the correct answer -- all choices count as "used."

### Design Notes

- No visual changes from Story 1. The user does not see any indication of pool tracking; the behavior is implicit.

### Engineering Notes

- Maintain a `usedPlayerIds: MutableSet<Int>` (or `SnapshotStateList`) that accumulates the IDs of all players shown across rounds.
- When selecting players for a new round, filter the full `pointsPlayers` list to exclude any player whose `id` is in `usedPlayerIds`, then shuffle and take 3.
- After selecting the three players for a round, add their IDs to `usedPlayerIds`.
- The `usedPlayerIds` set should be held in a `remember { mutableStateOf(...) }` or `remember { mutableStateListOf(...) }` so it survives recomposition but resets on activity recreation.
- Key the player selection on both `roundNumber` and `usedPlayerIds` size (or just `roundNumber`, since `usedPlayerIds` is updated before the round increments).

### QA / Testing Notes

- Play through multiple rounds and record every player name shown. Verify no player name appears more than once until a pool reset occurs.
- If the points data contains N players, verify that across ceil(N/3) rounds, all N players appear (assuming N is divisible by 3; otherwise the last partial group triggers a reset per Story 3).
- Test with awareness of the actual API data size. The NHL stats endpoint with `limit=-1` typically returns a large number of players (50+), so many rounds can be played before exhaustion.

### Edge Cases

- **Players with identical names but different IDs:** Tracking is by `id`, not by name. Two players who happen to share a name are treated as distinct. Both may appear across different rounds, and theoretically in the same round. This is correct behavior.

---

## Story 3: Reset Player Pool When Exhausted

**As a** trivia player,
**I want** the game to keep going even after all players have been shown,
**So that** I can play indefinitely without the game stopping.

**Story Points:** 2
**Priority:** P0
**Dependencies:** Story 2

### Acceptance Criteria

- [ ] When there are fewer than three unused players remaining in the pool, the pool resets: all players become available again.
- [ ] After a pool reset, the next round draws three players from the full pool (players from recent rounds may reappear).
- [ ] The pool reset happens silently with no message, animation, or interruption to the user's experience.
- [ ] The game never displays a "game over," "no more questions," or similar end state.

### Design Notes

- No visual changes. The reset is invisible to the user.

### Engineering Notes

- Before selecting players for a new round, check if the number of available (unused) players is less than 3. If so, clear `usedPlayerIds` to reset the pool.
- The reset check should happen before the filter-and-select logic so that the round always has a full pool to draw from when needed.
- Implementation is a simple conditional clear:
  ```
  if (pointsPlayers.size - usedPlayerIds.size < 3) {
      usedPlayerIds.clear()
  }
  ```
- After clearing, the normal selection logic proceeds: filter by `usedPlayerIds` (now empty), shuffle, take 3.

### QA / Testing Notes

- To test exhaustion without playing dozens of rounds, temporarily reduce the player pool size in a debug build (e.g., mock `statsData["points"]` with only 6-9 players) and verify the reset occurs correctly.
- With a pool of exactly 6 players: round 1 uses 3, round 2 uses 3, round 3 should reset and draw from all 6 again. Verify this sequence.
- With a pool of 7 players: round 1 uses 3, round 2 uses 3, round 3 has only 1 unused player remaining (fewer than 3), so pool resets. Round 3 draws from all 7. Verify no crash and three players are shown.
- With a pool of exactly 3 players: round 1 uses all 3, round 2 triggers a reset, draws from all 3 again. The user sees the same three players in a different order. Verify this works.
- Verify the game never shows an error, empty state, or end screen regardless of how many rounds are played.

### Edge Cases

- **Pool of fewer than 3 total players:** If the API returns a points category with fewer than 3 players, the existing guard in `TriviaQuestionScreen` handles this (displays "Unable to load question"). The loop feature does not need to handle this case differently -- the screen would never reach the loop because the initial question cannot be displayed with fewer than 3 choices. However, as a defensive measure, the reset logic should not enter an infinite loop. If the total pool size is less than 3, the "Unable to load question" fallback already prevents the loop from running.
- **Pool of exactly 3 players with reset:** The same 3 players repeat every round. The order is re-randomized each time. This is acceptable behavior.
- **Data changes if API is re-fetched:** Currently the app fetches data once on launch. If in the future data is re-fetched, `usedPlayerIds` could reference stale IDs. For this iteration, this is not a concern since the data is fetched once and held in memory.
