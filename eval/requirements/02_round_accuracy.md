# Round Accuracy Tracking

Track the player's accuracy across the session: how many rounds they have answered and how many they got right.

## Acceptance Criteria

1. `TriviaViewModel` exposes `totalRounds: Int` that starts at 0
2. `TriviaViewModel` exposes `correctRounds: Int` that starts at 0
3. `TriviaViewModel` exposes `accuracy: Double` that is `0.0` when no rounds have been played
4. `totalRounds` increments by 1 when `selectAnswer` is called (regardless of correctness)
5. `correctRounds` increments by 1 when `selectAnswer` is called with the correct player ID
6. `correctRounds` does not change when `selectAnswer` is called with an incorrect player ID
7. `accuracy` equals `correctRounds.toDouble() / totalRounds` for any non-zero `totalRounds`
8. `nextRound()` does not reset `totalRounds`, `correctRounds`, or `accuracy`

## Engineering Notes

- Add `totalRounds`, `correctRounds` as Compose `mutableIntStateOf` state in `TriviaViewModel`.
- `accuracy` should be a computed `val` (derived from `totalRounds` and `correctRounds`), not stored state.
- All three properties should be read-only externally (`private set` for stored state).
- No UI changes are required; this story is ViewModel-only.
