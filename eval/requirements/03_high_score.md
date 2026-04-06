# Session High Score

Track the highest score the player has reached during the current session.

## Acceptance Criteria

1. `TriviaViewModel` exposes a `highScore: Int` property that starts at 0
2. After a correct answer, if the updated `score` exceeds `highScore`, `highScore` is updated to equal `score`
3. `highScore` does not change when `score` resets to 0 after an incorrect answer
4. `highScore` persists across rounds (i.e. `nextRound()` does not reset it)
5. `highScore` is never negative

## Engineering Notes

- Add `highScore` as Compose `mutableIntStateOf` state in `TriviaViewModel` (same pattern as `score`).
- The update to `highScore` must happen inside `selectAnswer`, after `score` is updated.
- `highScore` should be read-only externally (`private set`).
- No UI changes are required; this story is ViewModel-only.
