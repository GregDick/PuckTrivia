# Feature: Landscape Two-Column Layouts for Question & Game Over Screens

## Feature Overview

Refactor the **Question** screen (`TriviaQuestionScreen`) and the **Game Over** screen (`GameOverScreen`) so that, in **landscape orientation only**, content is laid out as two side-by-side vertical columns instead of a single stacked column. This eliminates the current landscape rendering problems where vertically stacked content is cramped, runs off the bottom of the short landscape viewport, or overlaps (the Question screen draws a top-pinned status column and a center-aligned content column in the same `Box`, which collide on short heights). On the Question screen the left column holds the question text and the right column holds the vertical stack of answer-choice buttons. On the Game Over screen the right column holds the high-score list and the left column holds the game-over summary (final score and accuracy). **Portrait layout is unchanged.** The **Start screen is out of scope and must not be modified.**

This is a layout/responsive-design refactor. No new game logic, no new functionality, no copy changes, no behavior changes to scoring, lives, answer handling, high-score persistence, or navigation.

**Definition of Done:**
- In landscape, both the Question screen and the Game Over screen render as two readable columns with no overlapping elements and no off-screen content, across the supported device range (phones and tablets, minSdk 30+).
- In portrait, both screens render exactly as they do today (no visual regression).
- All answer choices and the Next button remain reachable and tappable in landscape on small heights (scroll if needed).
- The Start screen is byte-for-byte unchanged.
- The project builds (`./gradlew assembleDebug`), all existing unit and instrumented tests pass, and the existing `regular_season_quiz.journey.xml` journey still passes.

---

## Open Questions (please confirm — spec proceeds on the stated assumptions)

1. **Game Over left column contents.** The user said high scores go in the right column but did not specify the left. **Assumption:** the left column shows the game-over summary — the "Game Over" heading, "Score: N", "N / N correct", the "New top-3 score!" celebration (when applicable), and the "Play Again" button. If you'd rather the "Play Again" button span the full width below both columns, or live in the right column under the scores, say so.
2. **Status block (Score / Lives / Season / feedback) on the Question screen in landscape.** The Question screen currently pins Score/Lives/Season/feedback to the top center. **Assumption:** in landscape this status block stays as a full-width header row above the two columns (not inside either column), so the left column is purely the question and the right column is purely the answers, as described. Confirm you don't want the status moved into the left column.
3. **Overflow behavior.** **Assumption:** when answer buttons (right column) or the high-score/summary content exceed the available height on very short landscape viewports, that column scrolls vertically and independently. Alternative is to shrink/scale content; scrolling is the chosen approach.
4. **Column split ratio.** **Assumption:** a 50/50 split (`weight(1f)` each) with a gutter between columns. If you prefer the question to get more room (e.g., 40/60 or 45/55), specify.
5. **Tablet portrait.** **Assumption:** "landscape" is keyed off orientation (width >= height), not a width-based size class, so a tablet in portrait keeps the single-column layout. If you want wide tablets to use two columns in portrait too, that's a scope change — flag it.

---

## Current State (grounding)

Verified against the codebase:

- **`MainActivity.kt`** routes between screens via a `when` on `viewModel` state inside a single `Scaffold`. It passes `Modifier.padding(innerPadding)` to each screen. No orientation logic exists anywhere in `app/src/main`.
- **`TriviaQuestionScreen.kt`** is a root `Box(fillMaxSize)` containing two overlapping children:
  - a top-pinned `Column` (`align(Alignment.TopCenter)`, `padding(top = 64.dp)`) with Score / Lives / Season label / a fixed-height (48.dp) feedback slot;
  - a center-aligned `Column` (`align(Alignment.Center)`) with the question `Text`, a `choices.forEach { Button }` stack, and a fixed-height (72.dp) Next-button slot.
  In landscape these two columns occupy the same vertical band and collide / clip.
- **`GameOverScreen.kt`** is a single non-scrolling `Column(fillMaxSize, padding(top = 64.dp), horizontal = 24.dp)` stacking: "Game Over" heading, "Score: N", "N / N correct", optional "New top-3 score!", `HighScoreList` (private composable; renders "High Scores" title + up to 3 `HighScoreRow`s), and a full-width "Play Again" `Button`. With no scroll, this overflows the bottom in landscape.
- **No orientation handling, no `verticalScroll`, no `BoxWithConstraints`, no `WindowSizeClass`** currently exist in main source.
- **Tests:** `GameOverScreenTest` (instrumented, `createComposeRule`) asserts on visible text (`"1.  1200"`, `"Game Over"`, `"Play Again"`, `"7 / 10 correct"`) and content descriptions (`"this game"`, `"Rank 1"`). No `testTag` is used anywhere. The journey test `regular_season_quiz.journey.xml` drives the real app and asserts on visible labels ("Score:", "Lives:", answer buttons, "Correct!"/"Incorrect!", "Next").

