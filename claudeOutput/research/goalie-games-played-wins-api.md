# Goalie Games Played & Wins — NHL API Findings

> **Researched**: 2026-04-29  
> **Goal**: Identify NHL API endpoints that return games played or wins for goalies, for use as a trivia question stat.

---

## Summary

| Stat | Available via leaders endpoint? | Available via per-team endpoint? | Available via player landing? |
|------|-------------------------------|----------------------------------|-------------------------------|
| `wins` | **YES** — `goalie-stats-leaders` | YES — `club-stats` | YES — `player/landing` |
| `gamesPlayed` | **NO** — returns 400 | YES — `club-stats` | YES — `player/landing` |

**Recommendation**: Use `wins` as the goalie stat for trivia. It is available in the stats-leaders endpoint (same pattern used for skater stats), works for both regular season and playoffs, and is a more meaningful single-game trivia question ("how many wins did X have?").

---

## Endpoint 1: `/v1/goalie-stats-leaders/{season}/{gameType}`

**Base URL**: `https://api-web.nhle.com`

### Available categories

Only four categories are supported. `gamesPlayed` returns HTTP 400.

| Category | Description |
|----------|-------------|
| `wins` | Wins in regulation/OT/SO |
| `shutouts` | Games with zero goals allowed |
| `savePctg` | Save percentage (decimal, e.g. `0.916`) |
| `goalsAgainstAverage` | Goals against average |

### Response structure

Each category returns an array of player objects with:

```json
{
  "id": 8476883,
  "firstName": { "default": "Andrei" },
  "lastName": { "default": "Vasilevskiy" },
  "sweaterNumber": 88,
  "headshot": "https://assets.nhle.com/mugs/nhl/20252026/TBL/8476883.png",
  "teamAbbrev": "TBL",
  "teamName": { "default": "Lightning" },
  "teamLogo": "https://assets.nhle.com/logos/nhl/svg/TBL_light.svg",
  "position": "G",
  "value": 39
}
```

`value` is the raw stat. For wins, it is an integer (e.g., `39`).

### Live example — wins leaders, 2025-26 regular season (top 5)

```
GET https://api-web.nhle.com/v1/goalie-stats-leaders/20252026/2?categories=wins&limit=5
```

| Rank | Goalie | Team | Wins |
|------|--------|------|------|
| 1 | Andrei Vasilevskiy | TBL | 39 |
| 2 | Karel Vejmelka | UTA | 38 |
| 3 | Jake Oettinger | DAL | 35 |
| 4 | Logan Thompson | WSH | 31 |
| 5 | Scott Wedgewood | COL | 31 |

### Notes

- `/current` redirects (HTTP 307) to the **current active game type** — which is playoffs (gameType=3) once the postseason begins. Use explicit `/{season}/{gameType}` for reliable results.
- Fetching `limit=-1` returns all qualifying goalies.
- The `value` field is the only stat returned; there is no `gamesPlayed` alongside it.

---

## Endpoint 2: `/v1/club-stats/{team}/now`

Returns full goalie stats for every goalie on a team's current roster. Includes `gamesPlayed` and `wins`.

```
GET https://api-web.nhle.com/v1/club-stats/TOR/now
```

### Goalie object fields

```
playerId, headshot, firstName, lastName,
gamesPlayed, gamesStarted,
wins, losses, overtimeLosses,
goalsAgainstAverage, savePercentage,
shotsAgainst, saves, goalsAgainst,
shutouts, goals, assists, points, penaltyMinutes,
timeOnIce (seconds)
```

### Live example — Toronto goalies, 2025-26 regular season

```json
{
  "playerId": 8476932,
  "firstName": { "default": "Anthony" },
  "lastName": { "default": "Stolarz" },
  "gamesPlayed": 26,
  "gamesStarted": 25,
  "wins": 10,
  "losses": 10,
  "overtimeLosses": 3,
  "goalsAgainstAverage": 3.282347,
  "savePercentage": 0.892655,
  "shotsAgainst": 708,
  "saves": 632,
  "goalsAgainst": 76,
  "shutouts": 0,
  "timeOnIce": 83355
}
```

### Limitation

To build a league-wide `gamesPlayed` leaders list, you'd need to call this endpoint for all 32 teams and aggregate results — not practical for an on-demand pool fetch.

---

## Endpoint 3: `/v1/player/{playerId}/landing`

Returns a full player profile. For goalies, `featuredStats` and `seasonTotals` both contain `gamesPlayed` and `wins`.

### `featuredStats` (current season + career)

```json
{
  "season": 20252026,
  "regularSeason": {
    "subSeason": {
      "gamesPlayed": 58,
      "goalsAgainstAvg": 2.308533,
      "losses": 15,
      "otLosses": 4,
      "savePctg": 0.91234,
      "shutouts": 2,
      "wins": 39
    },
    "career": {
      "gamesPlayed": 598,
      "goalsAgainstAvg": 2.49898,
      "losses": 178,
      "otLosses": 39,
      "savePctg": 0.916771,
      "shutouts": 42,
      "wins": 370
    }
  },
  "playoffs": {
    "subSeason": { ... },
    "career": { ... }
  }
}
```

### `seasonTotals` (per-season history entries)

Each entry in the `seasonTotals` array (filtered by `gameTypeId == 2` for regular season):

```json
{
  "season": 20252026,
  "gameTypeId": 2,
  "gamesPlayed": 58,
  "gamesStarted": 58,
  "wins": 39,
  "losses": 15,
  "otLosses": 4,
  "goalsAgainstAvg": 2.308533,
  "savePctg": 0.91234,
  "shutouts": 2,
  "shotsAgainst": 1483,
  "goalsAgainst": 132,
  "timeOnIce": "3430:45",
  "leagueAbbrev": "NHL",
  "teamName": { "default": "Tampa Bay Lightning" }
}
```

---

## Secondary API (api.nhle.com/stats/rest)

The secondary REST API at `https://api.nhle.com/stats/rest/en` is **currently returning HTTP 500** and is unreachable. It was documented to have a `/goalie/summary` endpoint with flexible sorting (including by `gamesPlayed`), but it cannot be relied on.

---

## Recommendation for PuckTrivia

Use **`wins`** via the goalie-stats-leaders endpoint:

```
GET https://api-web.nhle.com/v1/goalie-stats-leaders/{season}/{gameType}?categories=wins&limit=-1
```

This matches the existing pattern used for skater stats (points, goals, assists, etc.) and returns a ranked list of all goalies with their win totals — exactly what the PlayerPool needs to pick a random goalie and generate a "how many wins did X have?" question.

`gamesPlayed` is not accessible via the leaders endpoint, but `wins` is a stronger trivia stat anyway (more variance between top and bottom goalies than games played, which clusters around 55–65 for starters).
