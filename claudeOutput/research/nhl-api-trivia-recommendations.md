# NHL API — Trivia Game Recommendations

> **Context**: Android trivia game about current NHL stats
> **Season in progress**: 2025-2026 (season ID: `20252026`)
> **Documented**: 2026-03-19

---

## Overview

The NHL Web API provides rich, freely accessible data that is ideal for generating current-season trivia questions. No authentication is required, and data is fresh and updated daily. This document ranks the most useful endpoints, describes the types of trivia questions each can support, and provides concrete question templates with the exact API calls to populate them.

---

## Tier 1 — Essential Endpoints (Build These First)

These endpoints deliver the highest density of trivia-worthy facts and should form the backbone of the game's data layer.

### 1. Skater Stats Leaders

**Endpoint**: `GET /v1/skater-stats-leaders/current?limit=-1`

**Why it's ideal for trivia**: Returns the ranked list of all stat leaders in one call. Every entry is immediately trivia-ready: who leads, by how much, what team they're on. This is the single most valuable endpoint for a trivia game.

**Recommended call**: Fetch all categories with `limit=-1` once per day and cache locally. The response is a single JSON object with arrays for `goals`, `assists`, `points`, `plusMinus`, `penaltyMins`, `goalsPp`, `goalsSh`, `faceoffLeaders`, `toi`.

**Trivia question types:**

| Category | Question Template | Answer Source |
|----------|-----------------|---------------|
| `points` | "Who leads the NHL in points this season?" | `points[0].firstName + lastName` |
| `points` | "How many points does [player] have this season?" | `points[n].value` |
| `goals` | "Which team does the current NHL goals leader play for?" | `goals[0].teamAbbrev` |
| `goals` | "True or false: [player] leads the NHL in goals this season." | `goals[0].id == player.id` |
| `assists` | "Which player has the most assists in the NHL this season?" | `assists[0].firstName + lastName` |
| `plusMinus` | "Who has the best plus/minus rating in the NHL?" | `plusMinus[0].firstName + lastName` |
| `goalsPp` | "Who leads the NHL in power-play goals?" | `goalsPp[0].firstName + lastName` |
| Multiple | "Rank these players from most to fewest points." | Ordering question using top 4 from `points` |
| Multiple | "Which of these players leads the NHL in [stat]?" | Multiple choice using `[category][0]` as answer |

**Example trivia question (generated from live data):**
> "How many points does Connor McDavid have in the 2025-2026 season?"
> Answer: 115

---

### 2. Goalie Stats Leaders

**Endpoint**: `GET /v1/goalie-stats-leaders/current?limit=-1`

**Why it's ideal**: Returns wins, shutouts, save %, and GAA leaders — four distinct question categories from one call.

**Trivia question types:**

| Category | Question Template |
|----------|-----------------|
| `wins` | "Which goalie has the most wins this season?" |
| `wins` | "How many wins does [goalie] have this season?" |
| `shutouts` | "Who leads the NHL in shutouts this season?" |
| `savePctg` | "Which goalie has the best save percentage this season?" |
| `savePctg` | "What is [goalie]'s save percentage this season?" (formatted as .XXX) |
| `goalsAgainstAverage` | "Which goalie has the lowest GAA this season?" |
| `goalsAgainstAverage` | "What is [goalie]'s goals against average?" |

**Example trivia question (from live data):**
> "Which goalie leads the NHL with 6 shutouts this season?"
> Answer: Ilya Sorokin (NYI)

---

### 3. Standings

**Endpoint**: `GET /v1/standings/now`

**Why it's ideal**: 32 teams, all with rich comparative data. Points, wins, streaks, home/road splits, divisional/conference rank — each is a potential question.

**Recommended approach**: Cache daily. Sort the `standings` array by `points` descending for overall standings. Each team entry has ~40 fields, most trivia-worthy.

**Trivia question types:**

| Field | Question Template |
|-------|-----------------|
| `points` | "Which team has the most points in the NHL right now?" |
| `points` | "How many points do the [team] have?" |
| `divisionSequence` | "Which team leads the [division] Division?" |
| `conferenceSequence` | "Which team leads the [conference] Conference?" |
| `wins` | "Which team has the most wins this season?" |
| `goalDifferential` | "Which team has the best goal differential?" |
| `streakCode` + `streakCount` | "The [team] are currently on a [N]-game [win/losing] streak — true or false?" |
| `l10Points` | "Which team has the most points over their last 10 games?" |
| `homeWins` | "Which team has the best home record?" |
| `roadWins` | "Which team has the most road wins?" |
| `otLosses` | "Which team has lost the most games in overtime/shootout?" |
| Multiple | "Rank these teams from most to fewest points: [4 teams]" |
| Multiple | "Which of these teams is currently in a playoff spot?" |