**Implication for ACs:** the two-column layouts must preserve every existing visible string and content description so current instrumented tests and the journey keep passing without modification.

---

## Story Structure

Three stories. Story 1 establishes the shared orientation primitive so Stories 2 and 3 don't each reinvent detection. Stories 2 and 3 are independent of each other and can be built in parallel after Story 1.

---

## Story 1: Add a shared orientation signal for landscape-aware layouts

**As a** developer,
**I want** a single, reusable way for a composable to know it is in landscape orientation,
**So that** the Question and Game Over screens can branch to a two-column layout consistently without duplicating detection logic.

**Story Points:** 2
**Priority:** P0
**Dependencies:** None

### Acceptance Criteria
- [ ] A composable can determine whether the current configuration is landscape and branch its layout accordingly.
- [ ] The signal recomputes automatically when the device rotates, with no manual recomposition wiring at the call site.
- [ ] The detection is consistent across both refactored screens (the same definition of "landscape" is used by both).
- [ ] No visible behavior changes ship in this story on its own; it is plumbing consumed by Stories 2 and 3.

### Design Notes
- "Landscape" means the viewport is wider than it is tall. Treat exact-square (rare) as portrait/single-column.

### Engineering Notes
- Simplest sufficient approach: read `LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE`. This recomposes on rotation because `LocalConfiguration` updates. Prefer this over pulling in the `material3-window-size-class` artifact unless Story 5's tablet-portrait question is answered "yes" (then a width-based `WindowWidthSizeClass` is the better primitive — see Open Question 5).
- Alternatively `BoxWithConstraints { maxWidth > maxHeight }` measures the actual slot rather than the device; acceptable and arguably more robust to multi-window/split-screen. Pick one and use it in both screens for consistency.
- Expose it as a tiny helper (e.g., `@Composable private fun isLandscape(): Boolean`) or just inline it in each screen — keep it minimal; do not over-engineer a responsive framework.
- No data-model, ViewModel, API, or DI changes. This is presentation-layer only.

### QA / Testing Notes
- Verified indirectly through Stories 2 and 3. No standalone user-facing test needed.

### Edge Cases & Risk Analysis
- **Multi-window / split-screen / freeform:** `LocalConfiguration.orientation` reflects the device, not the app window, so a portrait-orientation phone in a wide split-screen could mis-detect. `BoxWithConstraints` avoids this. Note the tradeoff; for minSdk 30 phone/tablet the configuration approach is acceptable.
- **Foldables:** posture changes surface as configuration changes and will re-trigger detection; no special handling required.

---

## Story 2: Two-column landscape layout for the Question screen

**As a** player rotating my device to landscape during a game,
**I want** the question on the left and the answer choices stacked on the right,
**So that** I can read the question and tap answers without content overlapping, getting cut off, or feeling cramped.

**Story Points:** 5
**Priority:** P0
**Dependencies:** Story 1

### Acceptance Criteria
- [ ] In **landscape**, the Question screen shows two side-by-side columns: the **left** column contains the question text and the **right** column contains the vertical stack of answer-choice buttons.
- [ ] In landscape, no two elements overlap, and no question text or answer button is clipped or drawn off-screen.
- [ ] In landscape, the Score, Lives, Season label, and the Correct/Incorrect feedback remain visible and are not obscured by the columns (header above the columns per Open Question 2).
- [ ] In landscape, every answer-choice button remains fully visible and tappable; if the answers do not fit the available height, the answer column scrolls so all choices are reachable.
- [ ] In landscape, when an answer has been selected, the **Next** button remains visible and tappable (scroll into view if needed).
- [ ] Answer correctness coloring is unchanged: before answering all buttons use the primary color; after answering the correct choice turns green and an incorrectly selected choice turns red.
- [ ] After answering, each answer button still shows the player's stat value with its unit label, exactly as today.
- [ ] In **portrait**, the Question screen is visually identical to the current implementation (single centered column, top-pinned status, fixed-height feedback and Next slots).
- [ ] All existing visible strings and the answer button labels ("First Last  TEAM") are preserved verbatim so the existing journey test continues to pass.

### Design Notes
- Two columns split the content area; default 50/50 (`Row` with two `weight(1f)` children) with a gutter (~16–24.dp) between them. Reuse existing horizontal screen padding (24.dp).
- Left column: vertically center the question text within the column for balance; keep `headlineSmall` styling.
- Right column: keep the existing `Button` styling (`fillMaxWidth` within the column, `vertical = 6.dp` spacing) and the existing per-button `Row` with name on the left and stat value on the right after answering.
- Keep the status header (Score/Lives/Season/feedback) as a full-width band above the two-column row; preserve the fixed-height feedback slot so feedback doesn't shift layout.
- Accessibility: tab/focus order should be question first, then answers top-to-bottom, then Next. Do not regress touch target sizes (answer buttons already meet 48.dp via default Button height + padding).
- The single-column portrait path should remain the existing code path; the landscape path is a sibling branch.

