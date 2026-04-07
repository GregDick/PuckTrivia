# Feature: Lives System and Game Over

## Feature Overview

Give the player 3 lives per game. Each incorrect answer costs one life. When all lives are lost, the trivia screen transitions to a game-over screen showing the player's final score and accuracy (correct answers out of total questions answered). A "Play Again" button on the game-over screen starts a fresh game. This feature replaces the current infinite-loop gameplay with a finite game session that has a clear end condition.

**Behavior change from existing system:** Currently, an incorrect answer resets the score to 0 and the game continues indefinitely. With the lives system, a wrong answer no longer resets the score to 0. Instead, a wrong answer only costs one life; the score remains unchanged. The score resets to 0 only when the player starts a new game via "Play Again". After 3 incorrect answers the game ends.

**Definition of Done:** The player sees 3 lives at the start of a game, loses a life on each wrong answer (score is unaffected by wrong answers), and sees the final wrong answer's feedback before being taken to a game-over screen. The game-over screen shows the player's accumulated score, accuracy (e.g., "7 / 10 correct"), and a "Play Again" button that starts a fresh game with 3 lives, score at 0, and new questions.

---

## Story 1: Display Lives on the Trivia Screen

**As a** trivia player,
**I want to** see how many lives I have remaining,
**So that** I know how many mistakes I can afford before the game ends.

**Story Points:** 2
**Priority:** P0
**Dependencies:** None

### Acceptance Criteria

- [ ] The player sees 3 lives displayed on the trivia screen at the start of a new game.
- [ ] Lives are displayed as a count (e.g., "Lives: 3") and remain visible at all times during gameplay.
- [ ] Correct answers do not change the number of lives displayed.
- [ ] Advancing to the next round via the "Next" button does not change the number of lives displayed.

### Design Notes

- Place the lives display in the top area of the trivia screen, near the existing score label. The lives and score should both be visible without scrolling.
- Use `MaterialTheme.typography.titleMedium` to match the score label's visual weight.
- Ensure the lives label does not overlap with or crowd the score label. A horizontal row with score on the left and lives on the right (or vice versa) is one reasonable layout. Stacking them vertically is also acceptable.
- Use the default `MaterialTheme.colorScheme.onBackground` color for the lives text.

### Engineering Notes

- Add a `lives` state property to `TriviaViewModel`, initialized to 3.
- Pass `lives` as a parameter to `TriviaQuestionScreen` and display it in the existing top-area layout alongside the score.

### QA / Testing Notes

- Verify lives display shows "Lives: 3" on first app launch after data loads.
- Verify the lives display does not flicker or disappear during round transitions (tapping "Next").
- Verify correct answers leave the lives count unchanged across multiple consecutive correct rounds.

### Edge Cases & Risk Analysis

- **Data load failure:** If the app shows the "Failed to load data" or "Unable to load question" fallback, the lives display should not appear since there is no active game.
- **Configuration change:** Lives state in the ViewModel survives configuration changes (rotation), which is correct and consistent with how score already behaves.

---

## Story 2: Lose a Life on Wrong Answer

**As a** trivia player,
**I want to** lose a life when I answer incorrectly,
**So that** there are consequences for wrong answers and the game has a clear end condition.

**Story Points:** 2
**Priority:** P0
**Dependencies:** Story 1

### Acceptance Criteria

- [ ] When the player selects an incorrect answer, the displayed life count decreases by 1.
- [ ] The life count update is visible immediately after selecting the wrong answer, at the same time as the "Incorrect!" feedback text -- without needing to tap "Next".
- [ ] When the player selects an incorrect answer, the displayed score remains unchanged (wrong answers no longer reset the score to 0).
- [ ] A player who answers incorrectly 3 times across the game session has 0 lives displayed.
- [ ] Lives never go below 0.
- [ ] Correct answers do not affect the life count.

### Design Notes

- When a life is lost, briefly display the lives text in the same error color used for "Incorrect!" feedback (`MaterialTheme.colorScheme.error`). The color reverts to default when the player advances to the next round or the game-over screen appears.
- No animation is required for the life loss. A simple value update with color change is sufficient.

### Engineering Notes

- Remove the existing score-reset-to-0 logic from `selectAnswer` for wrong answers. On a wrong answer, only decrement `lives`; leave the score unchanged.
- Decrement `lives` in the `selectAnswer` method. The `lives` property must not go below 0. Clamp or guard against this.

### QA / Testing Notes