**Example trivia question (from live data):**
> "Which team leads the NHL with 98 points?"
> Answer: Colorado Avalanche

**Example trivia question:**
> "Which Eastern Conference team currently has the most points?"
> Answer: Carolina Hurricanes (92 pts)

---

### 4. Player Landing Page

**Endpoint**: `GET /v1/player/{playerId}/landing`

**Why it's ideal**: One call delivers biographical facts, career totals, current season stats, draft history, and awards — dozens of question types per player.

**Recommended approach**: Pre-load landing pages for ~50 notable players (top point leaders from stats leaders + popular players) and cache. This is richer than any other endpoint.

**Trivia question types:**

| Section | Question Template |
|---------|-----------------|
| `draftDetails.overallPick` | "With what overall pick was [player] selected in the draft?" |
| `draftDetails.year` | "What year was [player] drafted?" |
| `draftDetails.round` | "In what round was [player] drafted?" |
| `careerTotals.regularSeason.goals` | "How many career regular-season goals does [player] have?" |
| `careerTotals.regularSeason.points` | "How many career NHL points does [player] have?" |
| `careerTotals.regularSeason.gamesPlayed` | "How many NHL regular-season games has [player] played?" |
| `careerTotals.playoffs.points` | "How many career playoff points does [player] have?" |
| `featuredStats.regularSeason.subSeason.goals` | "How many goals has [player] scored this season?" |
| `featuredStats.regularSeason.subSeason.points` | "What is [player]'s point total this season?" |
| `awards` | "Which trophy did [player] win in [year]?" |
| `awards` | "How many Art Ross Trophies has [player] won?" |
| `birthCity`, `birthCountry` | "Which country is [player] from?" |
| `position` | "What position does [player] play?" |
| `sweaterNumber` | "What jersey number does [player] wear?" |
| `currentTeamAbbrev` | "Which team does [player] currently play for?" |
| `shootsCatches` | "Does [player] shoot left or right?" |
| `heightInInches` | "How tall is [player]?" (multiple choice) |

**Example trivia question (from live data — McDavid):**
> "With what overall pick was Connor McDavid selected in the 2015 NHL Draft?"
> Answer: 1st overall

**Example trivia question:**
> "How many career NHL points does Connor McDavid have?"
> Answer: 1,197 (as of March 2026)

---

### 5. Club Stats

**Endpoint**: `GET /v1/club-stats/{team}/now`

**Why it's ideal**: Shows all players on a team with their stats — enables team-specific trivia ("Who leads the Maple Leafs in scoring?") and comparative questions within a roster.

**Recommended approach**: Load club stats for all 32 teams once per day (32 calls). Each response contains every player's season stats.

**Trivia question types:**

| Question Template | Data source |
|-----------------|-------------|
| "Who leads the [team] in goals this season?" | Sort `skaters` by `goals` descending |
| "Who leads the [team] in points?" | Sort `skaters` by `points` descending |
| "Who leads the [team] in assists?" | Sort `skaters` by `assists` descending |
| "What is [player]'s shooting percentage this season?" | `skaters[n].shootingPctg` |
| "How many power play goals does [player] have?" | `skaters[n].powerPlayGoals` |
| "Who has the best plus/minus on the [team]?" | Sort `skaters` by `plusMinus` descending |
| "How many wins does [goalie] have?" | `goalies[n].wins` |
| "What is [goalie]'s save percentage?" | `goalies[n].savePercentage` |
| "Which team had the player with the most face-off wins?" | Compare `faceoffWinPctg` across teams |

---

## Tier 2 — Highly Recommended Endpoints

### 6. Team Roster

**Endpoint**: `GET /v1/roster/{team}/current`

**Why it's useful**: Bio data for all players — great for "which team does X play for?" and "who wears #X for the [team]?" questions.

**Trivia question types:**

| Question Template | Data source |
|-----------------|-------------|
| "Who wears #[number] for the [team]?" | Find `sweaterNumber == N` in roster |
| "How many Canadians are on the [team] roster?" | Count `birthCountry == "CAN"` |
| "Which country has the most players on the [team]?" | Group by `birthCountry` |
| "Is [player] a left-handed or right-handed shot?" | `shootsCatches` field |
| "How many forwards are on the [team]?" | Count `forwards` array length |
| "Which [team] player is the tallest?" | Sort by `heightInInches` |

