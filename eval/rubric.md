# Eval Rubric

Used by judge.py to score each skill's output.

## Metric 1: Tests Pass (0 or 1)

- **1** — Gradle test output contains `BUILD SUCCESSFUL` with no test failures
- **0** — Any test failure, compilation error, or `BUILD FAILED`

## Metric 2: Requirement Satisfaction (0–3 per criterion, then averaged)

For each acceptance criterion in the requirement:

- **3** — Fully implemented and verifiably correct from the diff
- **2** — Mostly implemented; minor edge case or wording gap
- **1** — Partially implemented; the intent is present but incomplete
- **0** — Not implemented or implemented incorrectly

## Metric 3: Test Quality (0–3)

Evaluate the tests added by the skill:

- **+1** — At least one test exists per acceptance criterion (7 criteria → at least 7 tests, or fewer well-structured tests that each cover multiple criteria)
- **+1** — Every new public property/function added to `TriviaViewModel` has at least one dedicated unit test
- **+1** — Tests are meaningful: they assert observable behavior, not just that the code compiles or that a value is non-null

Score is the sum of the three sub-points above (0–3).