### Engineering Notes
- Branch at the top of `TriviaQuestionScreen` on the Story 1 landscape signal: render the existing layout for portrait, a new two-column layout for landscape. Factor the answer-button rendering (`choices.forEach { ... Button ... }`) into a shared private composable so both paths use identical button logic and there is no duplication of correctness-color and stat-label logic.
- The answer column should use `verticalScroll(rememberScrollState())` (or wrap in a scrollable container) so overflow scrolls. The question column generally won't overflow but consider scroll/auto-size for extreme question lengths.
- Keep all parameters of `TriviaQuestionScreen` and its `MainActivity` call site unchanged — this is internal layout only.
- No changes to `TriviaViewModel`, answer-selection callbacks, or routing in `MainActivity`.
- Beware re-using a single `rememberScrollState()` across orientation changes; that's fine, but ensure the per-button `containerColor` recomputation stays inside the shared button composable.

### QA / Testing Notes
- Manual: rotate to landscape on a phone mid-question; verify left=question, right=answers, no overlap, all buttons tappable, Next appears after answering.
- Manual: force a small landscape height (e.g., a short emulator window or split-screen) and confirm the answer column scrolls and the last answer + Next are reachable.
- Manual: very long question text and very long player names ("First Last  TEAM") — confirm wrapping, no clipping, no horizontal overflow.
- Manual: 2-choice vs many-choice rounds (goalie vs skater question types) — confirm the right column adapts.
- Automated: extend `TriviaQuestionScreen` instrumented coverage if added, or rely on the journey. The existing journey (`regular_season_quiz.journey.xml`) must still pass; consider adding a landscape variant journey that performs the same flow after rotation.
- Regression: confirm portrait renders identically (screenshot diff or visual check against current build).

### Edge Cases & Risk Analysis
- **Rotation mid-answer:** state is already preserved by `TriviaViewModel` (per the configuration-change feature); the layout must re-lay-out without losing the selected/answered/feedback state. Verify the Correct/Incorrect feedback and post-answer colors persist across rotation.
- **Very short landscape height (split-screen, small phones):** must scroll, not clip. This is the primary "avoid overlaps" fix.
- **Long player names / long stat unit labels:** the per-button `Row` uses `weight(1f)` on the name and shows the value after answering; verify the narrower right-column width doesn't cause the value to wrap awkwardly or push off-screen.
- **Many choices:** if a future question type returns more choices, the scroll requirement keeps them reachable — designing the right column as scrollable is the extension point.
- **Double-tap / rapid taps on answers:** unchanged from today (buttons disable once `answered`); layout change must not re-enable a disabled button.

---

## Story 3: Two-column landscape layout for the Game Over screen

**As a** player who just finished a game and rotated to landscape,
**I want** my final score/summary on the left and the high-score list on the right,
**So that** I can see my result and the leaderboard at once without content running off the bottom of the screen.

**Story Points:** 5
**Priority:** P0
**Dependencies:** Story 1

### Acceptance Criteria
- [ ] In **landscape**, the Game Over screen shows two side-by-side columns: the **right** column contains the high-score list ("High Scores" title + up to three rank rows) and the **left** column contains the game-over summary.
- [ ] The left column in landscape contains, at minimum: the "Game Over" heading, "Score: N", and "N / N correct", plus the "New top-3 score!" message when the player placed in the top three (per Open Question 1).
- [ ] The "Play Again" button is present and tappable in landscape, and tapping it triggers the same play-again action as today.
- [ ] In landscape, no elements overlap and nothing is clipped or off-screen; if either column's content exceeds the available height, that column scrolls.
- [ ] When there are **no** high scores, the right column shows no "High Scores" section (matching current behavior where the section is hidden for an empty list), and the left column still renders the summary and Play Again — the screen does not look broken with an empty right column.
- [ ] The current game's high-score row keeps its distinct tonal highlight and its "this game" accessibility marker.
- [ ] In **portrait**, the Game Over screen is visually identical to the current implementation (single centered column).
- [ ] All existing visible strings ("Game Over", "Score: N", "N / N correct", "High Scores", "New top-3 score!", "Play Again", "rank.  score", formatted date) and the per-row content descriptions ("Rank N…", "this game") are preserved verbatim so `GameOverScreenTest` passes without modification.

