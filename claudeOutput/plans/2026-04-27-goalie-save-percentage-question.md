# Goalie Save Percentage Question Type Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `GOALIES_SAVE_PCT` as a fifth `QuestionType` backed by a separate goalie stats API endpoint, with a `StatLeader` interface unifying goalie and skater models across the game logic and UI.

**Architecture:** A `StatLeader` interface extracts the common fields and `displayValue` contract shared by `SkaterStatLeader` and the new `GoalieStatLeader`. `TriviaViewModel` fetches both skater and goalie endpoints sequentially, building a unified `Map<QuestionType, List<StatLeader>>` pool. `QuestionType` gains `poolFraction` (replaces the hardcoded `/ 2.0`) and `minWins` (goalie-only wins filter) fields.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt DI, OkHttp, MockWebServer (tests)

---

## File Map

| Action | Path |
|--------|------|
| Create | `app/src/main/java/com/example/pucktrivia/model/StatLeader.kt` |
| Create | `app/src/main/java/com/example/pucktrivia/model/GoalieStatLeader.kt` |
| Modify | `app/src/main/java/com/example/pucktrivia/model/SkaterStatLeader.kt` |
| Modify | `app/src/main/java/com/example/pucktrivia/model/QuestionType.kt` |
| Modify | `app/src/main/java/com/example/pucktrivia/di/StatsUrl.kt` |
| Modify | `app/src/main/java/com/example/pucktrivia/di/NetworkModule.kt` |
| Modify | `app/src/main/java/com/example/pucktrivia/TriviaViewModel.kt` |
| Modify | `app/src/main/java/com/example/pucktrivia/TriviaQuestionScreen.kt` |
| Create | `app/src/test/java/com/example/pucktrivia/GoalieStatLeaderTest.kt` |
| Create | `app/src/test/java/com/example/pucktrivia/SkaterStatLeaderTest.kt` |
| Create | `app/src/test/java/com/example/pucktrivia/QuestionTypeTest.kt` |
| Create | `app/src/test/java/com/example/pucktrivia/GoalieQuestionTypeTest.kt` |
| Modify | `app/src/test/java/com/example/pucktrivia/GoalsQuestionTypeTest.kt` |
| Modify | `app/src/test/java/com/example/pucktrivia/PlayerPoolTest.kt` |
| Modify | `app/src/test/java/com/example/pucktrivia/TriviaViewModelTest.kt` |
| Modify | `app/src/test/java/com/example/pucktrivia/TriviaNoTieTest.kt` |
| Modify | `app/src/test/java/com/example/pucktrivia/LivesSystemTest.kt` |

---

### Task 1: StatLeader interface

**Files:**
- Create: `app/src/main/java/com/example/pucktrivia/model/StatLeader.kt`

- [ ] **Step 1: Create `StatLeader.kt`**

```kotlin
package com.example.pucktrivia.model

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

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/pucktrivia/model/StatLeader.kt
git commit -m "add StatLeader interface"
```

---

### Task 2: GoalieStatLeader model

**Files:**
- Create: `app/src/main/java/com/example/pucktrivia/model/GoalieStatLeader.kt`
- Create: `app/src/test/java/com/example/pucktrivia/GoalieStatLeaderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.pucktrivia

import com.example.pucktrivia.model.GoalieStatLeader
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalieStatLeaderTest {

    @Test
    fun `displayValue formats save percentage as 3-decimal string`() {
        val goalie = GoalieStatLeader(
            id = 1, firstName = "Marc", lastName = "Fleury",
            sweaterNumber = 29, teamAbbrev = "MIN", value = 0.9254,
        )
        assertEquals("0.925", goalie.displayValue)
    }

    @Test
    fun `displayValue rounds up at 5`() {
        val goalie = GoalieStatLeader(
            id = 2, firstName = "Carey", lastName = "Price",
            sweaterNumber = 31, teamAbbrev = "MTL", value = 0.9256,
        )
        assertEquals("0.926", goalie.displayValue)
    }

    @Test
    fun `implements StatLeader interface`() {
        val goalie = GoalieStatLeader(
            id = 42, firstName = "Tuukka", lastName = "Rask",
            sweaterNumber = null, teamAbbrev = "BOS", value = 0.910,
        )
        assertEquals(42, goalie.id)
        assertEquals("Tuukka", goalie.firstName)
        assertEquals("Rask", goalie.lastName)
        assertEquals(null, goalie.sweaterNumber)
        assertEquals("BOS", goalie.teamAbbrev)
        assertEquals(0.910, goalie.value, 0.001)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew test --tests "com.example.pucktrivia.GoalieStatLeaderTest"
```

Expected: BUILD FAILED — `GoalieStatLeader` does not exist yet.

- [ ] **Step 3: Create `GoalieStatLeader.kt`**

```kotlin
package com.example.pucktrivia.model

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

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew test --tests "com.example.pucktrivia.GoalieStatLeaderTest"
```

Expected: BUILD SUCCESSFUL, all 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/pucktrivia/model/GoalieStatLeader.kt \
        app/src/test/java/com/example/pucktrivia/GoalieStatLeaderTest.kt