- Answer incorrectly once: verify lives shows 2 and the score remains at whatever value it was before the wrong answer.
- Answer correctly twice (score = 200), then answer incorrectly: verify lives shows 2 and score still shows 200.
- Answer incorrectly with lives already at 1 (one life remaining): verify lives shows 0 and the game-over transition occurs (per Story 3).
- Answer correctly multiple times in a row: verify lives remain at 3 throughout.
- Rapid double-tap on a wrong answer: verify only one life is lost (the existing `!answered` guard prevents processing a second tap).

### Edge Cases & Risk Analysis

- **Score interaction:** Wrong answers no longer reset the score. The score at game over reflects the player's total accumulated correct answers throughout the game. For example, a player who answers 7 correctly (score = 700) and then gets their 3rd wrong answer will see "Score: 700" on the game-over screen.
- **Double-tap on wrong answer:** The existing `!answered` guard in `selectAnswer` prevents duplicate processing. The life decrement, being inside the same code path, is equally protected.

---

## Story 3: Game Over Screen

**As a** trivia player,
**I want to** see my final stats when the game ends,
**So that** I can reflect on how I performed.

**Story Points:** 3
**Priority:** P0
**Dependencies:** Story 2

### Acceptance Criteria

- [ ] When the player loses their last life (selects a 3rd incorrect answer), the "Incorrect!" feedback and answer result colors are shown on the trivia screen as normal.
- [ ] After the player taps "Next" on the round where they lost their last life, the trivia screen is replaced by a game-over screen (the player does NOT tap "Next" into another question).
- [ ] The game-over screen displays a heading such as "Game Over".
- [ ] The game-over screen displays the player's accumulated score (e.g., "Score: 700").
- [ ] The game-over screen displays the number of questions answered correctly out of the total number of questions answered (e.g., "3 / 10 correct").
- [ ] The player cannot answer more questions or advance rounds once the game-over screen is showing.

### Design Notes

- The game-over screen should be a separate composable, visually distinct from the trivia question screen.
- Center the content vertically and horizontally, consistent with the existing loading and error state screens.
- Use `MaterialTheme.typography.headlineMedium` for the "Game Over" heading.
- Use `MaterialTheme.typography.titleLarge` for the score and accuracy stats.
- Maintain the existing horizontal padding (24dp) for consistency with the trivia screen.
- Include sufficient vertical spacing (16-24dp) between the heading, stats, and the "Play Again" button (Story 4).

### Engineering Notes

- Add `totalAnswered` and `correctAnswered` state properties to `TriviaViewModel`, both initialized to 0. Increment `totalAnswered` on every answer selection. Increment `correctAnswered` only when the selected answer is correct. These increments should happen in the existing `selectAnswer` method alongside score and lives updates.
- The game-over screen is a new composable function (e.g., `GameOverScreen`) that accepts score, correct count, and total count as parameters.
- In `MainActivity`, add a new branch to the `when` block that checks for the game-over condition (lives == 0 AND the player has tapped "Next" to dismiss the final round). This should render `GameOverScreen` instead of `TriviaQuestionScreen`.
- The transition to game-over happens when `nextRound()` is called with 0 lives remaining. The ViewModel should set a `gameOver` flag (or equivalent) at that point rather than preparing a new round.
- The trivia screen with the final incorrect answer feedback must remain visible until the player taps "Next". The game-over screen only appears after that tap.
- Unit test the tracking counters: answer 5 questions (3 correct, 2 wrong), verify `totalAnswered` is 5 and `correctAnswered` is 3.

### QA / Testing Notes

- Play a full game: answer several questions correctly, then answer 3 incorrectly. Verify the game-over screen appears after tapping "Next" on the final wrong answer.
- On the final wrong answer, verify the "Incorrect!" text and answer button coloring are shown normally before the game-over transition.
- Verify the accuracy display is correct: if the player answered 7 out of 10 questions correctly, it shows "7 / 10 correct".
- Verify the score shown on the game-over screen matches the score that was visible on the trivia screen at the moment of the last wrong answer (the accumulated score, not 0).
- Verify no answer buttons or "Next" button from the trivia screen are visible on the game-over screen.
- Verify the back button / system navigation does not return the player to the trivia screen mid-game-over. (Pressing back should exit the app, consistent with the current single-screen architecture.)

### Edge Cases & Risk Analysis

