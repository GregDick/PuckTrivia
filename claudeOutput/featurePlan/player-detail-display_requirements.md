# Feature: Player Detail Display on Answer Choices

## Feature Overview

Enhance the trivia answer choice buttons to display additional player information beyond just the player's name. During the question phase, each answer choice button shows the player's NHL team abbreviation alongside their name, giving users a helpful hint and more context. After the user answers and the result is revealed, each button additionally shows the player's total points, reinforcing learning and making the reveal more informative.

**Definition of Done:** Each answer choice button displays the player's team abbreviation at all times, and additionally displays the player's point total after the answer is revealed. The layout is clean and readable across device sizes.

---

## Story 1: Display Team Name on Answer Choice Buttons

**As a** trivia player,
**I want to** see each player's NHL team abbreviation next to their name on every answer choice,
**So that** I have additional context about each player while deciding my answer.

**Story Points:** 2
**Priority:** P1
**Dependencies:** None

### Acceptance Criteria

- [ ] Each answer choice button displays the player's team abbreviation alongside their full name (e.g., "Connor McDavid - EDM")
- [ ] The team abbreviation is visible both before and after the user answers
- [ ] The team abbreviation text is visually distinct from the player name (e.g., lighter weight, secondary styling, or separated by a delimiter) so the two pieces of information are easy to scan independently
- [ ] The button text does not overflow or truncate on a typical phone screen width for the longest realistic player name + team abbreviation combination

### Design Notes

- The current button text is `"${player.firstName} ${player.lastName}"` rendered with `MaterialTheme.typography.bodyLarge` inside a full-width `Button`
- Consider a layout like `"FirstName LastName  -  TEAM"` or a two-line layout with the team on a secondary line in a smaller/lighter style
- The `teamAbbrev` field on `SkaterStatLeader` contains standard NHL three-letter abbreviations (e.g., "EDM", "TOR", "COL")
- Keep the button height comfortable for touch targets -- if using a two-line layout, ensure the button remains easy to tap
- Follow existing Material 3 color and typography patterns already in the theme

### Engineering Notes

- The `SkaterStatLeader` data class already contains `teamAbbrev: String` -- no data model or API changes are needed
- The change is isolated to `TriviaQuestionScreen.kt`, specifically the `Text` composable inside the `choices.forEach` button block
- If using a two-line layout, replace the single `Text` with a `Column` containing two `Text` composables inside the `Button`
- No ViewModel changes required -- `choices` already carries the `teamAbbrev` field to the UI

### QA / Testing Notes

- Verify the team abbreviation displays correctly for all answer choices in multiple rounds
- Test with players whose combined name + team abbreviation is long (e.g., "Alexander Barkov - FLA") to ensure no truncation or overflow
- Verify the team abbreviation remains visible after answering (both correct and incorrect states)
- Check that button colors (default, correct-green, error-red, disabled) still render text legibly with the added content

### Edge Cases & Risk Analysis

- **Long player names:** Some NHL players have long compound names. The layout must handle names like "Pierre-Luc Dubois" plus a team abbreviation without clipping. Test on narrow devices (360dp width).
- **Missing team abbreviation:** The `teamAbbrev` field is non-nullable in the data model and always present in the NHL API response, so this should not occur. If it somehow does, the player name alone should still display correctly.

---

## Story 2: Display Player Points After Answer Reveal

**As a** trivia player,
**I want to** see each player's total points displayed on the answer choice buttons after I answer,
**So that** I can see how the players compare and learn from the result.

**Story Points:** 3
**Priority:** P1
**Dependencies:** Story 1 (the button layout from Story 1 establishes the multi-info display pattern that this story extends)

### Acceptance Criteria

- [ ] After the user selects an answer, each answer choice button additionally displays the player's point total (e.g., "85 pts")
- [ ] The point total is NOT visible before the user answers -- it only appears after the answer is revealed
- [ ] The point total is displayed for ALL three answer choices, not just the correct one
- [ ] The points value is formatted as a whole number (no decimal places) followed by a "pts" label
- [ ] The player with the highest point total (the correct answer) is visually distinguishable by the existing correct/incorrect button coloring -- the points display reinforces this by making the numerical difference visible

### Design Notes

- After the answer reveal, the button should show three pieces of information: player name, team abbreviation, and point total
- Consider placing the points on the right side of the button or on a second line below the name + team, to keep the layout balanced
- The points text should be visually secondary to the player name but prominent enough to be easily scannable
- The transition from "no points shown" to "points shown" happens when `answered` becomes true -- the layout shift should feel natural and not jarring
- Since the `value` field is a `Double` from the API (e.g., `85.0`), display it as an integer (e.g., "85 pts")

### Engineering Notes

- The `SkaterStatLeader.value` field contains the point total as a `Double` -- convert to `Int` for display (e.g., `player.value.toInt()`)
- The `answered` boolean is already passed to `TriviaQuestionScreen` and used to conditionally render post-answer UI -- use the same condition to toggle points visibility
- Consider using a `Row` with `Arrangement.SpaceBetween` inside the button to place name+team on the left and points on the right, or a `Column` for a stacked layout
- No ViewModel or data model changes required -- all data is already available in the `choices` list

### QA / Testing Notes

- Verify points are hidden before answering and shown after answering for all three choices
- Verify the points format: whole numbers with no decimal places, followed by "pts"
- Confirm the correct answer's point total is always the highest among the three choices (this validates the game logic, not this feature, but is worth a sanity check)
- Test the visual transition: answer a question and confirm points appear without layout jank or flicker
- Verify that the "Next" button and result text ("Correct!" / "Incorrect!") still display correctly alongside the updated button layout

### Edge Cases & Risk Analysis

- **Large point values:** Top NHL scorers can have point totals over 100 (e.g., 128 pts). Verify the layout handles three-digit numbers without overflow.
- **Equal point values:** Two players could theoretically have the same point total. The game logic picks the `maxBy { it.value }` as correct, which would pick one arbitrarily in a tie. This is a pre-existing game logic concern, not a display concern -- but the display should handle it gracefully (both players show the same number).
- **Layout shift on reveal:** When points appear, the button content changes. If using a layout that changes the button height (e.g., adding a second line), all three buttons should change simultaneously so the layout does not shift unevenly. Prefer a fixed-height button or a layout approach (like right-aligned points in a `Row`) that does not change button dimensions.
- **Fractional values:** While NHL points are always whole numbers, the `value` field is a `Double`. If the API ever returns a fractional value (unlikely but possible for other stat categories in the future), `toInt()` truncation is acceptable for the points display.