---

### 7. Player Game Log

**Endpoint**: `GET /v1/player/{playerId}/game-log/now`

**Why it's useful**: Recent performance data enables "hot streak" questions and single-game performance trivia.

**Trivia question types:**

| Question Template | Data source |
|-----------------|-------------|
| "How many points did [player] record in his last 5 games?" | Sum `points` from last 5 `gameLog` entries |
| "Has [player] scored a goal in his last 3 games?" | Check `goals > 0` in last 3 entries |
| "Which was [player]'s highest-scoring game this season?" | Max `points` in `gameLog` |
| "How many road goals has [player] scored?" | Sum `goals` where `homeRoadFlag == "R"` |
| "How many power play points has [player] in his last 10 games?" | Sum `powerPlayPoints` from last 10 entries |

---

### 8. Score / Schedule

**Endpoint**: `GET /v1/score/now` or `GET /v1/score/{date}`

**Why it's useful**: Recent game results with team leaders prominently featured. Good for "what happened last night?" type questions and freshness.

**Trivia question types:**

| Question Template | Data source |
|-----------------|-------------|
| "What was the final score of last night's [team] vs [team] game?" | `awayTeam.score` vs `homeTeam.score` |
| "How many games were played in the NHL on [date]?" | `gameWeek[date].numberOfGames` |
| "Did the [team] win or lose on [date]?" | Compare scores |
| "Who led the [team] in goals in their game on [date]?" | `teamLeaders` where `category == "goals"` |
| "Which team had the most shots on goal on [date]?" | Compare `sog` across games |

---

### 9. Gamecenter Boxscore

**Endpoint**: `GET /v1/gamecenter/{gameId}/boxscore`

**Why it's useful**: Deep per-player game stats — best for post-game trivia questions about specific matchups.

**Trivia question types:**

| Question Template | Data source |
|-----------------|-------------|
| "How many goals did [player] score in the [team] vs [team] game on [date]?" | `playerByGameStats[team].forwards[n].goals` |
| "Who had the most hits in the game?" | Sort players by `hits` |
| "What was [team]'s power-play conversion rate?" | `summary.teamGameStats.powerPlay` |
| "How many shots did [team] take?" | `summary.teamGameStats.sog` |
| "Who won the faceoff battle?" | Compare `teamGameStats.faceoffWinningPctg` |
| "Who were the three stars of the game?" | `summary.threeStars` (in landing endpoint) |

---

## Tier 3 — Supplementary Endpoints

### 10. Player Spotlight

**Endpoint**: `GET /v1/player-spotlight`

**Why it's useful**: Small curated list of featured players (typically 10-20). Good for a "players in the spotlight this week" themed question set.

---

### 11. Prospects Roster

**Endpoint**: `GET /v1/prospects/{team}`

**Why it's useful**: "Who is [team]'s top prospect?" or "Which team has [prospect] in their system?" — good for harder difficulty questions.

---

### 12. Draft Picks (historical)

**Endpoint**: `GET /v1/draft-picks/{year}`

**Why it's useful**: "Which team originally drafted [player]?" — especially interesting for players who were traded.

---

## Recommended Data Pipeline for the App

### Daily Refresh Strategy

Cache these responses once per day (or on app launch):

```
Priority 1 — Fetch immediately on launch:
  GET /v1/skater-stats-leaders/current?limit=-1
  GET /v1/goalie-stats-leaders/current?limit=-1
  GET /v1/standings/now

Priority 2 — Fetch in background after launch:
  GET /v1/club-stats/{team}/now  (for all 32 teams, or lazily on demand)
  GET /v1/score/now  (for recent game results)

Priority 3 — Pre-load player details for top players:
  GET /v1/player/{id}/landing  (for top ~50 players)
```

### Suggested Player ID List to Pre-Load

Based on current season prominence and fan interest:

```
8478402  Connor McDavid       EDM  C
8477492  Nathan MacKinnon     COL  C
8476453  Nikita Kucherov      TBL  R
8477934  Leon Draisaitl       EDM  C
8479318  Auston Matthews      TOR  C
8484801  Macklin Celebrini    SJS  C
8476460  Mark Scheifele       WPG  C
8477956  David Pastrnak       BOS  R
8480027  Jason Robertson      DAL  L
8478864  Kirill Kaprizov      MIN  L
8480039  Martin Necas         COL  C
8476883  Andrei Vasilevskiy   TBL  G
8478009  Ilya Sorokin         NYI  G
8484144  Connor Bedard        CHI  C
8479361  Joseph Woll          TOR  G
```