git commit -m "add GoalieStatLeader model with displayValue"
```

---

### Task 3: SkaterStatLeader implements StatLeader

**Files:**
- Modify: `app/src/main/java/com/example/pucktrivia/model/SkaterStatLeader.kt`
- Create: `app/src/test/java/com/example/pucktrivia/SkaterStatLeaderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.pucktrivia

import com.example.pucktrivia.model.SkaterStatLeader
import com.example.pucktrivia.model.StatLeader
import org.junit.Assert.assertEquals
import org.junit.Test

class SkaterStatLeaderTest {

    @Test
    fun `displayValue returns integer string of value`() {
        val skater = SkaterStatLeader(1, "Alice", "Player", 10, "TST", "C", 80.0)
        assertEquals("80", skater.displayValue)
    }

    @Test
    fun `displayValue truncates decimal`() {
        val skater = SkaterStatLeader(2, "Bob", "Player", 11, "TST", "C", 42.9)
        assertEquals("42", skater.displayValue)
    }

    @Test
    fun `implements StatLeader interface`() {
        val skater: StatLeader = SkaterStatLeader(5, "Carol", "Player", null, "EDM", "L", 100.0)
        assertEquals(5, skater.id)
        assertEquals("100", skater.displayValue)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew test --tests "com.example.pucktrivia.SkaterStatLeaderTest"
```

Expected: BUILD FAILED — `SkaterStatLeader` does not implement `StatLeader` and has no `displayValue`.

- [ ] **Step 3: Update `SkaterStatLeader.kt`**

```kotlin
package com.example.pucktrivia.model

data class SkaterStatLeader(
    override val id: Int,
    override val firstName: String,
    override val lastName: String,
    override val sweaterNumber: Int?,
    override val teamAbbrev: String,
    val position: String,
    override val value: Double,
) : StatLeader {
    override val displayValue: String get() = value.toInt().toString()
}
```

- [ ] **Step 4: Run all tests**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL — all existing tests pass plus the 3 new `SkaterStatLeaderTest` tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/pucktrivia/model/SkaterStatLeader.kt \
        app/src/test/java/com/example/pucktrivia/SkaterStatLeaderTest.kt
git commit -m "SkaterStatLeader implements StatLeader, add displayValue"
```

---

### Task 4: QuestionType additions

**Files:**
- Modify: `app/src/main/java/com/example/pucktrivia/model/QuestionType.kt`
- Create: `app/src/test/java/com/example/pucktrivia/QuestionTypeTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.pucktrivia

import com.example.pucktrivia.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class QuestionTypeTest {

    @Test
    fun `GOALIES_SAVE_PCT has null positionGroup`() {
        assertNull(QuestionType.GOALIES_SAVE_PCT.positionGroup)
    }

    @Test
    fun `GOALIES_SAVE_PCT has poolFraction of 1`() {
        assertEquals(1.0, QuestionType.GOALIES_SAVE_PCT.poolFraction, 0.001)
    }

    @Test
    fun `GOALIES_SAVE_PCT has minWins of 10`() {
        assertEquals(10, QuestionType.GOALIES_SAVE_PCT.minWins)
    }

    @Test
    fun `GOALIES_SAVE_PCT has correct statKey`() {
        assertEquals("savePctg", QuestionType.GOALIES_SAVE_PCT.statKey)
    }

    @Test
    fun `GOALIES_SAVE_PCT has correct question text`() {
        assertEquals(
            "Which of these goalies currently has the highest save percentage?",
            QuestionType.GOALIES_SAVE_PCT.questionText,
        )
    }

    @Test
    fun `all skater types have poolFraction of 0point5`() {
        val skaterTypes = listOf(
            QuestionType.DEFENDERS_POINTS,
            QuestionType.FORWARDS_POINTS,
            QuestionType.DEFENDERS_GOALS,
            QuestionType.FORWARDS_GOALS,
        )
        for (type in skaterTypes) {
            assertEquals("$type should have poolFraction 0.5", 0.5, type.poolFraction, 0.001)
        }
    }

    @Test
    fun `all skater types have minWins of 0`() {
        val skaterTypes = listOf(
            QuestionType.DEFENDERS_POINTS,
            QuestionType.FORWARDS_POINTS,
            QuestionType.DEFENDERS_GOALS,
            QuestionType.FORWARDS_GOALS,
        )
        for (type in skaterTypes) {
            assertEquals("$type should have minWins 0", 0, type.minWins)
        }
    }

    @Test
    fun `all skater types have non-null positionGroup`() {
        val skaterTypes = listOf(
            QuestionType.DEFENDERS_POINTS,
            QuestionType.FORWARDS_POINTS,
            QuestionType.DEFENDERS_GOALS,
            QuestionType.FORWARDS_GOALS,
        )
        for (type in skaterTypes) {
            assertNotNull("$type should have non-null positionGroup", type.positionGroup)
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew test --tests "com.example.pucktrivia.QuestionTypeTest"
```

Expected: BUILD FAILED — `GOALIES_SAVE_PCT`, `poolFraction`, and `minWins` do not exist yet.

- [ ] **Step 3: Update `QuestionType.kt`**

```kotlin
package com.example.pucktrivia.model

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

- [ ] **Step 4: Run all tests**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL. `QuestionTypeTest` passes. All existing tests pass — the `positionGroup()` extension on `SkaterStatLeader` is separate and unaffected by the nullable change.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/pucktrivia/model/QuestionType.kt \
        app/src/test/java/com/example/pucktrivia/QuestionTypeTest.kt
git commit -m "QuestionType: add poolFraction, minWins, nullable positionGroup, GOALIES_SAVE_PCT"
```

---

### Task 5: GoalieStatsUrl DI qualifier and NetworkModule

**Files:**
- Modify: `app/src/main/java/com/example/pucktrivia/di/StatsUrl.kt`
- Modify: `app/src/main/java/com/example/pucktrivia/di/NetworkModule.kt`

- [ ] **Step 1: Add `@GoalieStatsUrl` to `StatsUrl.kt`**

```kotlin
package com.example.pucktrivia.di

import javax.inject.Qualifier

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class StatsUrl

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class GoalieStatsUrl

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher
```

- [ ] **Step 2: Add goalie URL provider to `NetworkModule.kt`**

```kotlin
package com.example.pucktrivia.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    @StatsUrl
    fun provideStatsUrl(): String =
        "https://api-web.nhle.com/v1/skater-stats-leaders/current?limit=-1"

    @Provides
    @GoalieStatsUrl
    fun provideGoalieStatsUrl(): String =
        "https://api-web.nhle.com/v1/goalie-stats-leaders/current?limit=-1"

    @Provides @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @Singleton fun provideRandom(): Random = Random
}
```

- [ ] **Step 3: Run all tests**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL. No test changes needed yet — the annotation is only wired once the ViewModel constructor is updated in Task 7.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/pucktrivia/di/StatsUrl.kt \
        app/src/main/java/com/example/pucktrivia/di/NetworkModule.kt
git commit -m "add GoalieStatsUrl DI qualifier and goalie stats URL provider"
```

---

### Task 6: Write failing GoalieQuestionTypeTest — fetch and parse stories

**Files:**
- Create: `app/src/test/java/com/example/pucktrivia/GoalieQuestionTypeTest.kt`

The ViewModel makes two HTTP calls in order: skater stats first, then goalie stats. `MockWebServer` serves responses in enqueue order regardless of URL, so each test enqueues a skater response (usually `{}`) followed by a goalie response. These tests compile but fail until the ViewModel is updated in Task 7.

- [ ] **Step 1: Create `GoalieQuestionTypeTest.kt`** with fetch and parse stories

```kotlin
package com.example.pucktrivia

import com.example.pucktrivia.model.GoalieStatLeader
import com.example.pucktrivia.model.QuestionType
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalieQuestionTypeTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        mockWebServer.shutdown()
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun goalieJson(id: Int, firstName: String, lastName: String, value: Double) =
        """{"id":$id,"firstName":{"default":"$firstName"},"lastName":{"default":"$lastName"},"sweaterNumber":${id + 29},"teamAbbrev":"TST","position":"G","value":$value}"""

    /**
     * Builds a full goalie stats JSON response with savePctg and wins categories.
     * Each triple is (id, firstName, value).
     */
    private fun createGoalieStatsJson(
        savePctgGoalies: List<Triple<Int, String, Double>>,
        winsGoalies: List<Triple<Int, String, Double>>,
    ): String {
        val savePctgJson = savePctgGoalies.joinToString(",") { (id, name, value) ->
            goalieJson(id, name, "Goalie", value)
        }
        val winsJson = winsGoalies.joinToString(",") { (id, name, value) ->
            goalieJson(id, name, "Goalie", value)
        }
        return """{"savePctg":[$savePctgJson],"wins":[$winsJson]}"""
    }

    private fun skaterUrl() =
        mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()

    private fun goalieUrl() =
        mockWebServer.url("/v1/goalie-stats-leaders/current?limit=-1").toString()

    private fun createViewModel(random: Random = Random(42)): TriviaViewModel =
        TriviaViewModel(OkHttpClient(), skaterUrl(), goalieUrl(), testDispatcher, random)

    /** Enqueues "{}" for the skater call, then the given goalie JSON. */
    private fun enqueueGoalieOnly(goalieJson: String) {
        mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setBody(goalieJson).setResponseCode(200))
    }

