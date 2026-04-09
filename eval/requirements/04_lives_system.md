# Lives System and Game Over

Give the player 3 lives per game. Each incorrect answer costs one life. When all lives are lost, show a game-over screen with the player's final score and accuracy. Wrong answers no longer reset the score to 0; the score only resets when starting a new game.

## Acceptance Criteria

1. `TriviaViewModel` exposes a `lives: Int` property that starts at 3.
2. When `selectAnswer` is called with an incorrect player ID, `lives` decrements by 1.
3. When `selectAnswer` is called with the correct player ID, `lives` does not change.
4. `nextRound()` does not change `lives`.
5. `lives` never goes below 0.
6. When `selectAnswer` is called with an incorrect player ID, the score remains unchanged (wrong answers no longer reset the score to 0).
7. `TriviaViewModel` exposes a `totalAnswered: Int` property that starts at 0 and increments by 1 on every call to `selectAnswer`.
8. `TriviaViewModel` exposes a `correctAnswered: Int` property that starts at 0 and increments by 1 when `selectAnswer` is called with the correct player ID.
9. `TriviaViewModel` exposes an `isGameOver: Boolean` that is `true` when `lives` equals 0, `false` otherwise.
10. When `isGameOver` is `true`, `selectAnswer` and `nextRound` have no effect.
11. When the player taps "Next" on the round where they lost their last life, the trivia screen is replaced by a game-over screen.
12. The game-over screen displays the player's accumulated score and accuracy as "`correctAnswered` / `totalAnswered` correct".
13. The game-over screen displays a "Play Again" button.
14. Tapping "Play Again" resets `lives` to 3, `score` to 0, `totalAnswered` to 0, `correctAnswered` to 0, `isGameOver` to `false`, and starts a new round.

## Engineering Notes

- Add `lives`, `totalAnswered`, and `correctAnswered` as Compose `mutableIntStateOf` state in `TriviaViewModel` (same pattern as `score`).
- `isGameOver` should be a derived `val` from `lives == 0`, not stored state.
- All new properties should be read-only externally (`private set`).
- Remove the existing score-reset-to-0 logic from `selectAnswer` for wrong answers.
- Add a `resetGame()` method that resets all game state and calls `prepareRound()`.
- The game-over screen is a new Composable that reads from the ViewModel.
