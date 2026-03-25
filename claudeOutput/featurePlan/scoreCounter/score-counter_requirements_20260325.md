# Feature: Persistent Score Counter

## Feature Overview

Add a visible score counter to the trivia game that tracks the player's current score across rounds. The score starts at 0, increases by 100 points for each correct answer, and resets to 0 on any wrong answer. The score is displayed persistently on the trivia screen so the user always knows their current streak value. "Persistent" in this context means the score survives the full question/answer loop within a session -- it does not reset between rounds. However, the score does not survive app process death or restart; it resets to 0 when the app is relaunched. A high score that persists across sessions is explicitly out of scope.

**Definition of Done:** A score counter is visible on the trivia question screen at all times. It reads "0" on first launch, increases by 100 after each correct answer, and resets to 0 immediately after a wrong answer. The score carries over across rounds within the same app session and resets to 0 when the app is restarted.

---

## Story 1: Display Score on the Trivia Screen

**As a** trivia player,
**I want to** see my current score displayed on the screen at all times,
**So that** I know how well I am doing in my current session.

**Story Points:** 2
**Priority:** P0
**Dependencies:** None (builds on existing `TriviaQuestionScreen`)

### Acceptance Criteria

- [ ] A score label is visible on the trivia question screen before the user answers any question.
- [ ] The score label displays the text "Score: 0" at the start of a new app session.
- [ ] The score label remains visible while the question is unanswered, after an answer is selected, and after tapping "Next" to advance to the next round.
- [ ] The score label is visible during every round of the question loop without exception.
- [ ] The score value is displayed as a whole number with no decimal places (e.g., "Score: 300", not "Score: 300.0").

### Design Notes

- Place the score label above the question text, aligned to the start (left in LTR layouts) or centered -- either is acceptable as long as it does not overlap with or crowd the question text.
- Use `MaterialTheme.typography.titleMedium` or `headlineSmall` for the score text so it is prominent but does not compete with the question for visual hierarchy.
- Use the default `MaterialTheme.colorScheme.onBackground` color for the score text in its resting state. Color changes on correct/wrong answers are covered in Story 2.
- Ensure sufficient vertical spacing (at least 16dp) between the score label and the question text below it.
- The score label should respect the existing horizontal padding (24dp) used by the rest of the screen content.

### Engineering Notes

- Add a `score` state variable (`mutableIntStateOf(0)`) inside `TriviaQuestionScreen`. This state lives in Compose `remember` and is scoped to the composable's lifetime, which means it resets when the activity is recreated (app restart, configuration change). This is the intended behavior for session-scoped persistence.
- The score variable must NOT be scoped inside `remember(roundNumber)` -- it must survive across rounds. Place it at the same scope level as `roundNumber` and `usedPlayerIds`.
- Display the score using a `Text` composable placed in the `Column` before the question `Text`.

### QA / Testing Notes

- Verify the score label is visible on the very first screen the user sees after data loads (round 0, no answer selected).
- Verify the score label does not disappear or flicker during round transitions (when the user taps "Next").
- Verify the score reads "0" after a fresh app launch, including after force-stopping and relaunching the app.
- Test with scores of varying lengths: 0, 100, 1000, 10000, to ensure the label does not truncate or overflow.

### Edge Cases & Risk Analysis

- **Configuration change (screen rotation):** Since the app currently stores all state in `remember` (not a ViewModel), the score will reset on configuration change. This is consistent with existing behavior for `roundNumber` and `usedPlayerIds`. No special handling is required for this story, but a future ViewModel migration would address this for all state simultaneously.
- **Data load failure:** If `statsData` is empty and the "Unable to load question" fallback is shown, the score label should not appear since there is no game to score. The score composable should only render inside the branch where `pointsPlayers` is non-empty.
- **Very high scores:** Theoretically a user could reach scores in the tens of thousands. The text should not be truncated. Since Compose `Text` handles arbitrary string lengths and the score is a simple integer, this is not a practical concern.

---

## Story 2: Increment Score on Correct Answer

**As a** trivia player,
**I want to** earn 100 points when I answer correctly,
**So that** I am rewarded for my knowledge and motivated to keep my streak going.

**Story Points:** 2
**Priority:** P0
**Dependencies:** Story 1 (score display must exist)

### Acceptance Criteria