    // Standard 5 qualifying goalies (all have 10+ wins), distinct SV%
    private val defaultSavePctg = listOf(
        Triple(1, "Fleury", 0.930),
        Triple(2, "Price", 0.920),
        Triple(3, "Rask", 0.915),
        Triple(4, "Quick", 0.910),
        Triple(5, "Rinne", 0.905),
    )
    private val defaultWins = listOf(
        Triple(1, "Fleury", 35.0),
        Triple(2, "Price", 30.0),
        Triple(3, "Rask", 28.0),
        Triple(4, "Quick", 25.0),
        Triple(5, "Rinne", 15.0),
    )

    // -----------------------------------------------------------------------
    // Story 1: Fetch and parse goalie data
    // -----------------------------------------------------------------------

    @Test
    fun `goalie data is stored in goalieStatsData under savePctg key`() =
        runTest(testDispatcher) {
            val json = createGoalieStatsJson(defaultSavePctg, defaultWins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertNotNull(viewModel.goalieStatsData["savePctg"])
            assertEquals(5, viewModel.goalieStatsData["savePctg"]!!.size)
        }

    @Test
    fun `goalie entries have correct fields`() =
        runTest(testDispatcher) {
            val json = createGoalieStatsJson(defaultSavePctg, defaultWins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            advanceUntilIdle()

            val goalie = viewModel.goalieStatsData["savePctg"]!!.first()
            assertEquals(1, goalie.id)
            assertEquals("Fleury", goalie.firstName)
            assertEquals("Goalie", goalie.lastName)
            assertEquals("TST", goalie.teamAbbrev)
            assertEquals(0.930, goalie.value, 0.001)
        }

    @Test
    fun `goalie entries are GoalieStatLeader instances`() =
        runTest(testDispatcher) {
            val json = createGoalieStatsJson(defaultSavePctg, defaultWins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            advanceUntilIdle()

            val goalie = viewModel.goalieStatsData["savePctg"]!!.first()
            assertTrue(goalie is GoalieStatLeader)
        }

    @Test
    fun `empty goalie response builds no goalie pool and game continues with skater types`() =
        runTest(testDispatcher) {
            val skaterJson = """{"points":[
                {"id":1,"firstName":{"default":"A"},"lastName":{"default":"P"},"sweaterNumber":11,"teamAbbrev":"TST","position":"C","value":100.0},
                {"id":2,"firstName":{"default":"B"},"lastName":{"default":"P"},"sweaterNumber":12,"teamAbbrev":"TST","position":"C","value":80.0},
                {"id":3,"firstName":{"default":"C"},"lastName":{"default":"P"},"sweaterNumber":13,"teamAbbrev":"TST","position":"C","value":60.0},
                {"id":4,"firstName":{"default":"D"},"lastName":{"default":"P"},"sweaterNumber":14,"teamAbbrev":"TST","position":"C","value":40.0},
                {"id":5,"firstName":{"default":"E"},"lastName":{"default":"P"},"sweaterNumber":15,"teamAbbrev":"TST","position":"C","value":20.0},
                {"id":6,"firstName":{"default":"F"},"lastName":{"default":"P"},"sweaterNumber":16,"teamAbbrev":"TST","position":"C","value":10.0}
            ]}"""
            mockWebServer.enqueue(MockResponse().setBody(skaterJson).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertNull(viewModel.pools[QuestionType.GOALIES_SAVE_PCT])
            assertEquals(3, viewModel.choices.size)
            assertNotNull(viewModel.correctPlayer)
        }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew test --tests "com.example.pucktrivia.GoalieQuestionTypeTest"
```

Expected: BUILD FAILED — `TriviaViewModel` constructor has no `goalieStatsUrl` parameter and `goalieStatsData` field doesn't exist.

---

### Task 7: Update TriviaViewModel and all existing tests

**Files:**
- Modify: `app/src/main/java/com/example/pucktrivia/TriviaViewModel.kt`
- Modify: `app/src/test/java/com/example/pucktrivia/TriviaViewModelTest.kt`
- Modify: `app/src/test/java/com/example/pucktrivia/TriviaNoTieTest.kt`
- Modify: `app/src/test/java/com/example/pucktrivia/LivesSystemTest.kt`
- Modify: `app/src/test/java/com/example/pucktrivia/GoalsQuestionTypeTest.kt`
- Modify: `app/src/test/java/com/example/pucktrivia/PlayerPoolTest.kt`

- [ ] **Step 1: Replace `TriviaViewModel.kt`**

```kotlin
package com.example.pucktrivia

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pucktrivia.di.GoalieStatsUrl
import com.example.pucktrivia.di.IoDispatcher
import com.example.pucktrivia.di.StatsUrl
import com.example.pucktrivia.model.GoalieStatLeader
import com.example.pucktrivia.model.QuestionType
import com.example.pucktrivia.model.SkaterStatLeader
import com.example.pucktrivia.model.StatLeader
import com.example.pucktrivia.model.positionGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@HiltViewModel
class TriviaViewModel
@Inject
constructor(
    private val client: OkHttpClient,
    @StatsUrl private val statsUrl: String,
    @GoalieStatsUrl private val goalieStatsUrl: String,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val random: kotlin.random.Random = kotlin.random.Random,
) : ViewModel() {

    var statsData by mutableStateOf<Map<String, List<SkaterStatLeader>>>(emptyMap())
        private set

    var goalieStatsData by mutableStateOf<Map<String, List<GoalieStatLeader>>>(emptyMap())
        internal set

    var isLoading by mutableStateOf(true)
        private set

    var loadError by mutableStateOf(false)
        private set

    var score by mutableIntStateOf(0)
        private set

    var roundNumber by mutableIntStateOf(0)
        private set

    var pools by mutableStateOf<Map<QuestionType, List<StatLeader>>>(emptyMap())
        internal set

    var usedIds by mutableStateOf<Map<QuestionType, Set<Int>>>(emptyMap())
        internal set

    var selectedPlayerId by mutableStateOf<Int?>(null)
        private set

    var choices by mutableStateOf<List<StatLeader>>(emptyList())
        private set

    var correctPlayer by mutableStateOf<StatLeader?>(null)
        private set

    var questionText by mutableStateOf("")
        private set

    var statUnitLabel by mutableStateOf("pts")
        private set

    var lives by mutableIntStateOf(3)
        private set

    var totalAnswered by mutableIntStateOf(0)
        private set

    var correctAnswered by mutableIntStateOf(0)
        private set

    var gameOver by mutableStateOf(false)
        private set

    var fatalError by mutableStateOf(false)
        private set

    val answered: Boolean
        get() = selectedPlayerId != null

    val isCorrect: Boolean
        get() = selectedPlayerId == correctPlayer?.id

    init {
        fetchStats()
    }

    private fun fetchStats() {
        viewModelScope.launch {
            try {
                val skaterData = fetchSkaterStats()
                val goalieData = fetchGoalieStats()
                statsData = skaterData
                goalieStatsData = goalieData
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

    fun selectAnswer(playerId: Int) {
        selectedPlayerId = playerId
        totalAnswered++
        if (playerId == correctPlayer?.id) {
            score += 100
            correctAnswered++
        } else {
            lives = maxOf(0, lives - 1)
        }
    }

    fun nextRound() {
        roundNumber++
        selectedPlayerId = null
        if (lives == 0) {
            gameOver = true
            return
        }
        prepareRound()
    }

    fun resetGame() {
        lives = 3
        score = 0
        totalAnswered = 0
        correctAnswered = 0
        selectedPlayerId = null
        gameOver = false
        fatalError = false
        usedIds = emptyMap()
        prepareRound()
    }

    private fun buildPools(
        skaterData: Map<String, List<SkaterStatLeader>>,
        goalieData: Map<String, List<GoalieStatLeader>>,
    ) {
        val built = mutableMapOf<QuestionType, List<StatLeader>>()
        for (type in QuestionType.entries) {
            if (type.positionGroup != null) {
                val players = skaterData[type.statKey] ?: continue
                val group = players.filter { it.positionGroup() == type.positionGroup }
                if (group.isEmpty()) continue
                val sorted = group.sortedByDescending { it.value }
                built[type] = sorted.take(kotlin.math.ceil(sorted.size * type.poolFraction).toInt())
            } else {
                val savePctgList = goalieData[type.statKey] ?: continue
                val winsList = goalieData["wins"] ?: emptyList()
                val qualifiedIds = winsList
                    .filter { it.value >= type.minWins }
                    .map { it.id }
                    .toSet()
                val filtered = savePctgList.filter { it.id in qualifiedIds }
                if (filtered.isEmpty()) continue
                val sorted = filtered.sortedByDescending { it.value }
                built[type] = sorted.take(kotlin.math.ceil(sorted.size * type.poolFraction).toInt())
            }
        }
        pools = built
    }

    private fun prepareRound() {
        if (pools.isEmpty()) {
            Log.e("TriviaViewModel", "No pools available — cannot prepare round")
            fatalError = true
            return
        }

        val types = pools.keys.toList()
        val type = types[random.nextInt(types.size)]
        val pool = pools[type]!!
        var currentUsed = usedIds[type] ?: emptySet()

        var picked = greedyPick(pool, currentUsed)
        if (picked.size < 3) {
            currentUsed = emptySet()
            picked = greedyPick(pool, currentUsed)
        }
        if (picked.size < 3) {
            Log.e(
                "TriviaViewModel",
                "Pool for $type cannot produce 3 distinct choices even after reset " +
                    "(poolSize=${pool.size}, distinctValues=${pool.distinctBy { it.value }.size})",
            )
            fatalError = true
            return
        }

        questionText = type.questionText
        statUnitLabel = type.unitLabel
        usedIds = usedIds + (type to (currentUsed + picked.map { it.id }))
        choices = picked
        correctPlayer = picked.maxByOrNull { it.value }
    }

    private fun greedyPick(
        pool: List<StatLeader>,
        usedIds: Set<Int>,
    ): List<StatLeader> {
        val unused = pool.filter { it.id !in usedIds }.shuffled(random)
        val claimedValues = mutableSetOf<Double>()
        val result = mutableListOf<StatLeader>()
        for (player in unused) {
            if (player.value !in claimedValues) {
                claimedValues.add(player.value)
                result.add(player)
                if (result.size == 3) break
            }
        }
        return result
    }

    private suspend fun fetchSkaterStats(): Map<String, List<SkaterStatLeader>> =
        withContext(ioDispatcher) {
            val request = Request.Builder().url(statsUrl).build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body!!.string())

            val result = mutableMapOf<String, List<SkaterStatLeader>>()
            for (key in json.keys()) {
                val playersArray = json.getJSONArray(key)
                val players = mutableListOf<SkaterStatLeader>()
                for (i in 0 until playersArray.length()) {
                    val player = playersArray.getJSONObject(i)
                    players.add(
                        SkaterStatLeader(
                            id = player.getInt("id"),
                            firstName = player.getJSONObject("firstName").getString("default"),
                            lastName = player.getJSONObject("lastName").getString("default"),
                            sweaterNumber = player.optInt("sweaterNumber", -1).takeIf { it != -1 },
                            teamAbbrev = player.getString("teamAbbrev"),
                            position = player.getString("position"),
                            value = player.getDouble("value"),
                        )
                    )
                }
                result[key] = players
            }
            result
        }

    private suspend fun fetchGoalieStats(): Map<String, List<GoalieStatLeader>> =
        withContext(ioDispatcher) {
            val request = Request.Builder().url(goalieStatsUrl).build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body!!.string())

            val result = mutableMapOf<String, List<GoalieStatLeader>>()
            for (key in json.keys()) {
                val playersArray = json.getJSONArray(key)
                val players = mutableListOf<GoalieStatLeader>()
                for (i in 0 until playersArray.length()) {
                    val player = playersArray.getJSONObject(i)
                    players.add(
                        GoalieStatLeader(
                            id = player.getInt("id"),
                            firstName = player.getJSONObject("firstName").getString("default"),
                            lastName = player.getJSONObject("lastName").getString("default"),
                            sweaterNumber = player.optInt("sweaterNumber", -1).takeIf { it != -1 },
                            teamAbbrev = player.getString("teamAbbrev"),
                            value = player.getDouble("value"),
                        )
                    )
                }
                result[key] = players
            }
            result
        }
}
```

- [ ] **Step 2: Update `TriviaViewModelTest.kt`** — update `enqueueDefaultResponse()` and `createViewModel()`

Replace these two methods (leave all test methods unchanged):

```kotlin
private fun enqueueDefaultResponse() {
    mockWebServer.enqueue(MockResponse().setBody(createDefaultStatsJson()).setResponseCode(200))
    mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
}

private fun createViewModel(): TriviaViewModel {
    val skaterUrl = mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()
    val goalieUrl = mockWebServer.url("/v1/goalie-stats-leaders/current?limit=-1").toString()
    return TriviaViewModel(OkHttpClient(), skaterUrl, goalieUrl, testDispatcher)
}
```

- [ ] **Step 3: Update `LivesSystemTest.kt`** — update `enqueueDefaultResponse()` and `createViewModel()`

Replace these two methods (leave all test methods unchanged):

```kotlin
private fun enqueueDefaultResponse() {
    mockWebServer.enqueue(MockResponse().setBody(createStatsJson()).setResponseCode(200))
    mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
}

private fun createViewModel(): TriviaViewModel {
    val skaterUrl = mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()
    val goalieUrl = mockWebServer.url("/v1/goalie-stats-leaders/current?limit=-1").toString()
    return TriviaViewModel(OkHttpClient(), skaterUrl, goalieUrl, testDispatcher)
}
```

- [ ] **Step 4: Update `TriviaNoTieTest.kt`** — update `createViewModel()` and add goalie enqueue to each test

Replace `createViewModel()`:

```kotlin
private fun createViewModel(): TriviaViewModel {
    val skaterUrl = mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()
    val goalieUrl = mockWebServer.url("/v1/goalie-stats-leaders/current?limit=-1").toString()
    return TriviaViewModel(OkHttpClient(), skaterUrl, goalieUrl, testDispatcher)
}
```

In each of the 4 test methods, add a goalie enqueue immediately after the existing `mockWebServer.enqueue(...)` call:

```kotlin
mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200)) // goalie — unused
```

- [ ] **Step 5: Update `GoalsQuestionTypeTest.kt`** — rename URL helper, add goalie URL, update `createViewModel()`, and add goalie enqueue to every test

Replace `mockUrl()` and `createViewModel()`:

```kotlin
private fun skaterMockUrl(): String =
    mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()

private fun goalieMockUrl(): String =
    mockWebServer.url("/v1/goalie-stats-leaders/current?limit=-1").toString()

private fun createViewModel(random: Random): TriviaViewModel =
    TriviaViewModel(OkHttpClient(), skaterMockUrl(), goalieMockUrl(), testDispatcher, random)
```

In every test method, add `mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))` immediately after each existing `mockWebServer.enqueue(...)` call. Every test in this file has exactly one existing enqueue — add the goalie one directly below it.

- [ ] **Step 6: Update `PlayerPoolTest.kt`** — same pattern as `GoalsQuestionTypeTest.kt`

Replace `mockUrl()` and `createViewModel()`:

```kotlin
private fun skaterMockUrl() =
    mockWebServer.url("/v1/skater-stats-leaders/current?limit=-1").toString()

private fun goalieMockUrl() =
    mockWebServer.url("/v1/goalie-stats-leaders/current?limit=-1").toString()

private fun createViewModel(random: Random) =
    TriviaViewModel(OkHttpClient(), skaterMockUrl(), goalieMockUrl(), testDispatcher, random)
```

In every test method, add `mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))` immediately after each existing `mockWebServer.enqueue(...)` call. Every test in this file has exactly one existing enqueue — add the goalie one directly below it.

- [ ] **Step 7: Run all tests**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL. All existing tests pass. `GoalieQuestionTypeTest` fetch/parse tests (from Task 6) now pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/pucktrivia/TriviaViewModel.kt \
        app/src/test/java/com/example/pucktrivia/TriviaViewModelTest.kt \
        app/src/test/java/com/example/pucktrivia/TriviaNoTieTest.kt \
        app/src/test/java/com/example/pucktrivia/LivesSystemTest.kt \
        app/src/test/java/com/example/pucktrivia/GoalsQuestionTypeTest.kt \
        app/src/test/java/com/example/pucktrivia/PlayerPoolTest.kt \
        app/src/test/java/com/example/pucktrivia/GoalieQuestionTypeTest.kt
git commit -m "update ViewModel for dual fetch and StatLeader types; update all tests for goalie URL"
```

