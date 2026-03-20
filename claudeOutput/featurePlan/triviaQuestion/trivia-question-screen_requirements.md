# Feature: Trivia Question Screen

## Feature Overview

Replace the existing NHL stats leaders list screen with an interactive multiple-choice trivia question screen. The screen presents the user with the question "Which of these players currently has the most points?" and three tappable answer buttons populated from the cached points leaders data. Three players are randomly selected from all available players; the correct answer is whichever of those three has the most points. After the user taps an answer, the screen shows whether they were correct or incorrect.

**Definition of Done:** The app launches, fetches data as before, and displays a single trivia question with three randomized answer buttons. Tapping a button reveals whether the selection was correct or incorrect. The old stats list screen is no longer reachable.

---

## Story 1: Display Trivia Question with Three Answer Choices

**As a** trivia player,
**I want to** see a multiple-choice question about NHL points leaders when I open the app,
**So that** I can test my hockey knowledge.

**Story Points:** 3
**Priority:** P0
**Dependencies:** None (uses existing data fetch and cached `statsData`)

### Acceptance Criteria

- [ ] When the app finishes loading data, the screen displays the text "Which of these players currently has the most points?" as a prominent heading.
- [ ] Below the question, three buttons are displayed, each labeled with a player's full name (first name + last name, e.g. "Connor McDavid").
- [ ] The three buttons correspond to three distinct players randomly selected from all available players in the "points" category of `statsData`.
- [ ] The correct answer is whichever of the three randomly selected players has the highest `value` in the "points" category.
- [ ] The vertical order of the three buttons is randomized each time the screen is displayed, so the correct answer does not always appear in the same position.
- [ ] While data is still loading, the existing `CircularProgressIndicator` continues to display.
- [ ] The previous stats leaders list screen (`StatsLeadersList`) is no longer shown anywhere in the app.

### Design Notes

- Use Material 3 components consistent with the existing theme (`PuckTriviaTheme`).
- Buttons should be full-width or near-full-width to provide a large tap target. `Button` or `OutlinedButton` from Material 3 are appropriate.
- The question text should use `MaterialTheme.typography.headlineSmall` or `titleLarge` to give it visual prominence.
- Center the question and buttons vertically within the available space (inside the `Scaffold` content area with `innerPadding` applied) using a `Column` with appropriate arrangement.
- Maintain comfortable vertical spacing between the question text and the buttons, and between each button (e.g., 12-16dp).

### Engineering Notes

- Select three players at random from `statsData["points"]` using `shuffled().take(3)` or equivalent random selection.
- The correct answer is the player among the three with the highest `value` (i.e., `selectedThree.maxByOrNull { it.value }`). The overall points leader may or may not be included depending on the random selection.
- Shuffle the list of three players to randomize button order.
- The shuffled list of three players should be computed once when the data arrives and stored in a `remember`/`mutableStateOf` variable so it does not re-shuffle on every recomposition. Use `remember(statsData)` or a `derivedStateOf` keyed on `statsData` to ensure stability.
- The `StatsLeadersList` composable and its associated constants (`CATEGORY_ORDER`, `CATEGORY_LABELS`) can remain in the codebase for now (they are not shown to the user); removing them is optional cleanup.
- Create the new trivia screen as a `@Composable` function (e.g., `TriviaQuestionScreen`) in a new file or in `MainActivity.kt`, called from `setContent` in place of `StatsLeadersList`.

### QA / Testing Notes

- Verify the correct answer is always the player with the highest value among the three randomly selected players (not necessarily the overall points leader).
- Verify all three names are distinct players (no duplicates).
- Verify button order changes across multiple app launches (statistical check -- not always the same position).
- Verify the loading spinner still shows while data is being fetched.
- Verify the old stats list screen does not appear at any point.

### Edge Cases