---

## Question Difficulty Tiering

### Easy (broad knowledge, high-profile facts)
- "Who leads the NHL in points right now?"
- "Which team has the most points in the standings?"
- "What team does Connor McDavid play for?"
- "What jersey number does [famous player] wear?"
- "Which division do the Toronto Maple Leafs play in?"

### Medium (specific stats, current season knowledge)
- "How many goals has [points leader] scored this season?"
- "Which team leads the [division] Division?"
- "What is [goalie]'s save percentage this season?"
- "Who leads the [team] in scoring?"
- "With what draft pick was [player] selected?"

### Hard (precise numbers, comparative, historical)
- "How many career playoff points does Connor McDavid have?"
- "Which goalie leads the NHL in shutouts and how many do they have?"
- "Which team has the best road record?"
- "Who has the most shorthanded goals this season?"
- "Which player leads the NHL in faceoff win percentage?"
- "How many career goals does [veteran player] have?"
- "What is [player]'s shooting percentage this season?"

---

## Multiple Choice Generation Strategy

For multiple choice questions, use real data to generate plausible wrong answers:

1. **Numeric stats**: Use other players from the same leaders list. If McDavid has 115 points, distractors can be 97, 111, 80 (other real players' totals).
2. **Team names**: Use teams from the same division or conference as distractors.
3. **Jersey numbers**: Use other common jersey numbers worn across the league.
4. **Draft picks**: If the answer is "1st overall," use "4th overall," "7th overall," "12th overall" as distractors.
5. **Countries**: If the answer is Canada, use USA, Sweden, Russia, Czech Republic (the most common NHL birth countries).

---

## Freshness Considerations

- **Points leaders / standings**: Change every game night. Cache with a TTL of 24 hours or refresh at midnight ET.
- **Rosters**: Change rarely (trades, injuries, call-ups). Safe to cache for 3-7 days.
- **Player bios** (birth date, height, draft info): Never change. Cache indefinitely.
- **Career totals**: Change every few games. Cache for 24 hours.
- **Game results**: Finalized after each game. The score endpoint updates throughout the day.

---

## Real Trivia Question Examples

The following questions are directly generated from live API data retrieved on 2026-03-19:

**Easy:**
1. "Who leads the NHL in points in the 2025-2026 season?"
   - A) Nathan MacKinnon
   - B) **Connor McDavid** ← correct
   - C) Nikita Kucherov
   - D) Leon Draisaitl

2. "Which team leads the entire NHL in points?"
   - A) Dallas Stars
   - B) Carolina Hurricanes
   - C) **Colorado Avalanche** ← correct (98 pts)
   - D) Buffalo Sabres

**Medium:**
3. "How many points does Connor McDavid have in the 2025-2026 season?"
   - A) 97
   - B) 111
   - C) **115** ← correct
   - D) 107

4. "Which goalie leads the NHL with the most wins this season?"
   - A) Jake Oettinger
   - B) Karel Vejmelka
   - C) **Andrei Vasilevskiy** ← correct (31 wins)
   - D) Ilya Sorokin

5. "Who leads the NHL in shutouts this season?"
   - A) Andrei Vasilevskiy
   - B) Scott Wedgewood
   - C) Jake Oettinger
   - D) **Ilya Sorokin** ← correct (6 shutouts)

6. "With what overall pick was Connor McDavid selected in the 2015 NHL Draft?"
   - A) 2nd overall
   - B) **1st overall** ← correct
   - C) 3rd overall
   - D) 4th overall

**Hard:**
7. "What is Scott Wedgewood's save percentage this season?"
   - A) .9060
   - B) .9138
   - C) .9127
   - D) **.9157** ← correct

8. "How many career NHL regular-season points does Connor McDavid have?"
   - A) 1,021
   - B) 1,143
   - C) **1,197** ← correct
   - D) 1,255

9. "Who leads the Colorado Avalanche in points this season?"
   - (Fetch via `/v1/club-stats/COL/now`, sort skaters by points)

10. "How many goals has William Nylander scored for Toronto this season?"
    - A) 18
    - B) 27
    - C) **23** ← correct
    - D) 31

---

## Sources

- Live API responses from `api-web.nhle.com` fetched 2026-03-19
- [GitHub - Zmalski/NHL-API-Reference](https://github.com/Zmalski/NHL-API-Reference)
- [GitHub - coreyjs/nhl-api-py](https://github.com/coreyjs/nhl-api-py)
- [freepublicapis.com - NHL API](https://www.freepublicapis.com/nhl-api-documentation)
