# Code Review Report

**Date:** 2026-03-25
**Reviewed Files:** `app/src/main/java/com/example/pucktrivia/TriviaQuestionScreen.kt`
**Plan Reference:** `claudeOutput/featurePlan/scoreCounter/score-counter_requirements_20260325.md`
**PR Branch:** `score`
**Diff Base:** `main`

---

## Executive Summary

The implementation is clean, minimal, and correct. All four stories from the requirements doc are fully satisfied. The score state is placed at the right scope, mutated only in the `onClick` lambda (preventing recomposition-triggered duplication), and the color feedback logic is a safe derived value. No bugs were found that required fixing. One minor design deviation and two low-priority observations are noted below, none of which block the PR.

**Verdict: APPROVED**

---

## Plan Compliance

### Implemented Items

- **Story 1 — Display Score:** `score` state is declared with `remember { mutableIntStateOf(0) }` at the correct scope (alongside `roundNumber` and `usedPlayerIds`, not inside `remember(roundNumber)`). A `Text("Score: $score")` composable is placed above the question text with `titleMedium` typography and at least 16 dp of bottom padding. The score label is inside the non-null `pointsPlayers` branch, so it does not appear on the error fallback screen — matching the edge-case requirement.
- **Story 2 — Increment on Correct Answer:** `score += 100` is inside the `if (!answered)` guard in `onClick`, preventing double-counting on rapid taps or recomposition. The score is immediately visible after the tap.
- **Story 3 — Reset on Wrong Answer:** `else score = 0` is present in the same expression. Color feedback (`CorrectGreen` / `MaterialTheme.colorScheme.error`) is derived from `answered` and `isCorrect` at composition time — a safe read-only derivation.
- **Story 4 — Resets on App Restart:** `remember` (not `rememberSaveable`) is used. No persistent storage is touched.

### Deviations from Plan

- **Score Text Alignment:** Story 1 Design Notes say the score label should be "aligned to the start (left in LTR layouts) or centered." The implementation uses `Modifier.fillMaxWidth()` but does not set a `textAlign`. In a `Column` with `horizontalAlignment = Alignment.CenterHorizontally`, a `fillMaxWidth` `Text` with no explicit `textAlign` renders its text **start-aligned** (the default). This is one of the two acceptable options from the requirements, so it is not a defect. It is worth noting because centering might look more balanced given the centered layout of everything else on the screen.

### Missing Items

None.

---

## Code Quality Assessment

### Best Practices
**Rating: Excellent**

- `mutableIntStateOf` is used for the `Int` state instead of the more generic `mutableStateOf`, which is the correct Compose-idiomatic choice for primitive integers and avoids unnecessary boxing.
- Score mutation is confined to the event handler (`onClick`), not to a side-effect or derived-state block. This is explicitly the approach recommended in the engineering notes and is the safest way to avoid double-counting.
- The color derivation (`when { !answered -> ...; isCorrect -> ...; else -> ... }`) is a pure read of existing state. No mutation happens at composition time.
- `remember` is used correctly without `rememberSaveable`, satisfying the non-persistence requirement.

### Architecture
**Rating: Excellent**

The change follows the existing pattern of the file precisely: all state lives in `remember` inside the composable, no ViewModel, no side effects. The score variable is placed at the same level as the other session-scoped state variables, consistent with the existing architecture. There are no new dependencies or imports required (the existing `mutableIntStateOf` import was already present).

### Readability
**Rating: Good**

The inline `if (...) score += 100 else score = 0` on a single line is compact. It is clear enough for a one-liner of this simplicity, and it matches the exact snippet shown in the requirements doc. Some teams prefer the block form for mutation-heavy branches, but this does not rise to a readability concern given the straightforward logic.

The score `Text` composable's `color` parameter spans multiple lines using a `when` expression, which is consistent with how `containerColor` is handled for the answer buttons elsewhere in the file.

### Testability
**Rating: Good**

This matches the existing state of the file. The composable has no ViewModel and all state is internal, which makes unit testing difficult regardless of this PR. That is a pre-existing architectural constraint, not introduced by this change. The score logic itself (increment vs. reset) is simple enough that the risk of an undetected logic error is low.

---

## Bugs Identified

### Fixed Bugs (Simple)

None. No simple bugs were identified that required a fix.

### Complex Bugs Requiring Attention

None. The following edge cases were evaluated and confirmed to be handled correctly:

- **Double-tap / rapid tap:** The `if (!answered)` guard sets `selectedPlayerId` on the first tap, which makes `answered` true, blocking any subsequent tap in the same round.
- **isCorrect evaluated before answered:** `val isCorrect = selectedPlayerId == correctPlayer.id` is computed at composition time. When `!answered` (i.e., `selectedPlayerId == null`), `isCorrect` is `false`. The score `Text` color condition checks `!answered` first, so `isCorrect` is only read when `answered` is true — correct behavior.
- **Score state outliving the null check:** `score` is declared after the `if (pointsPlayers.isNullOrEmpty()) { ...; return }` guard. This means `score` is never allocated in the error branch, and the score `Text` never renders there. This is the correct behavior per the requirements edge-case note.
- **Score accumulation across rounds:** `score` is not inside `remember(roundNumber)`, so it is not reset when `roundNumber` increments. Only `choices` and `correctPlayer` are keyed on `roundNumber`. Confirmed correct.
- **Recomposition safety:** `score` is only mutated in `onClick`. The `isCorrect` value and the color `when` expression are pure reads. No mutation risk on recomposition.

---

## Recommendations

1. **(Low) Consider explicit `textAlign = TextAlign.Start` on the score label.** Currently the text is start-aligned by default, which is correct per the requirements. Adding the explicit alignment makes the intent unambiguous and prevents a future refactor of the `Column`'s `horizontalAlignment` from accidentally centering the text without anyone noticing.

2. **(Low) Consider extracting the score color derivation to a named local val.** The `when` block is readable inline, but giving it a name (e.g., `val scoreColor = when { ... }`) would make the `Text(color = scoreColor, ...)` call slightly easier to scan — consistent with how `containerColor` is named before being passed to `ButtonDefaults`. This is a style preference, not a correctness issue.

3. **(Informational) Configuration-change reset is a known limitation.** As documented in the requirements, `remember` without `rememberSaveable` resets on device rotation. This is consistent with all other state in the composable and is explicitly accepted. If a ViewModel migration is planned for future stories, the score variable should be included in that migration at that time.

---

## Positive Highlights

- The implementation is exactly as lean as the feature requires. No over-engineering, no new abstractions, no new files.
- The placement of `score` in the state declaration block (lines 45-48) is well-organized and immediately readable alongside the other state variables.
- Using `mutableIntStateOf` instead of `mutableStateOf<Int>` shows good Compose idiomatic awareness.
- The mutation guard (`if (!answered)`) correctly wraps both the `selectedPlayerId` assignment and the score update in a single atomic block, so the two state writes cannot become inconsistent.
- The color feedback for the score label mirrors the color feedback already used for the answer buttons and the "Correct!"/"Incorrect!" text, giving the feature visual consistency with zero additional color constants.
