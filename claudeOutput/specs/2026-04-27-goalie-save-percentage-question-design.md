# Goalie Save Percentage Question Type — Design Spec

**Date:** 2026-04-27  
**Branch:** goalie-question  
**Scope:** Add `GOALIES_SAVE_PCT` as a new `QuestionType`, introducing a `StatLeader` interface and `GoalieStatLeader` model to support a second player type alongside the existing skater types.

---

## Overview

PuckTrivia currently has four question types, all backed by `SkaterStatLeader` data from the NHL skater stats leaders API. This spec adds a fifth question type — goalie save percentage — which requires a separate API endpoint, a new data model, and a shared interface to unify both player types across the game logic and UI.

---

## Model Layer

### `StatLeader` interface (`model/StatLeader.kt`)

Common contract for any player shown in a trivia question. Both skater and goalie models implement it.

```kotlin
interface StatLeader {
    val id: Int
    val firstName: String
    val lastName: String
    val sweaterNumber: Int?
    val teamAbbrev: String
    val value: Double
    val displayValue: String
}
```

`displayValue` is a pre-formatted string for UI display. Each implementation owns its own formatting — the UI never formats raw values.

### `SkaterStatLeader` (updated)

Implements `StatLeader`. No field changes.

```kotlin
override val displayValue: String get() = value.toInt().toString()
```

### `GoalieStatLeader` (new, `model/GoalieStatLeader.kt`)

Implements `StatLeader`. No `position` field — all are goalies.

```kotlin
data class GoalieStatLeader(
    override val id: Int,
    override val firstName: String,
    override val lastName: String,
    override val sweaterNumber: Int?,
    override val teamAbbrev: String,
    override val value: Double,
) : StatLeader {
    override val displayValue: String get() = "%.3f".format(value)
}
```

---

## `QuestionType` (updated)

Two new properties: `poolFraction` (replaces the hardcoded `/ 2.0` in `buildPools`) and `minWins` (goalie-only filter). `positionGroup` becomes nullable — `null` means no position filtering.

```kotlin
enum class QuestionType(
    val statKey: String,
    val positionGroup: PositionGroup?,
    val questionText: String,
    val unitLabel: String,
    val poolFraction: Double = 0.5,
    val minWins: Int = 0,
) {
    DEFENDERS_POINTS(
        statKey = "points",
        positionGroup = PositionGroup.DEFENDERS,
        questionText = "Which of these defenders currently has the most points?",
        unitLabel = "pts",
    ),
    FORWARDS_POINTS(
        statKey = "points",
        positionGroup = PositionGroup.FORWARDS,
        questionText = "Which of these forwards currently has the most points?",
        unitLabel = "pts",
    ),
    DEFENDERS_GOALS(
        statKey = "goals",
        positionGroup = PositionGroup.DEFENDERS,
        questionText = "Which of these defenders currently has the most goals?",
        unitLabel = "g",
    ),
    FORWARDS_GOALS(
        statKey = "goals",
        positionGroup = PositionGroup.FORWARDS,
        questionText = "Which of these forwards currently has the most goals?",
        unitLabel = "g",
    ),
    GOALIES_SAVE_PCT(
        statKey = "savePctg",
        positionGroup = null,
        questionText = "Which of these goalies currently has the highest save percentage?",
        unitLabel = "",
        poolFraction = 1.0,
        minWins = 10,
    ),
}
```

`poolFraction = 1.0` for goalies: the league-wide goalie pool is small enough that no top-50% cut is applied. `minWins = 10` filters out backup/emergency goalies whose SV% is unrepresentative.

---

## DI & Networking

### New qualifier (`di/GoalieStatsUrl.kt`)

```kotlin
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class GoalieStatsUrl
```

### `NetworkModule` addition

```kotlin
@Provides
@GoalieStatsUrl
fun provideGoalieStatsUrl(): String =
    "https://api-web.nhle.com/v1/goalie-stats-leaders/current?limit=-1"
```

Fetching without `categories` returns all four goalie categories (`wins`, `shutouts`, `savePctg`, `goalsAgainstAverage`). `wins` is needed for the minimum-wins filter; the rest are unused for now.

---

## `TriviaViewModel` (updated)

### Constructor

Gains `@GoalieStatsUrl private val goalieStatsUrl: String`.

### State

