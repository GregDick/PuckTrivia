# Bug: Goalie question never appears during gameplay

> **Investigated:** 2026-04-29
> **Branch:** `goalie-question`
> **Status:** Root cause identified, fix proposed

---

## Symptom

When a player runs the game, they never see the new `GOALIES_SAVE_PCT` question ("Which of these goalies currently has the highest save percentage?"). Only the four skater questions (forwards/defenders × points/goals) ever appear in the rotation.

---

## Root cause

The `GOALIES_SAVE_PCT` pool is silently dropped during `buildPools()` because **zero goalies pass the `minWins >= 10` filter** when the app is run during the NHL playoffs.

### Evidence — three pieces fit together

#### 1. The DI module hits `/current`

`app/src/main/java/com/example/pucktrivia/di/NetworkModule.kt:25-27`

```kotlin
@Provides
@GoalieStatsUrl
fun provideGoalieStatsUrl(): String =
    "https://api-web.nhle.com/v1/goalie-stats-leaders/current?limit=-1"
```

#### 2. `/current` redirects to the active game type — currently playoffs

Live HEAD request (2026-04-29):

```
GET /v1/goalie-stats-leaders/current?limit=-1
HTTP/2 307
location: https://api-web.nhle.com/v1/goalie-stats-leaders/20252026/3?limit=-1
```

`gameType=3` is **playoffs**. `gameType=2` would be regular season. OkHttp follows redirects by default, so the app receives playoff data without realising it switched.

This is documented in `claudeOutput/research/goalie-games-played-wins-api.md:71`:

> `/current` redirects (HTTP 307) to the **current active game type** — which is playoffs (gameType=3) once the postseason begins. Use explicit `/{season}/{gameType}` for reliable results.

#### 3. Playoff wins are tiny — nothing passes `minWins >= 10`

Live response from `/current` right now:

```
wins: 15 entries, max=4, min=1
shutouts: 2 entries, max=1, min=1
savePctg: 20 entries, max=0.954545, min=0.825
goalsAgainstAverage: 20 entries, max=4.32, min=1.097427
Goalies with >=10 wins in this response: 0
```

The leading goalie has **4 wins** (one per game in round 1). The threshold is **10** (`QuestionType.kt:42` — `minWins = 10`).

#### 4. `buildPools` silently drops the type when filtered list is empty

`app/src/main/java/com/example/pucktrivia/TriviaViewModel.kt:174-183`

```kotlin
} else {
    val savePctgList = goalieData[type.statKey] ?: continue
    val winsList = goalieData["wins"] ?: emptyList()
    val qualifiedIds =
        winsList.filter { it.value >= type.minWins }.map { it.id }.toSet()
    val filtered = savePctgList.filter { it.id in qualifiedIds }
    if (filtered.isEmpty()) continue        // ← drops GOALIES_SAVE_PCT silently
    ...
}
```

`prepareRound()` then picks from `pools.keys` (line 195) — and the goalies key was never added, so it can never be selected.

The behaviour is exactly covered by the test `no goalie pool built when all goalies are below minWins` in `GoalieQuestionTypeTest.kt:246` — that test passes because the design intentionally drops empty pools. The bug is that production data falls into that empty case in April-June every year.

---

## Why skater questions still work

The skater URL has the **same redirect issue** — `/v1/skater-stats-leaders/current?limit=-1` also returns 307 → `/20252026/3`. But skaters have:

- No `minWins`-style threshold
- A `poolFraction = 0.5` that gracefully picks the top half of whatever is returned
- Plenty of playoff scorers (more than 3 distinct values per category)

So the skater pools build successfully from playoff data, just with smaller numbers than the user expects ("McDavid has 5 points?"). This is a related secondary issue but is not the reported bug.

---

## Fix plan

### Approach: pin the URL to a specific season + regular season game type

Drop `/current` and use the explicit `/{season}/{gameType}` form recommended by the research doc. This makes the app behave consistently year-round and matches the data the `minWins=10` threshold was designed for.

Trade-off: during playoffs, the trivia stats reflect the regular season just completed, not the active playoffs. That is the correct call here — playoff sample sizes are too small for "highest save percentage" to be meaningful (a goalie can have a `1.000` save pct after one good period of relief work) and the `minWins=10` threshold encodes exactly that intent.

### Scope of changes

Both URLs should be fixed together — even though only the goalie URL causes the reported bug, the skater URL has the same latent issue (showing playoff scoring numbers when users expect season totals). Fixing both keeps the app coherent.

#### File 1: `app/src/main/java/com/example/pucktrivia/di/NetworkModule.kt`

Replace the two URL providers with explicit-season versions. Use a constant for the season so it's easy to bump each fall.

```kotlin
private const val NHL_SEASON = "20252026"
private const val GAME_TYPE_REGULAR = "2"
private const val BASE = "https://api-web.nhle.com/v1"

@Provides
@StatsUrl
fun provideStatsUrl(): String =
    "$BASE/skater-stats-leaders/$NHL_SEASON/$GAME_TYPE_REGULAR?limit=-1"

@Provides
@GoalieStatsUrl
fun provideGoalieStatsUrl(): String =
    "$BASE/goalie-stats-leaders/$NHL_SEASON/$GAME_TYPE_REGULAR?limit=-1"
```

#### File 2: tests

The mock-server-based tests in `GoalieQuestionTypeTest.kt`, `PlayerPoolTest.kt`, etc., construct mock URLs via `mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1")`. Those paths are arbitrary labels — the test harness pipes whatever URL is provided through `MockWebServer`, so the tests pass regardless of the path string. They do NOT need to change. Confirm by running `./gradlew test` after the URL update.

The one thing worth verifying: no test asserts the literal URL string. A grep for `"current"` in tests turned up only mock paths, not assertions.

### Optional follow-up (separate PR)

Surface the silent drop in `buildPools` with a `Log.w` so future "type X never appears" bugs are easier to spot:

```kotlin
if (filtered.isEmpty()) {
    Log.w("TriviaViewModel", "Dropping $type — no players passed minWins=${type.minWins}")
    continue
}
```

Not required to fix the reported bug, but cheap insurance.

---

## Verification steps

After applying the URL fix:

1. **Live curl check** — confirm the new URL returns regular-season data:
   ```
   curl -sL "https://api-web.nhle.com/v1/goalie-stats-leaders/20252026/2?limit=-1" \
     | python3 -c "import json,sys; d=json.load(sys.stdin); \
       print('Goalies with >=10 wins:', sum(1 for g in d['wins'] if g['value']>=10))"
   ```
   Should print a number well above 3 (probably 30+).

2. **Run unit tests** — `./gradlew test`. All existing goalie tests should still pass.

3. **Manual gameplay** — install debug APK and play through 10–15 questions. The goalie question should appear roughly 1-in-5 of the time (one of five enabled `QuestionType` entries, picked uniformly at random in `prepareRound`).

---

## Files touched

| Action | Path | Reason |
|--------|------|--------|
| Modify | `app/src/main/java/com/example/pucktrivia/di/NetworkModule.kt` | Pin both stats URLs to regular-season explicit form |

That's it. One file, two string constants.