### Design Notes
- Two columns split the content area; default 50/50 with a gutter. Reuse the existing 24.dp horizontal padding and the existing 64.dp top padding (or fold the top inset into the columns sensibly).
- Right column reuses the existing private `HighScoreList` / `HighScoreRow` composables unchanged — just place them in the right column for landscape.
- Left column reuses the existing summary `Text`s and the celebration `Text`.
- "Play Again" placement: default is at the bottom of the left column (with the summary). If product prefers it full-width below both columns, that's an acceptable variation (confirm via Open Question 1). Keep `bodyLarge` label styling.
- Vertically center each column's content for balance, or top-align both consistently; pick one and apply to both columns.
- Preserve the `surfaceVariant` / `tertiaryContainer` row backgrounds and `medium` shape on high-score rows.

### Engineering Notes
- Branch at the top of `GameOverScreen` on the Story 1 landscape signal: existing single `Column` for portrait, a `Row` of two columns for landscape. Reuse `HighScoreList` as-is in the right column; do not duplicate the high-score rendering.
- Make each column independently scrollable (`verticalScroll`) to satisfy the no-overflow AC on short heights.
- Keep `GameOverScreen`'s public signature and its `MainActivity` call site unchanged.
- Keep all `@Preview`s working; consider adding a landscape preview (`@Preview(widthDp = 800, heightDp = 360)` or `device = "spec:..."`) for the two-column variant to aid review — additive, not required.
- No changes to high-score persistence, ranking, or `TriviaViewModel`.

### QA / Testing Notes
- Manual: finish a game, rotate to landscape; verify left=summary, right=high scores, no overflow, Play Again works.
- Manual: empty high-score list in landscape (first ever game with no saved scores) — confirm the right column degrades gracefully (no empty/broken section) and the screen still looks intentional.
- Manual: one entry vs three entries — confirm the right column renders 1 and 3 rows correctly; the just-finished game's row keeps its highlight.
- Manual: top-3 placement — confirm "New top-3 score!" shows in the left column.
- Manual: very short landscape height — confirm columns scroll and Play Again is reachable.
- Automated: existing `GameOverScreenTest` must pass unchanged. It uses `createComposeRule` (no orientation control); if a landscape-specific instrumented assertion is desired, drive layout via the Story 1 signal or `BoxWithConstraints` so it can be exercised in a test. Do not break the existing text/content-description assertions.

### Edge Cases & Risk Analysis
- **Empty high scores in landscape:** the right column could be visually empty; ensure the layout still reads as intentional (e.g., the left column can use the full width when there are no scores, or the right column shows nothing but the split still looks balanced). Spell out the chosen handling.
- **Single high-score entry:** right column should not look stretched/awkward; reuse existing row styling.
- **Rotation on the Game Over screen:** state is preserved by the ViewModel; verify the celebration flag, current-game highlight, and the same high-score list survive rotation.
- **Long localized date strings** (`FormatStyle.MEDIUM`/`SHORT` are locale-dependent): the narrower right column must not clip the date; the row uses `SpaceBetween` — verify wrap/ellipsis behavior in the tighter width.
- **Accessibility:** the merged row semantics ("Rank N, … points, … , this game") must survive the column move; do not regress TalkBack grouping.

---

## Cross-Cutting Notes

### Out of Scope / Future Considerations
The ACs above are specific, but because this is a refactor of existing screens it is worth being explicit:
- **Start screen** (`StartScreen.kt`) — must not be touched.
- **Portrait layouts** — no intentional changes; any change is a regression unless it falls out of shared refactoring (e.g., extracting the answer-button composable) and is visually identical.
- **All non-layout behavior** — scoring, lives, answer evaluation, season mode, question generation, high-score persistence/ranking, navigation/routing, loading/error/playoffs-unavailable states — unchanged. (Loading, error, fatal-error, and "unable to load question" states in `MainActivity` are centered `Box`es and are not in scope.)
- **No new copy, colors, typography tokens, or theming changes.**
- Future: if tablets should use two columns in portrait too, migrate Story 1's detection to `WindowWidthSizeClass` (Open Question 5). Designing Story 1 as a single swappable signal makes that a localized change.

### Definition of Done checklist (feature level)
- [ ] `./gradlew assembleDebug` succeeds.
- [ ] `./gradlew test` passes (unit tests unaffected).
- [ ] `./gradlew connectedAndroidTest` passes, including the unmodified `GameOverScreenTest`.
- [ ] The `regular_season_quiz.journey.xml` journey passes (and, if added, a landscape variant).
- [ ] Manual landscape verification on both screens: no overlap, no clipping, all controls reachable/tappable, scrolls on short heights.
- [ ] Manual portrait verification: no visual regression on either screen.
- [ ] `StartScreen.kt` diff is empty.
