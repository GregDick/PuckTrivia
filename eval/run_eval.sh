#!/usr/bin/env bash
# Usage: ./eval/run_eval.sh eval/requirements/01_streak_counter.md
#
# Runs both skills against a single requirement in isolated git worktrees,
# then produces a manual-review report in eval/results/.

set -euo pipefail

REQUIREMENT="${1:?Usage: $0 <path-to-requirement.md>}"
REQUIREMENT="$(realpath "$REQUIREMENT")"
REQ_NAME="$(basename "$REQUIREMENT" .md)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
ROOT="$(git rev-parse --show-toplevel)"
RESULTS_DIR="$ROOT/eval/results/${REQ_NAME}_${TIMESTAMP}"
WORKTREE_BASE="$ROOT/eval/worktrees"
TDD_WORKTREE="$WORKTREE_BASE/tdd_${TIMESTAMP}"
BUILD_WORKTREE="$WORKTREE_BASE/build_${TIMESTAMP}"
TDD_BRANCH="eval-tdd-${TIMESTAMP}"
BUILD_BRANCH="eval-build-${TIMESTAMP}"

mkdir -p "$RESULTS_DIR" "$WORKTREE_BASE"

cleanup() {
  echo ""
  echo "Cleaning up worktrees..."
  git worktree remove --force "$TDD_WORKTREE" 2>/dev/null || true
  git worktree remove --force "$BUILD_WORKTREE" 2>/dev/null || true
  git branch -D "$TDD_BRANCH" "$BUILD_BRANCH" 2>/dev/null || true
}
trap cleanup EXIT

echo "=== Eval: $REQ_NAME ==="
echo "Results will be saved to: $RESULTS_DIR"
echo ""

# ── Create worktrees from main ───────────────────────────────────────────────
echo "[1/5] Creating worktrees..."
git worktree add "$TDD_WORKTREE" -b "$TDD_BRANCH" main
git worktree add "$BUILD_WORKTREE" -b "$BUILD_BRANCH" main

# ── Run TDD skill ────────────────────────────────────────────────────────────
echo "[2/5] Running /tdd-from-requirements..."
pushd "$TDD_WORKTREE" > /dev/null
claude --dangerously-skip-permissions -p \
  "/tdd-from-requirements

$(cat "$REQUIREMENT")

---
NOTE: This is a non-interactive automated eval run. Do not pause for user confirmation at any checkpoint. Proceed through all phases automatically without asking 'Continue?' or similar." \
  > "$RESULTS_DIR/tdd_claude_output.txt" 2>&1
echo "  done (exit $?)"
popd > /dev/null

# ── Run Build skill ──────────────────────────────────────────────────────────
echo "[3/5] Running /build-from-requirements..."
pushd "$BUILD_WORKTREE" > /dev/null
claude --dangerously-skip-permissions -p \
  "/build-from-requirements

$(cat "$REQUIREMENT")

---
NOTE: This is a non-interactive automated eval run. Do not pause for user confirmation at any checkpoint. Proceed through all phases automatically without asking 'Continue?' or similar." \
  > "$RESULTS_DIR/build_claude_output.txt" 2>&1
echo "  done (exit $?)"
popd > /dev/null

# ── Run tests ────────────────────────────────────────────────────────────────
echo "[4/5] Running tests..."
pushd "$TDD_WORKTREE" > /dev/null
./gradlew test --rerun-tasks > "$RESULTS_DIR/tdd_test_results.txt" 2>&1 && \
  echo "  TDD:   PASS" || echo "  TDD:   FAIL"
popd > /dev/null

pushd "$BUILD_WORKTREE" > /dev/null
./gradlew test --rerun-tasks > "$RESULTS_DIR/build_test_results.txt" 2>&1 && \
  echo "  Build: PASS" || echo "  Build: FAIL"
popd > /dev/null

# ── Collect diffs ────────────────────────────────────────────────────────────
echo "[5/5] Collecting diffs..."
git -C "$TDD_WORKTREE" diff main > "$RESULTS_DIR/tdd.diff"
git -C "$BUILD_WORKTREE" diff main > "$RESULTS_DIR/build.diff"

# ── Write manual review report ───────────────────────────────────────────────
TDD_TESTS_PASS="FAIL"
grep -q "BUILD SUCCESSFUL" "$RESULTS_DIR/tdd_test_results.txt" && TDD_TESTS_PASS="PASS"
BUILD_TESTS_PASS="FAIL"
grep -q "BUILD SUCCESSFUL" "$RESULTS_DIR/build_test_results.txt" && BUILD_TESTS_PASS="PASS"

TDD_DIFF_LINES="$(wc -l < "$RESULTS_DIR/tdd.diff")"
BUILD_DIFF_LINES="$(wc -l < "$RESULTS_DIR/build.diff")"

cat > "$RESULTS_DIR/REVIEW.md" << REPORT
# Eval Review: $REQ_NAME

**Date:** $(date '+%Y-%m-%d %H:%M')
**Requirement:** $REQUIREMENT

## Quick Stats

| Metric              | TDD        | Build      |
|---------------------|------------|------------|
| Tests pass          | $TDD_TESTS_PASS        | $BUILD_TESTS_PASS       |
| Diff size (lines)   | $TDD_DIFF_LINES        | $BUILD_DIFF_LINES       |

## Files

- \`tdd.diff\` — all changes made by the TDD skill
- \`build.diff\` — all changes made by the Build skill
- \`tdd_test_results.txt\` — Gradle test output for TDD
- \`build_test_results.txt\` — Gradle test output for Build
- \`tdd_claude_output.txt\` — full Claude session output for TDD
- \`build_claude_output.txt\` — full Claude session output for Build

## Rubric Scorecard

Fill this in after reviewing the diffs.

### Metric 1: Tests Pass (0 or 1)

| Skill | Score | Notes |
|-------|-------|-------|
| TDD   |       |       |
| Build |       |       |

### Metric 2: Requirement Satisfaction (0–3 per criterion, then average)

| Criterion | TDD score | Build score | Notes |
|-----------|-----------|-------------|-------|
| 1.        |           |             |       |
| 2.        |           |             |       |
| 3.        |           |             |       |
| 4.        |           |             |       |
| 5.        |           |             |       |
| 6.        |           |             |       |
| 7.        |           |             |       |
| **Avg**   |           |             |       |

### Metric 3: Test Quality (0–3)

| Sub-point                         | TDD | Build |
|-----------------------------------|-----|-------|
| One test per acceptance criterion |     |       |
| Tests cover all new public VM API |     |       |
| Tests assert meaningful behavior  |     |       |
| **Total**                         |     |       |

## Overall Winner

- **Winner:**
- **Reason:**
REPORT

echo ""
echo "=== Done ==="
echo "Results saved to: $RESULTS_DIR/"
echo ""
echo "TDD tests:   $TDD_TESTS_PASS"
echo "Build tests: $BUILD_TESTS_PASS"
echo ""
echo "Open REVIEW.md to fill in your scores:"
echo "  $RESULTS_DIR/REVIEW.md"
