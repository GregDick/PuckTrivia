# Feature Requirements: Skater Stats Leaders Fetch & Display

## Feature Overview

Add the networking layer foundation for PuckTrivia by fetching skater stats leaders from the NHL Web API on app launch and displaying the raw data in a scrollable, readable format. This is the only screen of the app. This is an intentionally minimal first step -- no error handling, no caching, no offline support. The purpose is to prove out the OkHttp + coroutines networking pattern and render real data on screen.

**Base URL**: `https://api-web.nhle.com`
**Endpoint**: `GET /v1/skater-stats-leaders/current?limit=-1`
**Auth**: None required (public API)

**Definition of Done**: The app launches, immediately fetches the skater stats leaders endpoint, and displays the response data in a scrollable list organized by stat category. OkHttp is used for HTTP, Kotlin coroutines for async work.

---

## Story 1: Add OkHttp dependency and INTERNET permission

**As a** developer,
**I want** OkHttp and coroutines dependencies added to the project and the INTERNET permission declared,
**So that** the app can make network requests.

**Story Points:** 1
**Priority:** P0
**Dependencies:** None

### Acceptance Criteria

- [ ] The app declares the `android.permission.INTERNET` permission in `AndroidManifest.xml`
- [ ] OkHttp is available as a dependency (added to the version catalog and `build.gradle.kts`)
- [ ] Kotlin coroutines-android is available as a dependency (added to the version catalog and `build.gradle.kts`; note: `lifecycle-runtime-ktx` already provides coroutine scope, but the coroutines core/android libraries should be explicitly declared)
- [ ] The project builds successfully after these additions

### Engineering Notes

- Add `okhttp` and `kotlinx-coroutines-android` to `gradle/libs.versions.toml` under `[versions]` and `[libraries]`
- Add corresponding `implementation` lines to `app/build.gradle.kts`
- OkHttp 4.x is the current stable line compatible with this project's Kotlin version
- The `INTERNET` permission goes in `app/src/main/AndroidManifest.xml` as a top-level `<uses-permission>` element before `<application>`
- `lifecycle-runtime-ktx` (already present) provides `lifecycleScope` which will be used to launch coroutines -- no ViewModel needed for this minimal step

---

## Story 2: Fetch skater stats leaders on app launch

**As a** user,
**I want** the app to fetch current NHL skater stats leaders as soon as it launches,
**So that** I can see real stats data.

**Story Points:** 3
**Priority:** P0
**Dependencies:** Story 1

### Acceptance Criteria

- [ ] When the app launches, it immediately makes a GET request to `https://api-web.nhle.com/v1/skater-stats-leaders/current?limit=-1`
- [ ] The network request runs off the main thread (on a background coroutine dispatcher)
- [ ] The JSON response is parsed into Kotlin data classes that represent the response structure
- [ ] The parsed data is held in Compose state so the UI recomposes when data arrives
- [ ] While the data is loading, the screen shows a simple loading indicator (e.g., `CircularProgressIndicator`)
- [ ] No error handling is implemented -- if the request fails, the app may crash or show nothing (this is acceptable and intentional)

### Engineering Notes

- Use `OkHttpClient` directly to make the GET request -- do not use Retrofit
- Use `Dispatchers.IO` for the network call
- JSON parsing: use `org.json.JSONObject` (included in Android SDK, no extra dependency) or add `kotlinx-serialization` / `Gson`. Since this is a minimal step, `org.json` is simplest and avoids new dependencies. The choice is left to the implementer, but the lightest path is preferred.
- The response shape (based on API research) is a JSON object where each key is a stat category name (`goals`, `assists`, `points`, `plusMinus`, `penaltyMins`, `goalsPp`, `goalsSh`, `faceoffLeaders`, `toi`) and each value is an array of player objects
- Each player object has: `id` (Int), `firstName` (object with `default` string), `lastName` (object with `default` string), `sweaterNumber` (Int), `headshot` (String URL), `teamAbbrev` (String), `teamName` (object with `default` string, may be absent), `teamLogo` (String URL, may be absent), `position` (String), `value` (Int or contextual type)
- Data classes should model the relevant subset: player name, team abbreviation, position, sweater number, and stat value. Headshot/logo URLs can be captured but are not displayed in this story.
- The fetch should be triggered once in `onCreate` via `lifecycleScope.launch` -- no ViewModel is needed for this minimal step
- Hold parsed results in a `mutableStateOf` or `MutableStateFlow` that Compose observes

### Data Model (suggested)