- **Player loses all 3 lives on the first 3 questions:** The game-over screen should show "0 / 3 correct" and a score of 0. Verify this edge case works.
- **Player loses all 3 lives immediately (wrong, wrong, wrong):** Same as above but verifies the minimum game length is 3 questions.
- **High score at game over:** A player who answers many questions correctly before losing 3 lives will have a large accumulated score displayed on the game-over screen. For example, 7 correct answers (score = 700) then 3 wrong answers shows "Score: 700" and "7 / 10 correct".
- **Player pool exhaustion during a game:** If the player answers enough questions to exhaust the player pool before losing all lives, the existing pool-reset logic (from the Question Loop feature) handles this transparently. No special handling is needed for the lives system.
- **Tracking counter accuracy:** The `totalAnswered` and `correctAnswered` counters must increment exactly once per answer selection. The existing `!answered` guard in `selectAnswer` prevents double-counting from rapid taps.

---

## Story 4: Play Again

**As a** trivia player,
**I want to** start a new game from the game-over screen,
**So that** I can try to beat my previous performance.

**Story Points:** 2
**Priority:** P0
**Dependencies:** Story 3

### Acceptance Criteria

- [ ] The game-over screen displays a "Play Again" button.
- [ ] Tapping "Play Again" returns the player to the trivia screen with a fresh game: 3 lives, score at 0, and a new question displayed.
- [ ] The accuracy counters (correct / total) reset to 0 / 0 on a new game.
- [ ] The used-player-IDs pool resets so the new game draws from all available players.
- [ ] After tapping "Play Again", the game-over screen is no longer visible and the trivia screen is fully interactive (answer buttons enabled, no residual game-over state).

### Design Notes

- Place the "Play Again" button below the stats on the game-over screen.
- Use a filled `Button` (primary style) to make it the clear primary action on the screen.
- Make the button full-width with the same horizontal padding as the stats content (24dp).
- Use `MaterialTheme.typography.bodyLarge` for the button text, consistent with the existing "Next" button.

### Engineering Notes

- Add a `resetGame()` method to `TriviaViewModel` that resets all game state: lives to 3, score to 0, totalAnswered to 0, correctAnswered to 0, selectedPlayerId to null, gameOver flag to false, pointsUsedIds and goalsUsedIds to empty sets, and calls `prepareRound()` to load a fresh question.
- The "Play Again" button's `onClick` calls `viewModel.resetGame()`.
- Verify that `prepareRound()` works correctly after resetting the used-IDs pools (it should, since it already handles empty used-ID sets on initial launch).

### QA / Testing Notes

- Complete a game, tap "Play Again", and verify all state is fresh: lives show 3, score shows 0, a new question appears with enabled answer buttons.
- Complete a game, tap "Play Again", then play the full second game to completion. Verify the game-over screen shows stats for only the second game, not cumulative across both games.
- Tap "Play Again" rapidly multiple times: verify the app does not crash or enter an inconsistent state.
- After "Play Again", verify that players from the previous game can appear again (the used-player pool was reset).

### Edge Cases & Risk Analysis

- **Incomplete reset:** If any piece of state is not reset (e.g., usedPlayerIds carries over), the new game will behave unexpectedly. The reset method must be comprehensive. A unit test should verify every state property returns to its initial value after `resetGame()`.
- **Rapid tap on "Play Again":** Multiple rapid taps should be safe because `resetGame()` is idempotent -- calling it twice produces the same result as calling it once. No debounce is needed.
- **Memory:** Starting many games in a row does not accumulate state because each reset clears all previous game data. No memory leak concern.

---

## Summary Table

| Story | Title | Points | Priority | Dependencies |
|-------|-------|--------|----------|-------------|
| 1 | Display Lives on the Trivia Screen | 2 | P0 | None |
| 2 | Lose a Life on Wrong Answer | 2 | P0 | Story 1 |
| 3 | Game Over Screen | 3 | P0 | Story 2 |
| 4 | Play Again | 2 | P0 | Story 3 |

**Total Story Points:** 9

---

## Assumptions

1. **Wrong answers no longer reset the score.** The existing mechanic where a wrong answer resets the score to 0 is removed by this feature. Wrong answers now only cost a life; the score accumulates throughout the game and only resets to 0 when the player starts a new game via "Play Again".
2. **Game-over transition happens after "Next" tap, not immediately.** The player sees the "Incorrect!" feedback and colored answer buttons on their final wrong answer, then taps "Next" to reach the game-over screen. This preserves the existing answer-feedback UX and avoids jarring the player by immediately yanking them out of the trivia screen.
3. **No lives-related animations or sound effects.** A simple count update with error coloring is sufficient. Heart icons, shake animations, or sound effects are potential future enhancements.
4. **No persistent high scores or game history.** The game-over screen shows stats for the current game only. Cross-session leaderboards or history are out of scope.
5. **3 lives is not configurable.** The number of lives is hardcoded to 3. A difficulty-selection screen or configurable lives count is a potential future enhancement.
