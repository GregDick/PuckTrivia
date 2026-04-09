# Streak Counter

Track the player's current consecutive-correct-answer streak and their best streak for the session.

## Acceptance Criteria

1. `TriviaViewModel` exposes a `streak: Int` property that starts at 0
2. `streak` increments by 1 when `selectAnswer` is called with the correct player ID
3. `streak` resets to 0 when `selectAnswer` is called with an incorrect player ID
4. `TriviaViewModel` exposes a `bestStreak: Int` property that starts at 0
5. `bestStreak` updates to equal `streak` whenever `streak` exceeds the current `bestStreak`
6. `bestStreak` does not decrease when `streak` resets to 0
7. `nextRound()` does not affect `streak` or `bestStreak`

## Engineering Notes

- Add `streak` and `bestStreak` as Compose state in `TriviaViewModel` (same pattern as `score`).
- Both properties should be read-only externally (`private set`).
- No UI changes are required; this story is ViewModel-only.
