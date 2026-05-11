# Feature Requirements: Season Mode Toggle (Regular Season vs. Playoffs)

> **Author**: Principal PM / TPM (requirements pass)
> **Date**: 2026-04-29
> **Branch context**: `goalie-question`
> **Feature ID**: `season-mode-toggle`

---

## 1. Feature Overview

Add a **start screen** to Puck Trivia that lets the player choose between **Regular Season** stats and **Playoffs** stats before the game begins. The selected mode determines which NHL API endpoints are queried (`gameType=2` for regular season, `gameType=3` for playoffs), and is reflected in the trivia question copy (e.g. "Which of these forwards has the most playoff goals?").

This feature also addresses a known mechanics bug: the goalie save-percentage question is silently dropped when no goalie meets the hardcoded `minWins = 10` threshold (a near-certainty in early playoff rounds where the league leader has 4 wins). After evaluating alternatives (see Section 8), this spec **recommends removing `minWins` entirely** and replacing the goalie filter with a `poolFraction`-based qualification consistent with the other question types.

### Definition of Done (feature level)

- [ ] First-time launch shows a start screen with Regular Season / Playoffs choices.
- [ ] Selecting a mode triggers the stats fetch for the corresponding `gameType`.
- [ ] All 5 question types build successfully and produce valid 3-choice rounds in both modes (when data is available).
- [ ] If playoffs aren't available yet (no playoff data returned by the API), the user is informed and can fall back to Regular Season without restarting the app.
- [ ] Question text reflects the selected mode ("most points" vs. "most playoff points").
- [ ] `minWins` filter is removed from `QuestionType.GOALIES_SAVE_PCT` (and the field is deleted from the enum signature).
- [ ] All existing unit tests pass; new tests cover mode selection, URL construction per mode, and the goalie-question pool construction without `minWins`.
- [ ] No regression on the existing `goalie-question` branch fixes (concurrent fetches, safe body read, etc.).

---

## 2. Structure: Epic with 4 Stories

This is structured as an **Epic** because it spans:

- a new UI surface (start screen),
- a DI/architecture refactor (URLs become mode-dependent — they can no longer be plain `String` injections),
- ViewModel state-machine changes (a new "awaiting mode selection" pre-load state),
- a content/copy change (per-mode question text),
- a game-mechanics change (`minWins` removal).

The stories are ordered so each can land independently behind the previous one.

---

# Epic: Season Mode Toggle

---

### Story 1: Convert hardcoded URLs to a mode-aware URL provider

**As a** developer,
**I want to** replace the singleton `@StatsUrl` / `@GoalieStatsUrl` `String` injections with a function or factory that takes a `SeasonMode`,
**So that** the ViewModel can fetch the correct endpoint based on the player's runtime choice.

**Story Points:** 3
**Priority:** P0 (blocks all subsequent stories)
**Dependencies:** None

#### Acceptance Criteria

- [ ] A new sealed type or enum `SeasonMode` exists with two variants: `RegularSeason` and `Playoffs`.
- [ ] `NetworkModule` no longer provides `@StatsUrl: String` / `@GoalieStatsUrl: String` directly; instead it provides a `StatsUrlProvider` (or equivalent) that returns the correct URL given a `SeasonMode`.
- [ ] Calling the provider with `SeasonMode.RegularSeason` returns URLs containing `/2?` (regular season `gameType`).
- [ ] Calling the provider with `SeasonMode.Playoffs` returns URLs containing `/3?` (playoff `gameType`).
- [ ] Season string (`20252026`) and base URL (`https://api-web.nhle.com/v1`) remain centralized as private constants in `NetworkModule`.
- [ ] No string-concatenation of game-type happens in `TriviaViewModel` — the ViewModel calls the provider with a `SeasonMode` and receives ready-to-use URLs.
- [ ] All existing unit tests pass after the refactor (tests that previously injected raw URL strings continue to work, either via the provider or via test doubles that bypass it).

#### Design Notes

- No user-facing UI changes in this story.

#### Engineering Notes

- Suggested shape:
  ```kotlin
  enum class SeasonMode(val gameType: String) {
      RegularSeason("2"),
      Playoffs("3"),
  }

  class StatsUrlProvider @Inject constructor() {
      fun skaterUrl(mode: SeasonMode): String = "$BASE/skater-stats-leaders/$NHL_SEASON/${mode.gameType}?limit=-1"
      fun goalieUrl(mode: SeasonMode): String = "$BASE/goalie-stats-leaders/$NHL_SEASON/${mode.gameType}?limit=-1"
  }
  ```
