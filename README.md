# Puck Trivia

An Android trivia app built with Kotlin and Jetpack Compose.

## Build

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew test                   # Run unit tests
```

## Evals

Evals compare two Claude Code skills (`/tdd-from-requirements` vs `/build-from-requirements`) against the same requirement. Each run creates isolated git worktrees, executes both skills, runs tests, and collects results for manual review.

### Running an eval

```bash
./eval/run_eval.sh eval/requirements/01_streak_counter.md
```

Pass any requirement file from `eval/requirements/` as the argument. Results are saved to `eval/results/<requirement>_<timestamp>/`.

### Available requirements

- `01_streak_counter.md`
- `02_round_accuracy.md`
- `03_high_score.md`

### Results structure

Each eval run produces:

```
eval/results/<name>_<timestamp>/
  tdd.diff                 # All changes from TDD skill
  build.diff               # All changes from Build skill
  tdd_tests/               # Test source files from TDD skill
  build_tests/             # Test source files from Build skill
  tdd_test_results.txt     # Gradle test output for TDD
  build_test_results.txt   # Gradle test output for Build
  tdd_claude_output.txt    # Full Claude session log for TDD
  build_claude_output.txt  # Full Claude session log for Build
  REVIEW.md                # Scorecard template (fill in after review)
```

Scoring uses the rubric in `eval/rubric.md`.
