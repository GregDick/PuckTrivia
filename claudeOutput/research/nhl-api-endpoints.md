# NHL Web API — Complete Endpoint Reference

> **Base URL**: `https://api-web.nhle.com`
> **All endpoints use HTTP GET**
> **No authentication required**
> **Documented**: 2026-03-19

---

## Table of Contents

1. [Player Endpoints](#1-player-endpoints)
2. [Standings Endpoints](#2-standings-endpoints)
3. [Schedule Endpoints](#3-schedule-endpoints)
4. [Score / Live Game Endpoints](#4-score--live-game-endpoints)
5. [Game Center Endpoints](#5-game-center-endpoints)
6. [Team Roster Endpoints](#6-team-roster-endpoints)
7. [Team Stats Endpoints](#7-team-stats-endpoints)
8. [Stats Leaders Endpoints](#8-stats-leaders-endpoints)
9. [Playoff Endpoints](#9-playoff-endpoints)
10. [Draft Endpoints](#10-draft-endpoints)
11. [NHL Edge (Advanced Analytics) Endpoints](#11-nhl-edge-advanced-analytics-endpoints)
12. [Utility / Meta Endpoints](#12-utility--meta-endpoints)
13. [Secondary Base URL: api.nhle.com/stats/rest](#13-secondary-base-url-apinhlecomstatsrest)

---

## 1. Player Endpoints

### GET /v1/player/{playerId}/landing

Returns comprehensive player profile including bio, current season stats, career totals, season-by-season history, awards, last 5 games, and draft details.

**Parameters:**
- `playerId` (path, integer): NHL player ID (e.g., `8478402` for McDavid)

**Example:**
```
GET https://api-web.nhle.com/v1/player/8478402/landing
```

**Key response sections:** `playerId`, `firstName`, `lastName`, `position`, `sweaterNumber`, `currentTeamAbbrev`, `heightInInches`, `weightInPounds`, `birthDate`, `birthCity`, `birthCountry`, `shootsCatches`, `draftDetails`, `featuredStats`, `careerTotals`, `last5Games`, `seasonTotals`, `awards`, `currentTeamRoster`

---

### GET /v1/player/{playerId}/game-log/{season}/{gameType}

Returns a game-by-game log for a player for a given season and game type.

**Parameters:**
- `playerId` (path, integer): Player ID
- `season` (path, integer): Season in YYYYYYYY format (e.g., `20252026`)
- `gameType` (path, integer): `2` for regular season, `3` for playoffs

**Example:**
```
GET https://api-web.nhle.com/v1/player/8478402/game-log/20252026/2
```

---

### GET /v1/player/{playerId}/game-log/now

Returns the game-by-game log for the current season (dynamically resolves to active season and game type).

**Parameters:**
- `playerId` (path, integer): Player ID

**Example:**
```
GET https://api-web.nhle.com/v1/player/8478402/game-log/now
```

**Key response fields per game entry:** `gameId`, `teamAbbrev`, `homeRoadFlag` (H/R), `gameDate`, `goals`, `assists`, `points`, `plusMinus`, `powerPlayGoals`, `powerPlayPoints`, `shorthandedGoals`, `shorthandedPoints`, `gameWinningGoals`, `otGoals`, `shots`, `shifts`, `pim`, `toi` (MM:SS format), `opponentAbbrev`, `opponentCommonName`

---

### GET /v1/player-spotlight

Returns a curated list of featured/spotlight NHL players for the current season. Used by the NHL website's homepage feature section.

**Parameters:** None

**Example:**
```
GET https://api-web.nhle.com/v1/player-spotlight
```

**Response fields:** `playerId`, `name` (with language variants), `playerSlug`, `position`, `sweaterNumber`, `teamId`, `headshot`, `teamTriCode`, `teamLogo`, `sortId`

---

## 2. Standings Endpoints

### GET /v1/standings/now

Returns current NHL standings for all 32 teams with full win/loss records, points, home/road/last-10 splits, and ranking sequences.

**Parameters:** None

**Example:**
```
GET https://api-web.nhle.com/v1/standings/now
```

**Key response fields:** `wildCardIndicator`, `standingsDateTimeUtc`, `standings` (array of team standing objects)

**Per-team fields:** `teamAbbrev`, `teamName`, `teamCommonName`, `teamLogo`, `seasonId`, `conferenceAbbrev`, `conferenceName`, `conferenceSequence`, `divisionAbbrev`, `divisionName`, `divisionSequence`, `gamesPlayed`, `wins`, `losses`, `otLosses`, `points`, `pointPctg`, `regulationWins`, `regulationPlusOtWins`, `goalFor`, `goalAgainst`, `goalDifferential`, `streakCode`, `streakCount`, `wildcardSequence`, home/road/L10 splits for all major stats

---

### GET /v1/standings/{date}

Returns standings as of a specific date.

**Parameters:**
- `date` (path, string): Date in `YYYY-MM-DD` format

**Example:**
```
GET https://api-web.nhle.com/v1/standings/2026-01-01
```

---

### GET /v1/standings-season

Returns metadata about all historical standing seasons — start/end dates, and which rule features were active (conferences, divisions, wild card, ties, etc.).

**Parameters:** None

**Example:**
```
GET https://api-web.nhle.com/v1/standings-season
```

**Response fields per season:** `id` (season integer), `conferencesInUse`, `divisionsInUse`, `pointForOTlossInUse`, `regulationWinsInUse`, `rowInUse`, `standingsStart`, `standingsEnd`, `tiesInUse`, `wildcardInUse`

---

## 3. Schedule Endpoints

### GET /v1/schedule/now

Returns the current week's schedule organized by day. This is the main schedule endpoint showing upcoming and recent games.

**Parameters:** None

**Example:**
```
GET https://api-web.nhle.com/v1/schedule/now
```

**Response structure:** `nextStartDate`, `previousStartDate`, `gameWeek` (array of day objects, each with `date`, `dayAbbrev`, `numberOfGames`, `games` array)

---

### GET /v1/schedule/{date}

Returns the schedule for the week containing the given date.

**Parameters:**
- `date` (path, string): Date in `YYYY-MM-DD` format

**Example:**
```
GET https://api-web.nhle.com/v1/schedule/2026-03-15
```

---

### GET /v1/schedule/{team}/now

Returns the current remaining schedule for a specific team this season.

**Parameters:**
- `team` (path, string): 3-letter team code (e.g., `TOR`, `EDM`)

**Example:**
```
GET https://api-web.nhle.com/v1/schedule/TOR/now
```

---

### GET /v1/schedule/{team}/{season}

Returns the full season schedule for a team.

**Parameters:**
- `team` (path, string): 3-letter team code
- `season` (path, integer): Season in YYYYYYYY format

**Example:**
```
GET https://api-web.nhle.com/v1/schedule/TOR/20252026
```

---

### GET /v1/schedule-calendar/now

Returns a calendar view of the schedule for the current date range.

**Example:**
```
GET https://api-web.nhle.com/v1/schedule-calendar/now
```

---

### GET /v1/schedule-calendar/{date}

Returns a calendar view of the schedule for the week of the given date.

**Example:**
```
GET https://api-web.nhle.com/v1/schedule-calendar/2026-03-15
```

---

## 4. Score / Live Game Endpoints

### GET /v1/score/now

Returns live scores and game status for all games on the current day, including team leaders displayed in the game tile.

**Parameters:** None

**Example:**
```
GET https://api-web.nhle.com/v1/score/now
```

**Response:** Same structure as `/v1/schedule/now` but with live score data. Game objects include `gameState` (`FUT`, `LIVE`, `OFF`), team scores, and `teamLeaders` (top stat leaders for each team in the matchup).

---

### GET /v1/score/{date}

Returns scores for all games on a specific date.

**Parameters:**
- `date` (path, string): Date in `YYYY-MM-DD` format

**Example:**
```
GET https://api-web.nhle.com/v1/score/2026-03-17
```

---

## 5. Game Center Endpoints

These endpoints provide detailed data for a specific game by its game ID.

### GET /v1/gamecenter/{gameId}/landing

Returns a full game summary including scoring by period, three stars, penalty summary, scoring plays with assist details and highlight clip URLs.

**Parameters:**
- `gameId` (path, integer): Game ID (e.g., `2025021073`)

**Example:**
```
GET https://api-web.nhle.com/v1/gamecenter/2025021073/landing
```

**Key sections:** `awayTeam`/`homeTeam` (with score, SOG), `periodDescriptor`, `summary` (goals by period, penalties by period, three stars), `gameOutcome`, `tvBroadcasts`

---

### GET /v1/gamecenter/{gameId}/boxscore

Returns the full boxscore for a game including per-player stats for skaters (goals, assists, points, plusMinus, pim, hits, powerPlayGoals, shots, faceoffWinningPctg, toi, blockedShots, shifts, giveaways, takeaways) and goalies (shots against by strength, save %, goals against, toi, starter, decision).

**Parameters:**
- `gameId` (path, integer): Game ID

**Example:**
```
GET https://api-web.nhle.com/v1/gamecenter/2025021073/boxscore
```

**Key sections:** `playerByGameStats` (object containing `awayTeam` and `homeTeam`, each with `forwards`, `defense`, `goalies` arrays), `summary` (shots on goal by period, team game stats), `gameOutcome`

---

### GET /v1/gamecenter/{gameId}/play-by-play

Returns every event in the game as a chronological array of play objects.

**Parameters:**
- `gameId` (path, integer): Game ID

**Example:**
```
GET https://api-web.nhle.com/v1/gamecenter/2025021073/play-by-play
```

**Response:** Root fields include `id`, `season`, `gameType`, `gameDate`, `awayTeam`, `homeTeam`, `plays` (array)

**Play object fields:** `eventId`, `periodDescriptor` (period number and type), `timeInPeriod` (MM:SS), `timeRemaining` (MM:SS), `typeCode` (integer), `typeDescKey` (string descriptor), `sortOrder`, `details` (event-specific, see below)

**Play type codes and typeDescKey values:**
| typeCode | typeDescKey | Notable detail fields |
|----------|-------------|----------------------|
| 502 | `faceoff` | `winningPlayerId`, `losingPlayerId`, `zoneCode` |
| 503 | `hit` | `hittingPlayerId`, `hitteePlayerId`, `zoneCode` |
| 504 | `giveaway` | `playerId`, `zoneCode` |
| 505 | `goal` | `scoringPlayerId`, `assist1PlayerId`, `assist2PlayerId`, `shotType`, `xCoord`, `yCoord`, `zoneCode`, `awayScore`, `homeScore` |
| 506 | `shot-on-goal` | `shootingPlayerId`, `goalieInNetId`, `shotType`, `xCoord`, `yCoord` |
| 507 | `missed-shot` | `shootingPlayerId`, `goalieInNetId`, `shotType`, `reason` |
| 508 | `blocked-shot` | `shootingPlayerId`, `blockingPlayerId` |
| 509 | `penalty` | `committedByPlayerId`, `drawnByPlayerId`, `descKey`, `duration`, `typeCode` |
| 516 | `stoppage` | `reason` |
| 520 | `period-start` | — |
| 521 | `period-end` | — |
| 524 | `shootout-complete` | — |
| 525 | `game-end` | — |
| 535 | `takeaway` | `playerId`, `zoneCode` |
| 537 | `delayed-penalty` | — |

---

### GET /v1/gamecenter/{gameId}/story

Returns editorial/story content for a game. Useful for game summaries and narrative content.

**Parameters:**
- `gameId` (path, integer): Game ID

**Example:**
```
GET https://api-web.nhle.com/v1/gamecenter/2025021073/story
```

---

### GET /v1/streams/{gameId}

Returns streaming/broadcast information for a game.

**Parameters:**
- `gameId` (path, integer): Game ID

---

### GET /v1/partner-game-odds/{gameId}

Returns betting odds for a game from partner bookmakers.

**Parameters:**
- `gameId` (path, integer): Game ID

---

## 6. Team Roster Endpoints

### GET /v1/roster/{team}/current

Returns the current active roster for a team, divided into forwards, defensemen, and goalies.

**Parameters:**
- `team` (path, string): 3-letter team code

**Example:**
```
GET https://api-web.nhle.com/v1/roster/TOR/current
```

**Response:** `{ "forwards": [...], "defensemen": [...], "goalies": [...] }`

**Per-player fields:** `id`, `headshot`, `firstName`, `lastName`, `sweaterNumber`, `positionCode` (C/L/R/D/G), `shootsCatches` (L/R), `heightInInches`, `weightInPounds`, `heightInCentimeters`, `weightInKilograms`, `birthDate`, `birthCity`, `birthCountry`, `birthStateProvince`

---

### GET /v1/roster/{team}/now

Alias for current roster (same as `/current`).

**Example:**
```
GET https://api-web.nhle.com/v1/roster/TOR/now
```

---

### GET /v1/roster/{team}/{season}

Returns the roster for a team in a specific season.

**Parameters:**
- `team` (path, string): 3-letter team code
- `season` (path, integer): Season in YYYYYYYY format

**Example:**
```
GET https://api-web.nhle.com/v1/roster/TOR/20242025
```

---

### GET /v1/roster-season/{team}

Returns a list of all seasons for which roster data is available for the given team.

**Parameters:**
- `team` (path, string): 3-letter team code

**Example:**
```
GET https://api-web.nhle.com/v1/roster-season/TOR
```

---

### GET /v1/prospects/{team}

Returns prospect data for a team (players not on the NHL roster but in the organization).

**Parameters:**
- `team` (path, string): 3-letter team code

**Example:**
```
GET https://api-web.nhle.com/v1/prospects/TOR
```

**Response structure:** Same `forwards`/`defensemen`/`goalies` split as roster. Some players may have null `sweaterNumber` (not yet assigned an NHL number).

---

## 7. Team Stats Endpoints

### GET /v1/club-stats/{team}/now

Returns season-to-date statistics for every player on a team's roster (skaters and goalies).

**Parameters:**
- `team` (path, string): 3-letter team code

**Example:**
```
GET https://api-web.nhle.com/v1/club-stats/TOR/now
```

**Response:** `{ "season": "20252026", "gameType": 2, "skaters": [...], "goalies": [...] }`

**Skater fields:** `playerId`, `headshot`, `firstName`, `lastName`, `positionCode`, `gamesPlayed`, `goals`, `assists`, `points`, `plusMinus`, `penaltyMinutes`, `powerPlayGoals`, `shorthandedGoals`, `gameWinningGoals`, `overtimeGoals`, `shots`, `shootingPctg`, `avgTimeOnIcePerGame` (seconds), `avgShiftsPerGame`, `faceoffWinPctg`

**Goalie fields:** `playerId`, `headshot`, `firstName`, `lastName`, `gamesPlayed`, `gamesStarted`, `wins`, `losses`, `overtimeLosses`, `goalsAgainstAverage`, `savePercentage`, `shotsAgainst`, `saves`, `goalsAgainst`, `shutouts`, `goals`, `assists`, `points`, `penaltyMinutes`, `timeOnIce` (seconds)

---

### GET /v1/club-stats/{team}/{season}/{gameType}

Returns season stats for a specific team, season, and game type.

**Parameters:**
- `team` (path, string): 3-letter team code
- `season` (path, integer): Season YYYYYYYY
- `gameType` (path, integer): `2` regular season, `3` playoffs

**Example:**
```
GET https://api-web.nhle.com/v1/club-stats/TOR/20242025/2
```

---

### GET /v1/team-scoreboard/{team}

Returns a scoreboard/recent games view for a specific team.

**Parameters:**
- `team` (path, string): 3-letter team code

**Example:**
```
GET https://api-web.nhle.com/v1/team-scoreboard/TOR
```

---

## 8. Stats Leaders Endpoints

### GET /v1/skater-stats-leaders/current

Returns the current season's stat leaders for skaters across multiple categories.

**Parameters:**
- `categories` (query, string, optional): Comma-separated category names. If omitted, returns all categories.
- `limit` (query, integer, optional): Number of players per category. Use `-1` to return all. Default is typically 5.

**Available skater categories:** `goals`, `assists`, `points`, `plusMinus`, `penaltyMins`, `toi`, `goalsPp` (power play goals), `goalsSh` (shorthanded goals), `faceoffLeaders`

**Example:**
```
GET https://api-web.nhle.com/v1/skater-stats-leaders/current?categories=points&limit=10
GET https://api-web.nhle.com/v1/skater-stats-leaders/current?limit=-1
```

**Response:** Object keyed by category name, each value is an array of player objects with `id`, `firstName`, `lastName`, `sweaterNumber`, `headshot`, `teamAbbrev`, `teamName`, `teamLogo`, `position`, `value` (the stat value)

---

### GET /v1/skater-stats-leaders/{season}/{gameType}

Returns stats leaders for a specific season.

**Parameters:**
- `season` (path, integer): Season YYYYYYYY
- `gameType` (path, integer): `2` or `3`
- `categories` (query, string, optional): Same category names as above
- `limit` (query, integer, optional): Player count per category

**Example:**
```
GET https://api-web.nhle.com/v1/skater-stats-leaders/20242025/2?categories=goals,assists,points&limit=10
```

---

### GET /v1/goalie-stats-leaders/current

Returns the current season's stat leaders for goalies.

**Parameters:**
- `categories` (query, string, optional)
- `limit` (query, integer, optional)

**Available goalie categories:** `wins`, `shutouts`, `savePctg`, `goalsAgainstAverage`

**Example:**
```
GET https://api-web.nhle.com/v1/goalie-stats-leaders/current?limit=10
```

---

### GET /v1/goalie-stats-leaders/{season}/{gameType}

Returns goalie stats leaders for a specific season.

**Example:**
```
GET https://api-web.nhle.com/v1/goalie-stats-leaders/20242025/2
```

---

## 9. Playoff Endpoints

### GET /v1/playoff-series-carousel

Returns a visual carousel of current playoff series matchups.

**Example:**
```
GET https://api-web.nhle.com/v1/playoff-series-carousel
```

---

### GET /v1/playoff-bracket/{season}

Returns the full playoff bracket for a season. Returns 404 if the season's playoffs have not started.

**Parameters:**
- `season` (path, integer): Season YYYYYYYY

**Example:**
```
GET https://api-web.nhle.com/v1/playoff-bracket/20242025
```

---

### GET /v1/playoff-series/{season}/schedule

Returns the schedule for all playoff series in a season.

**Parameters:**
- `season` (path, integer): Season YYYYYYYY

**Example:**
```
GET https://api-web.nhle.com/v1/playoff-series/20242025/schedule
```

---

## 10. Draft Endpoints

### GET /v1/draft-tracker/now

Returns live draft tracker data during the current draft period.

**Example:**
```
GET https://api-web.nhle.com/v1/draft-tracker/now
```

---

### GET /v1/draft-picks/now

Returns current draft picks.

**Example:**
```
GET https://api-web.nhle.com/v1/draft-picks/now
```

---

### GET /v1/draft-picks/{year}

Returns draft picks for a specific year.

**Parameters:**
- `year` (path, integer): Draft year (e.g., `2025`)

**Example:**
```
GET https://api-web.nhle.com/v1/draft-picks/2025
```

---

### GET /v1/draft-rankings

Returns current draft prospect rankings.

> **Note**: This endpoint returned 404 on 2026-03-19. It may only be active during pre-draft periods.

**Example:**
```
GET https://api-web.nhle.com/v1/draft-rankings
```

---

### GET /v1/draft-rankings/{date}

Returns draft rankings as of a specific date.

**Example:**
```
GET https://api-web.nhle.com/v1/draft-rankings/2025-05-01
```

---

## 11. NHL Edge (Advanced Analytics) Endpoints

NHL Edge provides advanced tracking data (skating speed, distance, zone time, shot speed/location). These endpoints may require an NHL Edge subscription or may have restricted access.

### Team Edge Endpoints

```
GET /v1/edge/team/{teamId}/details
GET /v1/edge/team/{teamId}/comparison
GET /v1/edge/team/{teamId}/skating-distance/top-10
GET /v1/edge/team/{teamId}/skating-distance/detail
GET /v1/edge/team/{teamId}/skating-speed/top-10
GET /v1/edge/team/{teamId}/skating-speed/detail
GET /v1/edge/team/{teamId}/zone-time/top-10
GET /v1/edge/team/{teamId}/zone-time/detail
GET /v1/edge/team/{teamId}/shot-speed/top-10
GET /v1/edge/team/{teamId}/shot-speed/detail
GET /v1/edge/team/{teamId}/shot-location/top-10
GET /v1/edge/team/{teamId}/shot-location/detail
```

### Skater Edge Endpoints

```
GET /v1/edge/skater/{playerId}/landing
GET /v1/edge/skater/{playerId}/detail
GET /v1/edge/skater/{playerId}/comparison
GET /v1/edge/skater/{playerId}/distance/top-10
GET /v1/edge/skater/{playerId}/distance/detail
GET /v1/edge/skater/{playerId}/speed/top-10
GET /v1/edge/skater/{playerId}/speed/detail
GET /v1/edge/skater/{playerId}/zone-time/top-10
GET /v1/edge/skater/{playerId}/zone-time/detail
GET /v1/edge/skater/{playerId}/shot-speed/top-10
GET /v1/edge/skater/{playerId}/shot-speed/detail
GET /v1/edge/skater/{playerId}/shot-location/top-10
GET /v1/edge/skater/{playerId}/shot-location/detail
GET /v1/edge/cat/skater/{playerId}/detail
```

### Goalie Edge Endpoints

```
GET /v1/edge/goalie/{playerId}/detail
GET /v1/edge/goalie/{playerId}/comparison
GET /v1/edge/goalie/{playerId}/5v5/top-10
GET /v1/edge/goalie/{playerId}/5v5/detail
GET /v1/edge/goalie/{playerId}/shot-location/top-10
GET /v1/edge/goalie/{playerId}/shot-location/detail
GET /v1/edge/goalie/{playerId}/save-percentage/top-10
GET /v1/edge/goalie/{playerId}/save-percentage/detail
GET /v1/edge/cat/goalie/{playerId}/detail
```

---

## 12. Utility / Meta Endpoints

### GET /v1/season

Returns a list of all NHL seasons as integers (e.g., `[19171918, 19181919, ...]`). Useful for validating season IDs.

**Example:**
```
GET https://api-web.nhle.com/v1/season
```

---

### GET /v1/meta/{gameId}

Returns metadata for a specific game.

**Example:**
```
GET https://api-web.nhle.com/v1/meta/2025021073
```

---

### GET /v1/game-information/{gameId}

Returns general game information.

**Example:**
```
GET https://api-web.nhle.com/v1/game-information/2025021073
```

---

### GET /v1/replay/goal/{gameId}/{playId}

Returns goal replay data including highlight clip URL.

---

### GET /v1/replay/play/{gameId}/{playId}

Returns play replay data.

---

### GET /v1/postal-lookup/{postalCode}

Returns the team associated with a given postal code (for local team detection).

**Example:**
```
GET https://api-web.nhle.com/v1/postal-lookup/M5G
```

---

## 13. Secondary Base URL: api.nhle.com/stats/rest

Base URL: `https://api.nhle.com/stats/rest/en`

This secondary API provides more traditional query-style access with different data structures.

### Teams

```
GET /team                    — All teams (includes historical/defunct)
GET /team/{teamId}           — Specific team by numeric ID
GET /team-stats              — Team statistics
GET /franchise               — Franchise information
```

### Players/Skaters

```
GET /player                  — Player list
GET /player/{playerId}       — Specific player
GET /skater-stats-leaders    — Skater stat leaders
GET /skater-milestones       — Milestone data
GET /skater/{playerId}       — Specific skater stats
GET /skater-stats            — Skater statistics
```

### Goalies

```
GET /goalie-stats-leaders    — Goalie stat leaders
GET /goalie-stats            — Goalie statistics
GET /goalie-milestones       — Milestone data
```

### Draft

```
GET /draft                   — Draft information
```

### Season / Game

```
GET /season                  — Season information
GET /component-season        — Component season data
GET /game/{gameId}           — Game details
GET /game-metadata           — Game metadata
```

### Utility

```
GET /config                  — API configuration
GET /ping                    — Server health check
GET /country                 — Country information
GET /shift-chart             — Player shift data
GET /glossary                — Statistical terminology
GET /content-module          — Content information
```

---

## Sources

- [GitHub - Zmalski/NHL-API-Reference](https://github.com/Zmalski/NHL-API-Reference)
- Live API responses fetched on 2026-03-19
- [GitHub - coreyjs/nhl-api-py](https://github.com/coreyjs/nhl-api-py)