- [ ] When the user selects the correct answer, the displayed score increases by exactly 100 points.
- [ ] The score update is visible at the same time as the "Correct!" feedback text -- the user does not need to tap "Next" to see the updated score.
- [ ] Selecting the correct answer multiple rounds in a row accumulates the score additively (0 -> 100 -> 200 -> 300, etc.).
- [ ] The score increment happens exactly once per answered question, even if the UI re-composes multiple times.

### Design Notes

- When the score increases, briefly display the score text in the same green used for "Correct!" feedback (`Color(0xFF4CAF50)`, the existing `CorrectGreen` constant). The green color should persist until the user taps "Next" to advance, at which point it reverts to the default text color.
- No animation is required for the score change. A simple value update with a color change is sufficient for this story.

### Engineering Notes

- Update the `score` value inside the answer selection logic. Specifically, when `selectedPlayerId` is set and `selectedPlayerId == correctPlayer.id`, add 100 to `score`.
- Be careful about WHERE the score mutation happens. It should occur exactly once at the moment the user taps an answer button, not during recomposition. The safest approach is to update the score inside the `onClick` lambda of the answer `Button`, at the same point where `selectedPlayerId` is set. Do NOT derive the score from recomposition-time checks like `if (answered && isCorrect)` because that would re-execute on every recomposition.
- The `onClick` lambda currently reads `onClick = { if (!answered) selectedPlayerId = player.id }`. The score update should be added inside this same `if (!answered)` guard, after determining whether the selected player is correct: `if (!answered) { selectedPlayerId = player.id; if (player.id == correctPlayer.id) score += 100 }`.

### QA / Testing Notes

- Answer 5 questions correctly in a row and verify the score reads 500.
- Verify the score update is visible immediately after tapping the correct answer, without needing to tap "Next".
- Verify that tapping "Next" does not cause a second increment.
- Rapidly tap the correct answer button multiple times and verify the score only increments once (the `!answered` guard prevents this, but it should be tested).

### Edge Cases & Risk Analysis

- **Double-tap on correct answer:** The existing `!answered` guard in the `onClick` handler prevents processing a second tap after `selectedPlayerId` is set. The score update, being inside the same guard, is equally protected. No additional safeguard is needed.
- **Recomposition-triggered duplication:** If the score update were placed in a `LaunchedEffect` or derived state block, recomposition could trigger it multiple times. Placing it in the `onClick` lambda avoids this entirely.

---

## Story 3: Reset Score to Zero on Wrong Answer

**As a** trivia player,
**I want** my score to reset to 0 when I answer incorrectly,
**So that** the game has high stakes and rewards sustained correct-answer streaks.

**Story Points:** 1
**Priority:** P0
**Dependencies:** Story 1 (score display must exist), Story 2 (scoring logic must exist)

### Acceptance Criteria

- [ ] When the user selects an incorrect answer, the displayed score is set to 0.
- [ ] The score reset is visible at the same time as the "Incorrect!" feedback text -- the user does not need to tap "Next" to see the reset.
- [ ] If the score was already 0 before an incorrect answer, it remains at 0 (no negative scores, no special behavior).
- [ ] After a reset, the next correct answer brings the score to 100 (not back to the pre-reset value).

### Design Notes

- When the score resets to 0, display the score text in the same red/error color used for "Incorrect!" feedback (`MaterialTheme.colorScheme.error`). The red color should persist until the user taps "Next", at which point it reverts to the default text color.
- If the score was already 0 before the wrong answer, still show the red color on the "0" to provide consistent visual feedback that the answer was wrong.

### Engineering Notes

- In the same `onClick` lambda described in Story 2, add an `else` branch: if the selected player is not the correct player, set `score = 0`.
- Full logic in the `onClick` lambda: `if (!answered) { selectedPlayerId = player.id; if (player.id == correctPlayer.id) score += 100 else score = 0 }`.
- The color of the score text can be derived during composition: `val scoreColor = when { !answered -> defaultColor; isCorrect -> CorrectGreen; else -> MaterialTheme.colorScheme.error }`. This is a safe derived value (no mutation) and correctly re-derives on recomposition.

### QA / Testing Notes

- Build up a score (e.g., 300) then answer incorrectly. Verify the score shows 0 immediately.
- After a reset, answer correctly and verify the score shows 100 (not 300 or any other value).
- Answer incorrectly on the very first question (score already 0). Verify the score still shows 0 and the red color is applied.
- Alternate correct and incorrect answers: verify the sequence is 0 -> 100 -> 0 -> 100 -> 0.