---

### Task 8: GoalieQuestionTypeTest — pool construction and question mechanics

**Files:**
- Modify: `app/src/test/java/com/example/pucktrivia/GoalieQuestionTypeTest.kt`

Add the following test methods to the existing `GoalieQuestionTypeTest` class.

- [ ] **Step 1: Add pool construction and question mechanics tests**

```kotlin
    // -----------------------------------------------------------------------
    // Story 2: Pool construction — minWins filter and poolFraction
    // -----------------------------------------------------------------------

    @Test
    fun `GOALIES_SAVE_PCT pool contains only goalies with minWins or more wins`() =
        runTest(testDispatcher) {
            // IDs 1,2,3 have 10+ wins (qualifying). IDs 4,5 have fewer than 10 (filtered out).
            val savePctg = listOf(
                Triple(1, "G1", 0.930),
                Triple(2, "G2", 0.920),
                Triple(3, "G3", 0.915),
                Triple(4, "G4", 0.910),
                Triple(5, "G5", 0.905),
            )
            val wins = listOf(
                Triple(1, "G1", 35.0),
                Triple(2, "G2", 30.0),
                Triple(3, "G3", 10.0),
                Triple(4, "G4", 5.0),
                Triple(5, "G5", 2.0),
            )
            val json = createGoalieStatsJson(savePctg, wins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            advanceUntilIdle()

            val pool = viewModel.pools[QuestionType.GOALIES_SAVE_PCT]!!
            val poolIds = pool.map { it.id }.toSet()
            assertEquals("Pool should contain exactly 3 qualifying goalies", setOf(1, 2, 3), poolIds)
        }

    @Test
    fun `GOALIES_SAVE_PCT pool includes all qualifying goalies (poolFraction = 1point0)`() =
        runTest(testDispatcher) {
            // 6 goalies all with 10+ wins → pool should contain all 6 (not just top 50%)
            val savePctg = (1..6).map { Triple(it, "G$it", 0.930 - it * 0.005) }
            val wins = (1..6).map { Triple(it, "G$it", (40 - it * 3).toDouble()) }
            val json = createGoalieStatsJson(savePctg, wins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            advanceUntilIdle()

            val pool = viewModel.pools[QuestionType.GOALIES_SAVE_PCT]!!
            assertEquals("All 6 qualifying goalies should be in the pool", 6, pool.size)
        }

    @Test
    fun `goalie with exactly minWins wins is included in pool`() =
        runTest(testDispatcher) {
            val savePctg = listOf(
                Triple(1, "G1", 0.930),
                Triple(2, "G2", 0.920),
                Triple(3, "G3", 0.915),
            )
            val wins = listOf(
                Triple(1, "G1", 20.0),
                Triple(2, "G2", 15.0),
                Triple(3, "G3", 10.0), // exactly minWins
            )
            val json = createGoalieStatsJson(savePctg, wins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            advanceUntilIdle()

            val pool = viewModel.pools[QuestionType.GOALIES_SAVE_PCT]!!
            assertTrue("Goalie with exactly 10 wins should be in pool", pool.any { it.id == 3 })
        }

    @Test
    fun `no goalie pool built when all goalies are below minWins`() =
        runTest(testDispatcher) {
            val skaterJson = """{"points":[
                {"id":1,"firstName":{"default":"A"},"lastName":{"default":"P"},"sweaterNumber":11,"teamAbbrev":"TST","position":"C","value":100.0},
                {"id":2,"firstName":{"default":"B"},"lastName":{"default":"P"},"sweaterNumber":12,"teamAbbrev":"TST","position":"C","value":80.0},
                {"id":3,"firstName":{"default":"C"},"lastName":{"default":"P"},"sweaterNumber":13,"teamAbbrev":"TST","position":"C","value":60.0},
                {"id":4,"firstName":{"default":"D"},"lastName":{"default":"P"},"sweaterNumber":14,"teamAbbrev":"TST","position":"C","value":40.0},
                {"id":5,"firstName":{"default":"E"},"lastName":{"default":"P"},"sweaterNumber":15,"teamAbbrev":"TST","position":"C","value":20.0},
                {"id":6,"firstName":{"default":"F"},"lastName":{"default":"P"},"sweaterNumber":16,"teamAbbrev":"TST","position":"C","value":10.0}
            ]}"""
            val savePctg = listOf(
                Triple(1, "G1", 0.930),
                Triple(2, "G2", 0.920),
                Triple(3, "G3", 0.915),
            )
            val wins = listOf(
                Triple(1, "G1", 3.0),
                Triple(2, "G2", 2.0),
                Triple(3, "G3", 1.0),
            )
            mockWebServer.enqueue(MockResponse().setBody(skaterJson).setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setBody(createGoalieStatsJson(savePctg, wins)).setResponseCode(200))
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertNull(viewModel.pools[QuestionType.GOALIES_SAVE_PCT])
        }

    // -----------------------------------------------------------------------
    // Story 3: Question mechanics
    // -----------------------------------------------------------------------

    @Test
    fun `correct answer is goalie with highest save percentage`() =
        runTest(testDispatcher) {
            val json = createGoalieStatsJson(defaultSavePctg, defaultWins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            advanceUntilIdle()

            val maxValue = viewModel.choices.maxOf { it.value }
            assertEquals(maxValue, viewModel.correctPlayer!!.value, 0.001)
        }

    @Test
    fun `goalie choices have distinct save percentage values`() =
        runTest(testDispatcher) {
            val json = createGoalieStatsJson(defaultSavePctg, defaultWins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            advanceUntilIdle()

            val values = viewModel.choices.map { it.value }
            assertEquals(
                "All goalie choice values must be distinct, but got: $values",
                values.size,
                values.distinct().size,
            )
        }

    @Test
    fun `goalie question text is correct`() =
        runTest(testDispatcher) {
            val json = createGoalieStatsJson(defaultSavePctg, defaultWins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(
                "Which of these goalies currently has the highest save percentage?",
                viewModel.questionText,
            )
        }

    @Test
    fun `goalie question has empty unit label`() =
        runTest(testDispatcher) {
            val json = createGoalieStatsJson(defaultSavePctg, defaultWins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals("", viewModel.statUnitLabel)
        }

    @Test
    fun `goalie pool resets independently when exhausted`() =
        runTest(testDispatcher) {
            // 3 qualifying goalies → pool of 3. Round 1 exhausts it. Round 2 resets and reuses.
            val savePctg = listOf(
                Triple(1, "G1", 0.930),
                Triple(2, "G2", 0.920),
                Triple(3, "G3", 0.915),
            )
            val wins = listOf(
                Triple(1, "G1", 20.0),
                Triple(2, "G2", 15.0),
                Triple(3, "G3", 10.0),
            )
            val json = createGoalieStatsJson(savePctg, wins)
            enqueueGoalieOnly(json)
            // Single type available (no skater data) → always GOALIES_SAVE_PCT
            val viewModel = createViewModel(Random(42))
            advanceUntilIdle()

            val round1Ids = viewModel.choices.map { it.id }.toSet()
            assertEquals(3, round1Ids.size)

            viewModel.selectAnswer(viewModel.correctPlayer!!.id)
            viewModel.nextRound()

            val round2Ids = viewModel.choices.map { it.id }.toSet()
            assertTrue(
                "After pool reset, previously seen goalies should reappear",
                round1Ids.intersect(round2Ids).isNotEmpty(),
            )
        }

    @Test
    fun `displayValue of each goalie choice is formatted as 0 dot XXX`() =
        runTest(testDispatcher) {
            val json = createGoalieStatsJson(defaultSavePctg, defaultWins)
            enqueueGoalieOnly(json)
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.choices.forEach { player ->
                assertTrue(
                    "displayValue '${player.displayValue}' should match 0.XXX pattern",
                    player.displayValue.matches(Regex("0\\.\\d{3}")),
                )
            }
        }
```