- The `@StatsUrl` / `@GoalieStatsUrl` qualifiers can be deleted, or repurposed if there's a strong reason to keep them. Recommend **delete** to avoid two ways of doing the same thing.
- `TriviaViewModel`'s constructor params change: replace `@StatsUrl statsUrl: String` and `@GoalieStatsUrl goalieStatsUrl: String` with `private val urlProvider: StatsUrlProvider`.
- Existing tests that pass URL strings into the ViewModel will need a small migration. Provide a `FakeStatsUrlProvider` or an overload that lets tests inject pre-baked URLs.

#### QA / Testing Notes

- Unit test: `StatsUrlProviderTest` verifying URL output for both modes for both endpoints (4 cases).
- Unit test: `TriviaViewModelTest` confirms it calls the provider with the correct mode (currently always `RegularSeason` since Story 1 doesn't introduce a UI; default that in this story).
- No instrumented tests required for this story.

#### Edge Cases & Risk Analysis

- **Risk: tests that previously snapshot the exact URL string** may need updating. Search for hardcoded `goalie-stats-leaders/20252026/2` in tests and migrate.
- **Risk: Hilt graph regression** — if the qualifiers are deleted, ensure no other module references them.

---

### Story 2: Add Start Screen with Regular Season / Playoffs choice

**As a** player,
**I want to** choose between Regular Season and Playoffs stats when I open the app,
**So that** I can play with the timeframe of stats I find most interesting.

**Story Points:** 5
**Priority:** P0
**Dependencies:** Story 1

#### Acceptance Criteria

- [ ] On app launch (no game in progress), the user sees a **Start Screen** with the app title and two clearly-labeled buttons: **Regular Season** and **Playoffs**.
- [ ] Tapping either button advances the app to the loading state, which fetches stats for the corresponding `gameType`.
- [ ] The Start Screen does not display a loading spinner — it is the first thing the user sees and is shown immediately on launch.
- [ ] No stats fetch occurs until a button is tapped (the ViewModel does not auto-fetch on construction anymore).
- [ ] Once stats are fetched and pools are built, the app advances to the trivia question screen as it does today.
- [ ] If the user reaches Game Over, tapping "Play Again" returns to the **Start Screen** (not directly to a new game), so the player can switch modes between sessions.
- [ ] The chosen mode is displayed somewhere persistent (small badge/chip) on the trivia screen so the player knows which mode they're in. Suggested placement: top of `TriviaQuestionScreen`, next to score/lives.
- [ ] The Start Screen is themed consistently with `PuckTriviaTheme` (Material 3, edge-to-edge).

#### Design Notes

- **Layout** (suggested):
  - App title "Puck Trivia" centered, large.
  - Subtitle / tagline (optional): "NHL trivia, fresh from the league".
  - "Choose your stats:" label.
  - Two large buttons stacked vertically (or side-by-side on landscape): **Regular Season** (primary) and **Playoffs** (secondary or also primary).
- Use Material 3 `Button` and `OutlinedButton` to differentiate, or two `FilledTonalButton`s of equal weight if both modes are first-class.
- Buttons should be a comfortable tap target (min 48dp height).
- Mode badge on trivia screen: small `AssistChip` or text label like "Playoffs" / "Regular Season" — must be visible but not compete with the question.
- Accessibility: each mode button must have a `contentDescription` that reads the full mode name; the badge should be readable by TalkBack.

#### Engineering Notes

- Introduce a new ViewModel state: `selectedMode: SeasonMode?` (nullable, initially `null`).
- Remove the `init { fetchStats() }` call. Replace with a public `fun startGame(mode: SeasonMode)` that sets `selectedMode = mode` and triggers the fetch.
- `MainActivity`'s `when` ordering becomes:
  1. `selectedMode == null` → Start Screen
  2. `isLoading` → spinner
  3. `loadError` → error
  4. (rest unchanged)
- Add a new file `StartScreen.kt` under `com/example/pucktrivia/`.
- `resetGame()` should reset `selectedMode = null` (or expose a separate `returnToStart()` — recommend the former for simplicity, since "Play Again" naturally returns to Start in the new flow).
- Keep `statsData`, `goalieStatsData`, and `pools` cleared when returning to Start, so a mode switch does not show stale data while the new fetch is in flight.

#### QA / Testing Notes

- Unit test: ViewModel does not fetch on construction (remove the test that asserts otherwise, if any).
- Unit test: `startGame(RegularSeason)` triggers a fetch using the regular-season URLs.
- Unit test: `startGame(Playoffs)` triggers a fetch using the playoff URLs.
- Unit test: `resetGame()` returns the ViewModel to a state where `selectedMode == null` and pools are empty.
- Manual test (on device): launch the app, verify Start Screen appears, tap each mode and confirm the right data loads (cross-reference goalie wins — regular season should show ~30+ wins, playoffs ~4 max).
- Manual test: complete a game, tap Play Again, confirm Start Screen appears.

#### Edge Cases & Risk Analysis

- **Cold-start race**: if the user taps a mode button very quickly (double-tap), only one fetch should occur. Disable the buttons (or set `isLoading = true`) on the first tap.
- **Configuration change** (rotation): the selected mode and any in-flight fetch must survive rotation. Since `TriviaViewModel` is a Hilt `ViewModel`, this should work for free, but verify `selectedMode` is part of ViewModel state, not Compose-only state.
- **Process death**: If the OS kills the process and restores it mid-game, the game state isn't currently persisted; this story does not change that. The user will return to the Start Screen on relaunch, which is acceptable.

---

### Story 3: Per-mode question text and pool fallback when playoffs unavailable

**As a** player who chose Playoffs,
**I want to** see playoff-flavored question copy ("most playoff goals") and be told gracefully if playoffs haven't started yet,
**So that** the experience feels intentional and I'm not confused by empty data.

**Story Points:** 3
**Priority:** P1
**Dependencies:** Story 2

#### Acceptance Criteria

- [ ] When the player is in **Regular Season** mode, all question text reads as it does today (e.g., "Which of these forwards currently has the most points?").
- [ ] When the player is in **Playoffs** mode, question text is rephrased to include "playoff" (e.g., "Which of these forwards has the most playoff points?", "highest playoff save percentage", etc.). Exact copy table below.
- [ ] If the player chooses Playoffs and the API returns empty datasets for **all** question types (e.g., the postseason hasn't started), the app shows a dedicated **"Playoffs haven't started yet"** screen with a button to switch to Regular Season — it does **not** show the generic `fatalError` screen.
- [ ] If the player chooses Playoffs and the API returns data but only some question types yield non-empty pools, the game proceeds with whatever pools are available (current behavior — the silent `continue` in `buildPools` is acceptable here as long as at least one pool exists).
- [ ] Switching from the playoffs-unavailable screen to Regular Season triggers a fresh fetch with regular-season URLs and proceeds normally.

#### Question Text Copy Table

| QuestionType | Regular Season copy (unchanged) | Playoffs copy |
|---|---|---|
| `DEFENDERS_POINTS` | Which of these defenders currently has the most points? | Which of these defenders has the most playoff points? |
| `FORWARDS_POINTS` | Which of these forwards currently has the most points? | Which of these forwards has the most playoff points? |
| `DEFENDERS_GOALS` | Which of these defenders currently has the most goals? | Which of these defenders has the most playoff goals? |
| `FORWARDS_GOALS` | Which of these forwards currently has the most goals? | Which of these forwards has the most playoff goals? |
| `GOALIES_SAVE_PCT` | Which of these goalies currently has the highest save percentage? | Which of these goalies has the highest playoff save percentage? |

Note the deliberate drop of "currently" in playoff copy — playoff stats are cumulative for the postseason, not "currently" in the sense the regular-season copy implies.

#### Design Notes

- Question text comes from `QuestionType.questionText`. Two ways to implement:
  1. Add `questionText(mode: SeasonMode): String` as a method on `QuestionType` (cleanest).
  2. Resolve the right copy in the ViewModel before assigning to `questionText`.
- Recommend option 1 for testability and locality of copy.
- "Playoffs haven't started yet" screen: simple centered layout with title, body text ("The NHL playoffs haven't started yet — try Regular Season instead."), and a primary button "Play Regular Season".

#### Engineering Notes

- "All pools empty" detection: `pools.isEmpty()` after `buildPools()`. When this happens **and** `selectedMode == Playoffs`, set a new state `playoffsUnavailable = true` instead of `fatalError = true`.
- The "switch to Regular Season" button calls `startGame(SeasonMode.RegularSeason)`, which clears the unavailable flag and re-fetches.
- Be careful: an HTTP error or transient network failure should still go to `loadError` (existing behavior), not `playoffsUnavailable`. The unavailable state is specifically "fetch succeeded but data is empty/unusable".

#### QA / Testing Notes

- Unit test: `QuestionType.questionText(SeasonMode.RegularSeason)` returns existing copy for all 5 types.
- Unit test: `QuestionType.questionText(SeasonMode.Playoffs)` returns the playoff copy from the table above.
- Unit test: With a mocked HTTP response that returns empty arrays for all categories in playoffs mode, `playoffsUnavailable` becomes `true` and `fatalError` stays `false`.
- Unit test: The same empty-data response in regular-season mode still results in `fatalError = true` (we expect regular-season data to always exist; empty there is genuinely fatal).
- Manual test: kick off a playoffs run and verify question copy includes "playoff" in every screen.

#### Edge Cases & Risk Analysis

- **Edge: playoffs partially available** — e.g., skater data exists but goalies haven't played yet (impossible in practice, but defensively). Treat any pool-having mode as playable; only the fully-empty case shows the unavailable screen.
- **Edge: transient empty response that recovers on retry** — Story 3 does not add retry logic. The "Switch to Regular Season" button effectively serves as a manual retry path. Adding automatic retry is out of scope.
- **Edge: API returns 200 with an unexpected JSON shape** — already handled by existing `loadError` path; no new risk.
- **Future copy concerns**: if/when off-season is reachable, both modes may be "ended" — out of scope for now (regular season copy can still show last season's leaders and that's acceptable for a trivia app).

---

### Story 4: Remove `minWins` filter from goalie pool construction

**As a** player,
**I want to** see the goalie save-percentage question in any mode that has goalie data,
**So that** the question never silently disappears (which it does today in early playoffs).

**Story Points:** 2
**Priority:** P1
**Dependencies:** None (can ship independently of Stories 1-3, but is most valuable shipped alongside them)

#### Acceptance Criteria

- [ ] The `minWins: Int` parameter is removed from `QuestionType`'s constructor and from `GOALIES_SAVE_PCT`.
- [ ] `TriviaViewModel.buildPools()` no longer reads `goalieData["wins"]` for filtering (it can still read it for other purposes if a future story needs it; this story removes the filter only).
- [ ] The goalie pool is built by sorting goalies by save percentage descending and taking `ceil(size * poolFraction)` — same `poolFraction` mechanism used by every other question type. `poolFraction` for `GOALIES_SAVE_PCT` is set to **1.0** (use all available goalies — see Engineering Notes).
- [ ] In Regular Season mode, the goalie question still feels like a "real" trivia question — i.e., the choices are recognizable starters, not 4th-string call-ups with two games played. (See Engineering Notes for guardrails.)
- [ ] In Playoffs mode, the goalie question appears as long as at least 3 goalies have a recorded save percentage (which is the actual minimum for the game mechanics to work).
- [ ] Existing `GoalieQuestionTypeTest` is updated to remove `minWins` assertions and add an assertion that the pool size is `ceil(N * poolFraction)`.

#### Design Notes

- No UI changes. This is purely a mechanics simplification.
- The user will notice the change indirectly: they'll see the goalie question in playoffs mode (where they currently wouldn't), and possibly see slightly less-famous goalies in regular season (offset by `poolFraction`).

#### Engineering Notes

- **Why drop `minWins` rather than make it mode-aware?** See Section 8 (Recommendation).
- Updated `buildPools` branch for goalies:
  ```kotlin
  val savePctgList = goalieData[type.statKey] ?: continue
  if (savePctgList.isEmpty()) continue
  val sorted = savePctgList.sortedByDescending { it.value }
  built[type] = sorted.take(kotlin.math.ceil(sorted.size * type.poolFraction).toInt())
  ```
- `poolFraction` for `GOALIES_SAVE_PCT` is set to **1.0** — use the full available list. Rationale: goalies are few (~20 qualifying in playoffs, ~70 in regular season). Unlike skaters where the long tail is full of fringe players, the goalie leaderboard already filters to goalies who have played enough to appear. Using the full list maximises variety and avoids the game cycling through the same handful of names.
- Delete the `minWins: Int = 0` default from the `QuestionType` constructor signature; remove the property entirely. This is a small breaking change to the enum's parameter list, so re-run all unit tests.

#### QA / Testing Notes

- Unit test: `QuestionType` no longer has a `minWins` property (compile-time check via test file).
- Unit test: `buildPools` with a fake goalie dataset of 10 goalies (varied save percentages, varied wins from 0 to 30) produces a pool of all 10 goalies (`ceil(10 * 1.0) = 10`), sorted by save percentage descending, with no regard to wins.
- Unit test: `buildPools` with a fake playoff goalie dataset of 4 goalies produces a pool of all 4 goalies. 4 goalies is sufficient for a 3-choice round as long as at least 3 have distinct save percentages.
- Manual test: Regular Season mode — confirm goalie question shows recognizable starters.
- Manual test: Playoffs mode (during postseason) — confirm goalie question now appears.

#### Edge Cases & Risk Analysis

- **Edge: very small playoff pool** — if only 2-3 goalies have a `savePctg` entry, `prepareRound` may not be able to pick 3 distinct values. The existing fatal-error path catches this. With Story 3 in place, an entirely unplayable playoff dataset routes to `playoffsUnavailable`. This is acceptable.
- **Risk: regular-season quality regression** — without `minWins`, a low-save-percentage backup with 1-2 games played could enter the pool in regular season. With `poolFraction = 1.0`, these goalies are included. In practice the regular-season leaderboard only surfaces goalies who have faced enough shots to post a statistically meaningful save percentage; single-game flukes rarely appear. If playtesting reveals too many fringe names, lower `poolFraction` to `0.5` as a first tightening (still more inclusive than the old `minWins = 10` rule).
- **Future**: if the API ever exposes a `gamesPlayed` filter on the leaders endpoint (it doesn't today — see research notes), revisiting a "minimum games played" guard becomes cheap. Not in scope.

---

## 3. Cross-Story Edge Cases

These apply across the epic and should be considered during integration testing:

- **API season rollover**: `NHL_SEASON = "20252026"` is hardcoded. Once the season ends, this string becomes stale. Out of scope for this epic, but flag for a follow-up "make season selection dynamic" story.
- **Network slow/timeout**: The Start Screen → Loading transition relies on a network call. If the call hangs, the user sees a spinner indefinitely. The existing OkHttp client has no explicit timeout configured. **Recommend** adding a 15s read timeout to `OkHttpClient` in this epic (or as a follow-up) so `loadError` triggers reliably.
- **Mode switch mid-game**: not supported in this epic. The user must complete or quit the current game (Game Over → Play Again → Start Screen) to switch modes. Adding an in-game "switch mode" button is a future consideration.
- **Mode persistence across launches**: not in this epic. Every launch shows the Start Screen. If product wants "remember my last choice", that's a follow-up.
- **Offline launch**: with no network, the user picks a mode, sees the spinner briefly, then sees `loadError`. They can use the existing reset path. No special offline UX is added here.

---

## 4. Test Plan Summary

| Layer | Coverage focus |
|---|---|
| Unit (ViewModel) | mode-aware `startGame()`, `selectedMode` lifecycle, `playoffsUnavailable` vs. `fatalError` distinction, pool construction without `minWins` |
| Unit (URL provider) | URL strings for both modes, both endpoints (4 cases) |
| Unit (QuestionType) | mode-aware question text (10 cases: 5 types × 2 modes), absence of `minWins` property |
| Compose UI test (optional, P2) | Start Screen renders both buttons; tapping each calls the correct ViewModel method |
| Manual / instrumented | end-to-end flow in both modes; goalie question appears in playoffs; mode badge visible during play; "Playoffs haven't started" screen shows when applicable |

---

## 5. Files Likely Touched

- `app/src/main/java/com/example/pucktrivia/di/NetworkModule.kt` (Story 1)
- `app/src/main/java/com/example/pucktrivia/di/StatsUrl.kt` (Story 1 — likely deleted)
- `app/src/main/java/com/example/pucktrivia/model/SeasonMode.kt` (new, Story 1)
- `app/src/main/java/com/example/pucktrivia/model/QuestionType.kt` (Stories 3, 4)
- `app/src/main/java/com/example/pucktrivia/TriviaViewModel.kt` (Stories 1, 2, 3, 4)
- `app/src/main/java/com/example/pucktrivia/MainActivity.kt` (Story 2, 3)
- `app/src/main/java/com/example/pucktrivia/StartScreen.kt` (new, Story 2)
- `app/src/main/java/com/example/pucktrivia/PlayoffsUnavailableScreen.kt` (new, Story 3 — could also be inlined in `MainActivity`)
- `app/src/main/java/com/example/pucktrivia/TriviaQuestionScreen.kt` (Story 2 — add mode badge)
- `app/src/test/java/...` — multiple test files updated; new tests for `StatsUrlProvider`, `SeasonMode`, mode-aware question text

---

## 6. Suggested Sequencing & Sprint Plan

**Sprint 1 (foundation):** Story 1 + Story 4. These are low-risk and set up the architecture cleanly. Story 4 in particular fixes the silent-drop bug today and is independent of the UI work.

**Sprint 2 (user-facing):** Story 2 + Story 3. These deliver the actual feature the user sees. Ship them together so the Playoffs option is never selectable without playoff-aware copy and the unavailable-fallback in place.

If sprint capacity is tight, Story 3 could ship after Story 2 — but note that doing so means a brief window where Playoffs mode shows "currently has the most points" copy, which is misleading. Strongly prefer shipping 2 and 3 together.

---

## 7. Open Questions for Product

1. **Default mode**: if we ever auto-skip the Start Screen (e.g., "Play Again" goes straight back into the same mode), which mode is the default? Recommendation: always show the Start Screen, no default — the choice is the entire feature.
2. **"Playoffs haven't started" copy**: is "The NHL playoffs haven't started yet" appropriate? Or should it be more generic ("No playoff data is available right now") to also cover off-season?
3. **Mode badge styling**: subtle text label or a more visible chip/pill? Recommendation in Story 2 is `AssistChip`-style, but designer should confirm.
4. **Goalie `poolFraction` is set to 1.0** (use the full list). If playtesting reveals too many fringe names, lower to 0.5 as a first tightening.

---

## 8. Recommendation: Drop `minWins` Entirely (Don't Replace It)

The user explicitly asked whether `minWins` should be removed entirely or replaced with a per-mode threshold. **Recommendation: remove it entirely.** Here's why:

### Option A: Remove `minWins` entirely (RECOMMENDED)

**Pros**
- Simpler code: one fewer mechanism in `QuestionType`, one fewer special-case in `buildPools` (the `goalieData["wins"]` lookup goes away).
- Consistent with every other question type — they all rely on `poolFraction` only.
- Naturally handles all timeframes: regular season, early playoffs, late playoffs, off-season — no mode-specific tuning needed.
- `poolFraction = 1.0` uses the full goalie leaderboard; goalies are few enough that there's no long tail of fringe players to worry about.

**Cons**
- A backup with very few games could appear, but the leaderboard endpoint only surfaces goalies who have played enough to post a meaningful save percentage in practice. Worst case — a backup with a hot 5-game stretch shows up — is arguably more interesting trivia, not less. `poolFraction` can be tightened later if needed.

### Option B: Replace `minWins` with a per-mode threshold (e.g. `minWins = 10` for regular season, `minWins = 1` for playoffs)

**Pros**
- More explicit control over who qualifies.

**Cons**
- Adds a `Map<SeasonMode, Int>` (or similar) to `QuestionType`, complicating the enum.
- Still fragile: `minWins = 1` in early playoffs is itself a guess that could be wrong (e.g., during round 1 game 1 when no team has a win yet).
- Requires re-tuning every time the calendar moves through a new phase of the season.

### Option C: Replace with a `minGamesPlayed` threshold

**Cons** (decisive)
- The `goalie-stats-leaders` endpoint **does not return games played** (per research in `claudeOutput/research/goalie-games-played-wins-api.md`). To filter on games played, we'd have to call `/v1/club-stats/{team}/now` for all 32 teams and aggregate. That's 32 extra requests per game start — not worth it.

### Decision

Go with **Option A**. It's the simplest, the most consistent with the rest of the codebase, and naturally resilient to the calendar. `poolFraction = 1.0` for goalies (full list) is the right default given the small goalie population — `poolFraction` remains the single tuning knob and can be tightened later if playtesting reveals a problem. This also aligns with the stated user goal of *simplifying the game*.

---

## 9. Quality Checklist (self-review)

- [x] Every acceptance criterion describes observable user-facing or test-observable behavior.
- [x] Edge cases are enumerated specifically (playoffs not started, double-tap on Start, rotation, transient empty response, etc.).
- [x] Engineering, design, and QA each have actionable information.
- [x] Story dependencies are explicit (Story 1 blocks 2; 2 blocks 3; 4 is independent).
- [x] Story breakdown supports iterative delivery — each story is shippable on its own.
- [x] Out-of-scope items called out only where genuinely ambiguous (mode-mid-game switch, season rollover, mode persistence).
- [x] Recommendation on `minWins` is explicit and justified.