```kotlin
data class SkaterStatLeader(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val sweaterNumber: Int,
    val teamAbbrev: String,
    val position: String,
    val value: Int
)

// The full response is a Map<String, List<SkaterStatLeader>>
// where keys are category names like "goals", "assists", "points", etc.
```

### QA / Testing Notes

- Verify the network call fires immediately on app launch (check logcat for request timing)
- Verify the app does not freeze or ANR during the network call (it must be off the main thread)
- Test on a device with internet connectivity -- that is the only supported scenario

---

## Story 3: Display skater stats leaders in a scrollable list

**As a** user,
**I want** to see the fetched skater stats leaders displayed in a readable, scrollable format organized by stat category,
**So that** I can browse the data.

**Story Points:** 3
**Priority:** P0
**Dependencies:** Story 2

### Acceptance Criteria

- [ ] After data loads, the screen displays stat categories as section headers (e.g., "Goals", "Assists", "Points", etc.)
- [ ] Under each category header, each player entry shows: rank number, full name (first + last), team abbreviation, position, and stat value
- [ ] Example rendered entry: `1. Connor McDavid - EDM - C - 115`
- [ ] The entire screen is vertically scrollable (the `limit=-1` response contains hundreds of entries across all categories)
- [ ] Category headers are visually distinct from player entries (e.g., larger/bolder text, different color, or a divider)
- [ ] The rank number for each player is derived from their position in the array (1-indexed), not from a field in the response
- [ ] The loading indicator from Story 2 is replaced by the data once it arrives

### Design Notes

- Use `LazyColumn` for the scrollable list -- the dataset is large enough that lazy rendering matters
- Category headers can use `Text` with `MaterialTheme.typography.titleMedium` or `titleLarge`
- Player entries can use `MaterialTheme.typography.bodyMedium`
- Use the existing `Scaffold` and `PuckTriviaTheme` wrapper from `MainActivity`
- Respect edge-to-edge insets (already handled by the existing `Scaffold` + `innerPadding` pattern)
- No images, no player headshots, no team logos -- just text for this step
- Category names from the API are camelCase (`plusMinus`, `goalsPp`, `goalsSh`, `faceoffLeaders`, `toi`) -- display them as human-readable labels (e.g., "Plus/Minus", "Power Play Goals", "Shorthanded Goals", "Faceoff Leaders", "Time on Ice")

### Engineering Notes

- Use `LazyColumn` with `item` for headers and `items` for player lists under each category
- Iterate over the map entries to render each category section
- Consider ordering categories in a sensible default order rather than relying on JSON key iteration order: points, goals, assists, plusMinus, penaltyMins, goalsPp, goalsSh, faceoffLeaders, toi
- The composable should accept the `Map<String, List<SkaterStatLeader>>` as a parameter for testability

### QA / Testing Notes

- Verify all stat categories present in the response are rendered (expect approximately 9 categories)
- Verify scrolling is smooth (LazyColumn, not Column with verticalScroll on hundreds of items)
- Verify rank numbers are sequential and start at 1 within each category
- Verify the loading indicator disappears once data renders
- Spot-check a few player names/values against nhl.com to confirm data is parsed correctly

### Edge Cases

- Some player objects may have optional fields missing (e.g., `teamName`, `teamLogo`, `headshot`). The display only uses `firstName`, `lastName`, `teamAbbrev`, `position`, `value` -- all of which appear to always be present. If a field is missing, the parser should handle it gracefully (default to empty string or 0), but this is not a formal error-handling requirement.
- The `value` field is an integer for most categories but `toi` (time on ice) may be formatted differently. If it parses as an integer, display as-is. Do not add special formatting logic in this step.

---

## Summary of Scope

**In scope:**
- INTERNET permission
- OkHttp + coroutines dependencies
- Single GET request on launch
- JSON parsing into data classes
- Scrollable display of all stat categories and player entries
- Loading indicator while fetching

**Explicitly out of scope (by user request -- do not implement):**
- Error handling (no try/catch UI, no retry, no error states)
- Caching
- Offline support
- Pagination
- Navigation / multiple screens
- Player images or team logos
- ViewModel / architecture components beyond what is minimally needed
- Unit or instrumented tests

---

## File Change Summary (expected)

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Add okhttp and coroutines versions + library entries |
| `app/build.gradle.kts` | Add okhttp and coroutines implementation dependencies |
| `app/src/main/AndroidManifest.xml` | Add INTERNET permission |
| `app/src/main/java/com/example/pucktrivia/MainActivity.kt` | Replace template Greeting with fetch + display logic |
| `app/src/main/java/com/example/pucktrivia/model/SkaterStatLeader.kt` (new) | Data class for parsed player entries |