- [ ] **Step 2: Run all goalie tests**

```bash
./gradlew test --tests "com.example.pucktrivia.GoalieQuestionTypeTest"
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Run the full test suite**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/example/pucktrivia/GoalieQuestionTypeTest.kt
git commit -m "add GoalieQuestionTypeTest: pool construction, mechanics, and display"
```

---

### Task 9: Update TriviaQuestionScreen

**Files:**
- Modify: `app/src/main/java/com/example/pucktrivia/TriviaQuestionScreen.kt`

`MainActivity.kt` passes `viewModel.choices` directly with no type annotation — it compiles automatically once the ViewModel state type changes. No changes to `MainActivity.kt` are needed.

- [ ] **Step 1: Replace `TriviaQuestionScreen.kt`**

```kotlin
package com.example.pucktrivia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.pucktrivia.model.StatLeader

internal val CorrectGreen = Color(0xFF4CAF50)

@Composable
fun TriviaQuestionScreen(
    score: Int,
    lives: Int,
    livesColor: Color,
    questionText: String,
    statUnitLabel: String,
    choices: List<StatLeader>,
    selectedPlayerId: Int?,
    correctPlayerId: Int,
    answered: Boolean,
    isCorrect: Boolean,
    onAnswerSelected: (Int) -> Unit,
    onNextRound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Score: $score",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Lives: $lives",
                style = MaterialTheme.typography.headlineLarge,
                color = livesColor,
            )
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (answered) {
                    Text(
                        text = if (isCorrect) "Correct!" else "Incorrect!",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isCorrect) CorrectGreen else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = questionText,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            choices.forEach { player ->
                val containerColor =
                    when {
                        !answered -> MaterialTheme.colorScheme.primary
                        player.id == correctPlayerId -> CorrectGreen
                        player.id == selectedPlayerId -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }

                Button(
                    onClick = { onAnswerSelected(player.id) },
                    enabled = !answered,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = containerColor,
                            disabledContainerColor = containerColor,
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${player.firstName} ${player.lastName}  ${player.teamAbbrev}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (answered) {
                            Text(
                                text = "${player.displayValue}${if (statUnitLabel.isNotEmpty()) " $statUnitLabel" else ""}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().height(72.dp).padding(top = 24.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (answered) {
                    OutlinedButton(onClick = onNextRound, modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Next", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build the debug APK**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL with no errors.

- [ ] **Step 3: Run all tests**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/pucktrivia/TriviaQuestionScreen.kt
git commit -m "TriviaQuestionScreen: use StatLeader and displayValue for stat rendering"
```