```kotlin
var pools by mutableStateOf<Map<QuestionType, List<StatLeader>>>(emptyMap())
var choices by mutableStateOf<List<StatLeader>>(emptyList())
var correctPlayer by mutableStateOf<StatLeader?>(null)
```

`statsData` field type stays `Map<String, List<SkaterStatLeader>>` (skater-only). A new `goalieStatsData: Map<String, List<GoalieStatLeader>>` field holds goalie data.

### `fetchStats()`

Fires both fetches and passes both datasets to `buildPools()`:

```kotlin
private fun fetchStats() {
    viewModelScope.launch {
        try {
            val skaterData = fetchSkaterStats()
            val goalieData = fetchGoalieStats()
            buildPools(skaterData, goalieData)
            prepareRound()
        } catch (e: Exception) {
            Log.e("TriviaViewModel", "Failed to fetch stats", e)
            loadError = true
        } finally {
            isLoading = false
        }
    }
}
```

### `fetchGoalieStats()` (new)

Mirrors `fetchSkaterStats()`, parsing response objects into `GoalieStatLeader` (no `position` field parsed).

### `buildPools()` (updated)

```kotlin
private fun buildPools(
    skaterData: Map<String, List<SkaterStatLeader>>,
    goalieData: Map<String, List<GoalieStatLeader>>,
) {
    val built = mutableMapOf<QuestionType, List<StatLeader>>()
    for (type in QuestionType.entries) {
        if (type.positionGroup != null) {
            // Skater path — unchanged logic, poolFraction replaces hardcoded 0.5
            val players = skaterData[type.statKey] ?: continue
            val group = players.filter { it.positionGroup() == type.positionGroup }
            if (group.isEmpty()) continue
            val sorted = group.sortedByDescending { it.value }
            built[type] = sorted.take(ceil(sorted.size * type.poolFraction).toInt())
        } else {
            // Goalie path
            val savePctgList = goalieData[type.statKey] ?: continue
            val winsList = goalieData["wins"] ?: emptyList()
            val qualifiedIds = winsList.filter { it.value >= type.minWins }.map { it.id }.toSet()
            val filtered = savePctgList.filter { it.id in qualifiedIds }
            if (filtered.isEmpty()) continue
            val sorted = filtered.sortedByDescending { it.value }
            built[type] = sorted.take(ceil(sorted.size * type.poolFraction).toInt())
        }
    }
    pools = built
}
```

### `prepareRound()` and `greedyPick()`

No signature changes — they already operate generically on the pool lists, which are now `List<StatLeader>`.

### `usedIds`

No changes. `usedIds` is already `Map<QuestionType, Set<Int>>`, so `GOALIES_SAVE_PCT` is tracked independently as a natural consequence of the existing per-type design. Pool reset logic also works unchanged.

---

## `TriviaQuestionScreen` (updated)

`choices` parameter type changes from `List<SkaterStatLeader>` to `List<StatLeader>`. Stat value rendering uses `displayValue`:

```kotlin
text = "${player.displayValue}${if (statUnitLabel.isNotEmpty()) " $statUnitLabel" else ""}"
```

`GameOverScreen` is unchanged.

---

## Testing

### Existing tests

All existing test files (`PlayerPoolTest`, `GoalsQuestionTypeTest`, `TriviaViewModelTest`, `TriviaNoTieTest`, `LivesSystemTest`) need:
- `createViewModel()` updated to pass a second `goalieStatsUrl` parameter (can point to the mock server with an empty JSON object `{}`; this produces an empty goalie data map, so no `GOALIES_SAVE_PCT` pool is built and the game proceeds with skater types only — the correct fallback)
- No logic changes; skater-only tests are unaffected by the goalie fetch

### New `GoalieQuestionTypeTest`

Covers:
- Goalie data is parsed into `GoalieStatLeader` with correct fields
- `GOALIES_SAVE_PCT` pool includes 100% of wins-filtered goalies (not 50%)
- Goalies below `minWins` threshold are excluded from the pool
- Choices have distinct `value`s (no-tie invariant)
- Correct player is the one with the highest SV%
- `displayValue` is a 3-decimal string (e.g., `"0.925"`)
- Goalie pool resets independently of skater pools
- Missing `savePctg` key falls back gracefully (no goalie pool built, game continues with skater types)