### Edge Cases & Risk Analysis

- **Score already at 0:** Setting `score = 0` when it is already 0 is a no-op in terms of state value but will still trigger the red color feedback via the `isCorrect` / `answered` flags. No special handling needed.
- **User expectation around "reset":** Some users might expect a wrong answer to subtract points rather than reset to 0. This is a design decision, not a bug. The reset-to-zero mechanic creates a streak-based game that is intentionally high-stakes.

---

## Story 4: Score Resets on App Restart

**As a** trivia player,
**I want** the score to start fresh at 0 when I reopen the app,
**So that** each app session feels like a new game.

**Story Points:** 1
**Priority:** P1
**Dependencies:** Story 1 (score display must exist)

### Acceptance Criteria

- [ ] When the app is launched (cold start), the score displays 0 regardless of what the score was in any previous session.
- [ ] When the app process is killed and relaunched, the score displays 0.
- [ ] The score is not written to any persistent storage (SharedPreferences, DataStore, database, or file).

### Design Notes

- No specific design work needed. This story is about confirming the absence of persistence rather than adding behavior.

### Engineering Notes

- This story requires no new code if the score is implemented as a Compose `remember { mutableIntStateOf(0) }` as specified in Story 1. The `remember` block initializes to 0 on every fresh composition, which includes activity recreation and cold starts.
- Do NOT use `rememberSaveable` for the score. `rememberSaveable` would persist the score across configuration changes and process death via `SavedStateHandle`, which contradicts the requirement that the score resets on app restart. (Note: `rememberSaveable` survives process death only if the system kills the process while the activity is in the back stack, not if the user explicitly force-stops the app. However, the simpler `remember` is the correct choice here to avoid any ambiguity.)
- If a future story introduces persistent high scores, that would be a separate state variable backed by DataStore or similar. The current session score should remain non-persistent.

### QA / Testing Notes

- Launch the app, answer several questions correctly to build up a score, then force-stop the app from Android Settings (or via `adb shell am force-stop com.example.pucktrivia`). Relaunch and verify the score is 0.
- Launch the app, build up a score, press the home button, then return to the app via the recents screen. Verify the score is preserved (the app was not killed). This confirms that the score only resets on actual process death, not on backgrounding.
- Rotate the device (if rotation is not locked). The score will reset due to activity recreation with `remember`. This is acceptable and consistent with all other state in the current architecture. Document this as a known limitation if needed.

### Edge Cases & Risk Analysis

- **Configuration change (rotation):** As noted above, `remember` does not survive configuration changes. The score will reset to 0 on rotation. This is consistent with `roundNumber` and all other game state. If this becomes a user complaint, the fix is to migrate all game state to a ViewModel, which is a separate architectural story.
- **System-initiated process death:** If Android kills the app process while it is in the background (low memory), and the user returns via recents, the activity will be recreated and the score will be 0. This is the correct behavior per requirements.

---

## Summary Table

| Story | Title | Points | Priority | Dependencies |
|-------|-------|--------|----------|-------------|
| 1 | Display Score on the Trivia Screen | 2 | P0 | None |
| 2 | Increment Score on Correct Answer | 2 | P0 | Story 1 |
| 3 | Reset Score to Zero on Wrong Answer | 1 | P0 | Story 1, Story 2 |
| 4 | Score Resets on App Restart | 1 | P1 | Story 1 |

**Total Story Points:** 6

---

## Assumptions

1. **"Persistent" means session-scoped, not disk-persisted.** The score survives across rounds within the question loop but does not survive app restart. If cross-session persistence (e.g., high score leaderboard) is desired, that would be a separate feature.
2. **No ViewModel migration in this feature.** The existing codebase manages all state via Compose `remember` in the composable function. This feature follows the same pattern. A ViewModel migration (which would also fix configuration-change resets for all state) is a separate architectural concern.
3. **No score animation.** A color change on correct/incorrect is included, but animated score transitions (counting up, bouncing, etc.) are not in scope.
4. **Single stat category.** The app currently only asks questions about the "points" stat category. The scoring system is independent of which stat category is being asked about, so it will work without modification if additional categories are added later.
5. **No high-score tracking or display.** There is no "best score" label, no persistence of the highest score achieved, and no game-over screen. These are potential future enhancements.