- **Fewer than 3 players in points category:** Extremely unlikely given NHL data, but if the "points" list has fewer than 3 entries, the app should not crash. Display as many buttons as there are players (minimum 1). This is a defensive guard, not a designed experience.
- **"points" key missing from statsData:** If the API response does not contain a "points" category, display an error or empty state rather than crashing. A simple `Text("Unable to load question")` is sufficient for this iteration.
- **Players with identical names:** The data uses player IDs internally, so even if two players share a name, they are distinct entries. For this iteration, displaying duplicate names is acceptable since it is astronomically unlikely among NHL leaders.

---

## Story 2: Answer Selection and Correctness Feedback

**As a** trivia player,
**I want to** tap one of the answer buttons and see whether I got it right,
**So that** I can learn and enjoy the trivia experience.

**Story Points:** 2
**Priority:** P0
**Dependencies:** Story 1

### Acceptance Criteria

- [ ] Tapping any of the three answer buttons records the user's selection and transitions the screen to a "result" state.
- [ ] If the user tapped the correct answer (the player with the highest value among the three selected), the screen displays a "Correct!" message.
- [ ] If the user tapped an incorrect answer, the screen displays an "Incorrect!" message.
- [ ] After an answer is selected, all three buttons become disabled (non-tappable) so the user cannot change their answer.
- [ ] The correct answer is visually distinguished after selection (e.g., the correct button changes to a success/green color).
- [ ] If the user selected an incorrect answer, their selected button is visually distinguished as wrong (e.g., changes to an error/red color).
- [ ] The question text remains visible in the result state.

### Design Notes

- Use `MaterialTheme.colorScheme.error` for incorrect selections and a green/success color (e.g., `Color(0xFF4CAF50)` or `MaterialTheme.colorScheme.primary` adapted) for correct answer highlighting. If Material 3's color scheme does not include a semantic "success" color, define a green constant.
- The "Correct!" or "Incorrect!" message should appear between the question and the buttons, or below the buttons, with clear visual weight (e.g., `titleMedium` or `titleLarge` typography).
- Disabled buttons should still display their text clearly (not overly faded) since the player names serve as reference after answering.

### Engineering Notes

- Introduce a state variable to track the screen's phase: `unanswered` vs. `answered`. A nullable `selectedPlayerId: Int?` works well -- `null` means unanswered, non-null means answered.
- Compare `selectedPlayerId` against the correct player's `id` to determine correctness.
- Button `enabled` should be `selectedPlayerId == null`.
- Button colors can be set conditionally using `ButtonDefaults.buttonColors()` with overridden `containerColor` based on whether the button's player is the correct answer or the selected-wrong answer.
- No network requests are needed for this story; all logic is local using the already-cached data.

### QA / Testing Notes

- Tap the correct answer and verify "Correct!" feedback and green highlight on the correct button.
- Tap each incorrect answer in separate test runs and verify "Incorrect!" feedback, red highlight on the tapped button, and green highlight on the actual correct button.
- Verify buttons are not tappable after an answer has been selected (tap a second button after answering and confirm no state change).
- Verify the question text remains visible after answering.

### Edge Cases

- **Rapid double-tap:** If the user taps two buttons in very quick succession, only the first tap should register. The state transition to `answered` on the first tap should prevent the second tap from having any effect since buttons become disabled.
- **Configuration change (screen rotation) after answering:** With the current architecture (state held in `mutableStateOf` on `MainActivity`), a configuration change will re-create the activity and reset state. This is acceptable for this iteration. The question and answers will re-randomize on rotation. Preserving answer state across configuration changes is a future concern.

---

## Future Considerations

These items are explicitly out of scope for this iteration but are noted for planning purposes:

- **"Next Question" or "Play Again" button** to reset the screen with a new question (potentially from a different stat category).
- **Score tracking** across multiple questions.
- **Timer** adding urgency to each question.
- **ViewModel migration** to survive configuration changes and separate concerns.
- **Question variety** using other stat categories (goals, assists, etc.) with dynamically generated question text.
- **Difficulty scaling** by selecting decoys that are closer in rank to the correct answer.
