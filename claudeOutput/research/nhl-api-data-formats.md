# NHL Web API — Data Formats and JSON Response Examples

> **Documented**: 2026-03-19
> **All examples are from live API responses fetched on this date**
> **2025-2026 season is currently active (season ID: `20252026`)**

---

## Table of Contents

1. [Common Conventions](#1-common-conventions)
2. [Standings Response](#2-standings-response)
3. [Schedule Response](#3-schedule-response)
4. [Score/Now Response](#4-scorenow-response)
5. [Player Landing Response](#5-player-landing-response)
6. [Player Game Log Response](#6-player-game-log-response)
7. [Skater Stats Leaders Response](#7-skater-stats-leaders-response)
8. [Goalie Stats Leaders Response](#8-goalie-stats-leaders-response)
9. [Roster Response](#9-roster-response)
10. [Club Stats Response](#10-club-stats-response)
11. [Gamecenter Boxscore Response](#11-gamecenter-boxscore-response)
12. [Gamecenter Play-by-Play Response](#12-gamecenter-play-by-play-response)
13. [Player Spotlight Response](#13-player-spotlight-response)
14. [Standings Season Metadata Response](#14-standings-season-metadata-response)
15. [ID Reference Tables](#15-id-reference-tables)

---

## 1. Common Conventions

### Multi-Language String Fields

String fields that can vary by language use an object with `default` (English) and optional locale codes:

```json
"teamName": { "default": "Maple Leafs", "fr": "Maple Leafs" },
"firstName": { "default": "Connor", "cs": "Connor" },
"birthCity": { "default": "Dardenne Prairie" }
```

Always use the `default` key for English.

### Date Formats

| Format | Example | Used For |
|--------|---------|---------|
| `YYYY-MM-DD` | `"2026-03-19"` | Game dates, birth dates, standings dates |
| ISO 8601 UTC | `"2026-03-19T23:00:00Z"` | Game start times |
| Integer season ID | `20252026` | Season references |

### Time on Ice (TOI) Formats

TOI appears in two different formats depending on the endpoint:

- **MM:SS string** — used in game logs and play-by-play: `"24:52"`, `"60:00"`
- **Seconds (decimal)** — used in club-stats: `1248.23` (= ~20:48)
- **Seconds (integer)** — used in career totals: `47431` total career seconds

### Shooting/Save Percentage

Percentages are returned as decimals:
- `0.146825` = 14.68% shooting percentage
- `0.914` = .914 save percentage
- `0.9157` = .9157 save percentage

### Coordinate System (Play-by-Play)

Ice coordinates use the standard NHL coordinate system:
- `xCoord`: -100 to 100 (left to right from center ice)
- `yCoord`: -42.5 to 42.5 (bottom to top)
- `zoneCode`: `"O"` (offensive), `"D"` (defensive), `"N"` (neutral)

---

## 2. Standings Response

**Endpoint**: `GET /v1/standings/now`

```json
{
  "wildCardIndicator": true,
  "standingsDateTimeUtc": "2026-03-19T17:00:01Z",
  "standings": [
    {
      "conferenceAbbrev": "W",
      "conferenceName": "Western",
      "conferenceSequence": 1,
      "divisionAbbrev": "C",
      "divisionName": "Central",
      "divisionSequence": 1,
      "teamAbbrev": { "default": "COL" },
      "teamName": { "default": "Avalanche" },
      "teamCommonName": { "default": "Avalanche" },
      "teamLogo": "https://assets.nhle.com/logos/nhl/svg/COL_light.svg",
      "placeName": { "default": "Colorado" },
      "seasonId": 20252026,
      "gameTypeId": 2,
      "date": "2026-03-19",
      "gamesPlayed": 67,
      "wins": 44,
      "losses": 13,
      "otLosses": 10,
      "ties": 0,
      "points": 98,
      "pointPctg": 0.73134328,
      "regulationWins": 38,
      "regulationPlusOtWins": 42,
      "shootoutWins": 2,
      "shootoutLosses": 5,
      "goalFor": 247,
      "goalAgainst": 183,
      "goalDifferential": 64,
      "goalDifferentialPctg": 0.95522,
      "goalsForPctg": 0.26923,
      "homeGamesPlayed": 33,
      "homeWins": 22,
      "homeLosses": 7,
      "homeOtLosses": 4,
      "homePoints": 48,
      "roadGamesPlayed": 34,
      "roadWins": 22,
      "roadLosses": 6,
      "roadOtLosses": 6,
      "roadPoints": 50,
      "l10GamesPlayed": 10,
      "l10Wins": 7,
      "l10Losses": 2,
      "l10OtLosses": 1,
      "l10Points": 15,
      "streakCode": "W",
      "streakCount": 3,
      "wildcardSequence": 0
    }
  ]
}
```

**Key notes:**
- `wildcardSequence`: 0 means not in wildcard position (team is in a playoff division spot)
- `streakCode`: `"W"` = win streak, `"L"` = loss streak, `"OT"` = OT loss streak
- `conferenceSequence` / `divisionSequence`: ranking position within conference/division
- All 32 NHL teams appear in the array

**Live data (2026-03-19) top teams:**
- Colorado Avalanche: 44-13-10, 98 pts (1st West, 1st Central)
- Dallas Stars: 43-15-10, 96 pts (2nd West, 2nd Central)
- Carolina Hurricanes: 43-19-6, 92 pts (1st East, 1st Metropolitan)
- Buffalo Sabres: 42-20-6, 90 pts (2nd East, 1st Atlantic)

---

## 3. Schedule Response

**Endpoint**: `GET /v1/schedule/now`

```json
{
  "nextStartDate": "2026-03-26",
  "previousStartDate": "2026-03-12",
  "gameWeek": [
    {
      "date": "2026-03-19",
      "dayAbbrev": "THU",
      "numberOfGames": 11,
      "datePromo": [],
      "games": [
        {
          "id": 2025021083,
          "season": 20252026,
          "gameType": 2,
          "gameDate": "2026-03-19",
          "venue": { "default": "TD Garden" },
          "neutralSite": false,
          "startTimeUTC": "2026-03-19T23:00:00Z",
          "easternUTCOffset": "-04:00",
          "venueUTCOffset": "-04:00",
          "venueTimezone": "US/Eastern",
          "gameState": "FUT",
          "gameScheduleState": "OK",
          "tvBroadcasts": [
            {
              "id": 281,
              "market": "H",
              "countryCode": "US",
              "network": "NESN",
              "sequenceNumber": 1
            },
            {
              "id": 285,
              "market": "A",
              "countryCode": "CA",
              "network": "TSN3",
              "sequenceNumber": 2
            }
          ],
          "awayTeam": {
            "id": 52,
            "commonName": { "default": "Jets" },
            "placeName": { "default": "Winnipeg" },
            "placeNameWithPreposition": { "default": "in Winnipeg" },
            "abbrev": "WPG",
            "logo": "https://assets.nhle.com/logos/nhl/svg/WPG_light.svg",
            "darkLogo": "https://assets.nhle.com/logos/nhl/svg/WPG_dark.svg",
            "record": "28-28-11",
            "odds": [
              { "providerId": 2, "value": "2.38" }
            ]
          },
          "homeTeam": {
            "id": 6,
            "commonName": { "default": "Bruins" },
            "abbrev": "BOS",
            "record": "38-22-7",
            "logo": "https://assets.nhle.com/logos/nhl/svg/BOS_light.svg"
          },
          "gameCenterLink": "/gamecenter/wpg-vs-bos/2026/03/19/2025021083",
          "ticketsLink": "https://www.ticketmaster.com/...",
          "teamLeaders": [
            {
              "id": 8478398,
              "firstName": { "default": "Kyle" },
              "lastName": { "default": "Connor" },
              "headshot": "https://assets.nhle.com/mugs/nhl/20252026/WPG/8478398.png",
              "teamAbbrev": "WPG",
              "sweaterNumber": 81,
              "position": "L",
              "category": "goals",
              "value": 31
            }
          ]
        }
      ]
    }
  ]
}
```

**TV broadcast market codes:**
- `"H"` = Home market broadcast
- `"A"` = Away market broadcast
- `"N"` = National broadcast

---

## 4. Score/Now Response

**Endpoint**: `GET /v1/score/now`

The score/now response uses the same structure as schedule/now but includes live score data when games are in progress. When a game is in state `"OFF"` (final), teams have a `score` field:

```json
{
  "awayTeam": {
    "id": 28,
    "abbrev": "SJS",
    "score": 2,
    "sog": 24
  },
  "homeTeam": {
    "id": 22,
    "abbrev": "EDM",
    "score": 5,
    "sog": 38
  },
  "gameState": "OFF",
  "gameOutcome": {
    "lastPeriodType": "REG"
  },
  "periodDescriptor": {
    "number": 3,
    "periodType": "REG",
    "maxRegulationPeriods": 3
  }
}
```

**gameOutcome.lastPeriodType values:**
- `"REG"` — decided in regulation
- `"OT"` — decided in overtime
- `"SO"` — decided in shootout

---

## 5. Player Landing Response

**Endpoint**: `GET /v1/player/{playerId}/landing`

**Full response structure for Connor McDavid (ID: 8478402), 2025-26 season:**

```json
{
  "playerId": 8478402,
  "isActive": true,
  "currentTeamId": 22,
  "currentTeamAbbrev": "EDM",
  "fullTeamName": { "default": "Edmonton Oilers" },
  "teamCommonName": { "default": "Oilers" },
  "teamPlaceNameWithPreposition": { "default": "in Edmonton" },
  "firstName": { "default": "Connor" },
  "lastName": { "default": "McDavid" },
  "sweaterNumber": 97,
  "position": "C",
  "headshot": "https://assets.nhle.com/mugs/nhl/20252026/EDM/8478402.png",
  "heroImage": "https://assets.nhle.com/mugs/actionshots/1296x729/8478402.jpg",
  "heightInInches": 73,
  "heightInCentimeters": 185,
  "weightInPounds": 193,
  "weightInKilograms": 88,
  "birthDate": "1997-01-13",
  "birthCity": { "default": "Richmond Hill" },
  "birthStateProvince": { "default": "ON" },
  "birthCountry": "CAN",
  "shootsCatches": "L",
  "playerSlug": "connor-mcdavid-8478402",
  "inTop100AllTime": 1,
  "inHHOF": 0,

  "draftDetails": {
    "year": 2015,
    "teamAbbrev": "EDM",
    "round": 1,
    "pickInRound": 1,
    "overallPick": 1
  },

  "featuredStats": {
    "season": 20252026,
    "regularSeason": {
      "subSeason": {
        "assists": 78,
        "gameWinningGoals": 3,
        "gamesPlayed": 69,
        "goals": 37,
        "points": 115,
        "powerPlayGoals": 11,
        "shots": 252,
        "shootingPctg": 0.146825
      },
      "career": {
        "assists": 799,
        "gameWinningGoals": 75,
        "gamesPlayed": 781,
        "goals": 398,
        "points": 1197,
        "powerPlayGoals": 98,
        "shots": 2659,
        "shootingPctg": 0.1496
      }
    }
  },

  "careerTotals": {
    "regularSeason": {
      "assists": 799,
      "avgToi": "21:51",
      "faceoffWinningPctg": 0.4856,
      "gamesPlayed": 781,
      "gameWinningGoals": 75,
      "goals": 398,
      "otGoals": 14,
      "pim": 235,
      "plusMinus": 181,
      "points": 1197,
      "powerPlayGoals": 98,
      "powerPlayPoints": 330,
      "shootingPctg": 0.1496,
      "shorthandedGoals": 3,
      "shots": 2659
    },
    "playoffs": {
      "assists": 106,
      "avgToi": "23:38",
      "gamesPlayed": 96,
      "goals": 44,
      "points": 150,
      "plusMinus": 26
    }
  },

  "last5Games": [
    {
      "gameId": 2025021073,
      "teamAbbrev": "EDM",
      "homeRoadFlag": "H",
      "gameDate": "2026-03-17",
      "goals": 0,
      "assists": 1,
      "points": 1,
      "plusMinus": 0,
      "powerPlayGoals": 0,
      "powerPlayPoints": 1,
      "shots": 5,
      "toi": "24:52",
      "opponentAbbrev": "SJS"
    }
  ],

  "awards": [
    {
      "trophy": { "default": "Art Ross Trophy" },
      "seasons": [
        {
          "seasonId": 20222023,
          "gamesPlayed": 82,
          "goals": 64,
          "assists": 89,
          "points": 153
        }
      ]
    }
  ],

  "seasonTotals": [
    {
      "season": 20252026,
      "gameTypeId": 2,
      "teamName": { "default": "Edmonton Oilers" },
      "leagueAbbrev": "NHL",
      "sequence": 1,
      "gamesPlayed": 69,
      "goals": 37,
      "assists": 78,
      "points": 115,
      "plusMinus": 22,
      "pim": 28,
      "gameWinningGoals": 3,
      "shots": 252,
      "shootingPctg": 14.7,
      "powerPlayGoals": 11,
      "powerPlayPoints": 38,
      "shorthandedGoals": 0,
      "avgToi": "21:17"
    }
  ],

  "currentTeamRoster": [
    {
      "playerId": 8477934,
      "firstName": { "default": "Leon" },
      "lastName": { "default": "Draisaitl" },
      "playerSlug": "leon-draisaitl-8477934"
    }
  ]
}
```

**Goalie-specific fields in player landing** (for a goalie like Vasilevskiy, ID: 8476883):

```json
"featuredStats": {
  "season": 20252026,
  "regularSeason": {
    "subSeason": {
      "gamesPlayed": 46,
      "wins": 31,
      "losses": 12,
      "otLosses": 3,
      "goalsAgainstAvg": 2.298,
      "savePctg": 0.914,
      "shutouts": 2
    },
    "career": {
      "gamesPlayed": 586,
      "wins": 362,
      "losses": 175,
      "otLosses": 38,
      "goalsAgainstAvg": 2.502,
      "savePctg": 0.917,
      "shutouts": 42
    }
  }
}
```

---

## 6. Player Game Log Response

**Endpoint**: `GET /v1/player/8478402/game-log/now`

```json
{
  "seasonId": 20252026,
  "gameTypeId": 2,
  "playerStatsSeasons": [
    { "season": 20252026, "gameTypes": [2] }
  ],
  "gameLog": [
    {
      "gameId": 2025021073,
      "teamAbbrev": "EDM",
      "homeRoadFlag": "H",
      "gameDate": "2026-03-17",
      "goals": 0,
      "assists": 1,
      "commonName": { "default": "Oilers" },
      "opponentCommonName": { "default": "Sharks" },
      "points": 1,
      "plusMinus": 0,
      "powerPlayGoals": 0,
      "powerPlayPoints": 1,
      "gameWinningGoals": 0,
      "otGoals": 0,
      "shots": 5,
      "shifts": 22,
      "shorthandedGoals": 0,
      "shorthandedPoints": 0,
      "opponentAbbrev": "SJS",
      "pim": 0,
      "toi": "24:52"
    }
  ]
}
```

**Notes:**
- `homeRoadFlag`: `"H"` = home game, `"R"` = road (away) game
- `toi`: Time on ice in `MM:SS` string format
- Games appear in reverse-chronological order (most recent first)

---

## 7. Skater Stats Leaders Response

**Endpoint**: `GET /v1/skater-stats-leaders/current?categories=points&limit=10`

```json
{
  "points": [
    {
      "id": 8478402,
      "firstName": { "default": "Connor" },
      "lastName": { "default": "McDavid" },
      "sweaterNumber": 97,
      "headshot": "https://assets.nhle.com/mugs/nhl/20252026/EDM/8478402.png",
      "teamAbbrev": "EDM",
      "teamName": { "default": "Oilers" },
      "teamLogo": "https://assets.nhle.com/logos/nhl/svg/EDM_light.svg",
      "position": "C",
      "value": 115
    },
    {
      "id": 8477492,
      "firstName": { "default": "Nathan" },
      "lastName": { "default": "MacKinnon" },
      "sweaterNumber": 29,
      "teamAbbrev": "COL",
      "position": "C",
      "value": 111
    },
    {
      "id": 8476453,
      "firstName": { "default": "Nikita" },
      "lastName": { "default": "Kucherov" },
      "sweaterNumber": 86,
      "teamAbbrev": "TBL",
      "position": "R",
      "value": 111
    },
    {
      "id": 8477934,
      "firstName": { "default": "Leon" },
      "lastName": { "default": "Draisaitl" },
      "sweaterNumber": 29,
      "teamAbbrev": "EDM",
      "position": "C",
      "value": 97
    },
    {
      "id": 8484801,
      "firstName": { "default": "Macklin" },
      "lastName": { "default": "Celebrini" },
      "sweaterNumber": 71,
      "teamAbbrev": "SJS",
      "position": "C",
      "value": 95
    }
  ]
}
```

**Current season points leaders (as of 2026-03-19):**
1. Connor McDavid (EDM) — 115 pts (37G, 78A in 69 GP)
2. Nathan MacKinnon (COL) — 111 pts
3. Nikita Kucherov (TBL) — 111 pts
4. Leon Draisaitl (EDM) — 97 pts
5. Macklin Celebrini (SJS) — 95 pts
6. Mark Scheifele (WPG) — 83 pts
7. David Pastrnak (BOS) — 82 pts
8. Jason Robertson (DAL) — 81 pts
9. Martin Necas (COL) — 81 pts
10. Kirill Kaprizov (MIN) — 80 pts

**Available categories and their values:**
- `goals` — integer count
- `assists` — integer count
- `points` — integer count
- `plusMinus` — signed integer
- `penaltyMins` — integer minutes
- `toi` — total time on ice (format TBD per season query)
- `goalsPp` — power play goals
- `goalsSh` — shorthanded goals
- `faceoffLeaders` — faceoff win percentage (decimal)

---

## 8. Goalie Stats Leaders Response

**Endpoint**: `GET /v1/goalie-stats-leaders/current`

```json
{
  "wins": [
    {
      "id": 8476883,
      "firstName": { "default": "Andrei" },
      "lastName": { "default": "Vasilevskiy" },
      "sweaterNumber": 88,
      "teamAbbrev": "TBL",
      "teamName": { "default": "Lightning" },
      "position": "G",
      "value": 31
    },
    {
      "id": 8479351,
      "firstName": { "default": "Karel" },
      "lastName": { "default": "Vejmelka" },
      "teamAbbrev": "UTA",
      "value": 30
    },
    {
      "id": 8478048,
      "firstName": { "default": "Jake" },
      "lastName": { "default": "Oettinger" },
      "teamAbbrev": "DAL",
      "value": 29
    }
  ],
  "shutouts": [
    {
      "id": 8478009,
      "firstName": { "default": "Ilya" },
      "lastName": { "default": "Sorokin" },
      "teamAbbrev": "NYI",
      "value": 6
    }
  ],
  "savePctg": [
    {
      "id": 8481596,
      "firstName": { "default": "Scott" },
      "lastName": { "default": "Wedgewood" },
      "teamAbbrev": "COL",
      "value": 0.9157
    }
  ],
  "goalsAgainstAverage": [
    {
      "id": 8481596,
      "firstName": { "default": "Scott" },
      "lastName": { "default": "Wedgewood" },
      "teamAbbrev": "COL",
      "value": 2.19
    }
  ]
}
```

**Current season goalie leaders (as of 2026-03-19):**
- Wins leader: Andrei Vasilevskiy (TBL) — 31 wins
- Shutouts leader: Ilya Sorokin (NYI) — 6 shutouts
- Save % leader: Scott Wedgewood (COL) — .9157
- GAA leader: Scott Wedgewood (COL) — 2.19

---

## 9. Roster Response

**Endpoint**: `GET /v1/roster/TOR/current`

```json
{
  "forwards": [
    {
      "id": 8479318,
      "headshot": "https://assets.nhle.com/mugs/nhl/20252026/TOR/8479318.png",
      "firstName": { "default": "Auston" },
      "lastName": { "default": "Matthews" },
      "sweaterNumber": 34,
      "positionCode": "C",
      "shootsCatches": "L",
      "heightInInches": 77,
      "weightInPounds": 224,
      "heightInCentimeters": 196,
      "weightInKilograms": 102,
      "birthDate": "1997-09-17",
      "birthCity": { "default": "San Ramon" },
      "birthStateProvince": { "default": "California" },
      "birthCountry": "USA"
    },
    {
      "id": 8480076,
      "firstName": { "default": "William" },
      "lastName": { "default": "Nylander" },
      "sweaterNumber": 88,
      "positionCode": "R",
      "shootsCatches": "R",
      "heightInInches": 73,
      "weightInPounds": 195,
      "birthDate": "1996-05-01",
      "birthCity": { "default": "Calgary" },
      "birthStateProvince": { "default": "AB" },
      "birthCountry": "CAN"
    }
  ],
  "defensemen": [
    {
      "id": 8477981,
      "firstName": { "default": "Morgan" },
      "lastName": { "default": "Rielly" },
      "sweaterNumber": 44,
      "positionCode": "D",
      "shootsCatches": "L",
      "heightInInches": 73,
      "weightInPounds": 198,
      "birthDate": "1994-03-09",
      "birthCity": { "default": "Vancouver" },
      "birthCountry": "CAN"
    }
  ],
  "goalies": [
    {
      "id": 8479361,
      "firstName": { "default": "Joseph" },
      "lastName": { "default": "Woll" },
      "sweaterNumber": 60,
      "positionCode": "G",
      "shootsCatches": "L",
      "heightInInches": 75,
      "weightInPounds": 212,
      "birthDate": "1998-07-12",
      "birthCity": { "default": "Dardenne Prairie" },
      "birthStateProvince": { "default": "Missouri" },
      "birthCountry": "USA"
    }
  ]
}
```

**positionCode values:** `"C"` (center), `"L"` (left wing), `"R"` (right wing), `"D"` (defenseman), `"G"` (goalie)

---

## 10. Club Stats Response

**Endpoint**: `GET /v1/club-stats/TOR/now`

```json
{
  "season": "20252026",
  "gameType": 2,
  "skaters": [
    {
      "playerId": 8479318,
      "headshot": "https://assets.nhle.com/mugs/nhl/20252026/TOR/8479318.png",
      "firstName": { "default": "Auston" },
      "lastName": { "default": "Matthews" },
      "positionCode": "C",
      "gamesPlayed": 60,
      "goals": 27,
      "assists": 26,
      "points": 53,
      "plusMinus": -4,
      "penaltyMinutes": 22,
      "powerPlayGoals": 8,
      "shorthandedGoals": 0,
      "gameWinningGoals": 6,
      "overtimeGoals": 1,
      "shots": 227,
      "shootingPctg": 0.1189,
      "avgTimeOnIcePerGame": 1248.23,
      "avgShiftsPerGame": 23.6,
      "faceoffWinPctg": 0.5967
    },
    {
      "playerId": 8480076,
      "firstName": { "default": "William" },
      "lastName": { "default": "Nylander" },
      "positionCode": "R",
      "gamesPlayed": 52,
      "goals": 23,
      "assists": 40,
      "points": 63,
      "plusMinus": -6,
      "shots": 116,
      "shootingPctg": 0.1983,
      "powerPlayGoals": 6,
      "avgTimeOnIcePerGame": 1137.48
    }
  ],
  "goalies": [
    {
      "playerId": 8479361,
      "firstName": { "default": "Joseph" },
      "lastName": { "default": "Woll" },
      "gamesPlayed": 32,
      "gamesStarted": 30,
      "wins": 14,
      "losses": 12,
      "overtimeLosses": 5,
      "goalsAgainstAverage": 3.078,
      "savePercentage": 0.9060,
      "shotsAgainst": 874,
      "saves": 792,
      "goalsAgainst": 82,
      "shutouts": 2,
      "goals": 0,
      "assists": 1,
      "points": 1,
      "penaltyMinutes": 0,
      "timeOnIce": 55443
    }
  ]
}
```

**Notes:**
- `avgTimeOnIcePerGame` for skaters is in **seconds** (e.g., `1248.23` = 20 minutes 48 seconds)
- `timeOnIce` for goalies is total season seconds

---

## 11. Gamecenter Boxscore Response

**Endpoint**: `GET /v1/gamecenter/2025021073/boxscore`

```json
{
  "id": 2025021073,
  "season": 20252026,
  "gameType": 2,
  "gameDate": "2026-03-17",
  "startTimeUTC": "2026-03-18T01:00:00Z",
  "gameState": "OFF",
  "gameScheduleState": "OK",
  "awayTeam": {
    "id": 28,
    "commonName": { "default": "Sharks" },
    "abbrev": "SJS",
    "score": 2,
    "sog": 24,
    "logo": "https://assets.nhle.com/logos/nhl/svg/SJS_light.svg"
  },
  "homeTeam": {
    "id": 22,
    "commonName": { "default": "Oilers" },
    "abbrev": "EDM",
    "score": 5,
    "sog": 38,
    "logo": "https://assets.nhle.com/logos/nhl/svg/EDM_light.svg"
  },
  "playerByGameStats": {
    "awayTeam": {
      "forwards": [
        {
          "playerId": 8484801,
          "sweaterNumber": 71,
          "name": { "default": "Macklin Celebrini" },
          "position": "C",
          "goals": 1,
          "assists": 1,
          "points": 2,
          "plusMinus": -2,
          "pim": 0,
          "hits": 2,
          "powerPlayGoals": 0,
          "sog": 3,
          "faceoffWinningPctg": 0.5,
          "toi": "19:14",
          "blockedShots": 0,
          "shifts": 20,
          "giveaways": 1,
          "takeaways": 0
        }
      ],
      "defense": [ ... ],
      "goalies": [
        {
          "playerId": 8482521,
          "name": { "default": "Mackenzie Blackwood" },
          "evenStrengthShotsAgainst": "19/22",
          "powerPlayShotsAgainst": "3/5",
          "shorthandedShotsAgainst": "0/0",
          "saveShotsAgainst": "0/0",
          "savePctg": 0.868,
          "goalsAgainst": 5,
          "toi": "60:00",
          "starter": true,
          "decision": "L"
        }
      ]
    },
    "homeTeam": { ... }
  },
  "summary": {
    "shotsByPeriod": [
      { "period": 1, "away": 8, "home": 15 },
      { "period": 2, "away": 8, "home": 12 },
      { "period": 3, "away": 8, "home": 11 }
    ],
    "teamGameStats": [
      { "category": "sog", "awayValue": 24, "homeValue": 38 },
      { "category": "faceoffWinningPctg", "awayValue": 0.5, "homeValue": 0.5 },
      { "category": "powerPlay", "awayValue": "1/4", "homeValue": "2/5" },
      { "category": "pim", "awayValue": 10, "homeValue": 10 },
      { "category": "hits", "awayValue": 18, "homeValue": 28 },
      { "category": "blockedShots", "awayValue": 14, "homeValue": 12 },
      { "category": "giveaways", "awayValue": 8, "homeValue": 9 },
      { "category": "takeaways", "awayValue": 6, "homeValue": 5 }
    ]
  },
  "gameOutcome": {
    "lastPeriodType": "REG"
  }
}
```

---

## 12. Gamecenter Play-by-Play Response

**Endpoint**: `GET /v1/gamecenter/2025021073/play-by-play`

```json
{
  "id": 2025021073,
  "plays": [
    {
      "eventId": 52,
      "periodDescriptor": {
        "number": 1,
        "periodType": "REG",
        "maxRegulationPeriods": 3
      },
      "timeInPeriod": "00:00",
      "timeRemaining": "20:00",
      "typeCode": 520,
      "typeDescKey": "period-start",
      "sortOrder": 8
    },
    {
      "eventId": 79,
      "timeInPeriod": "02:46",
      "typeCode": 506,
      "typeDescKey": "shot-on-goal",
      "details": {
        "xCoord": 78,
        "yCoord": 10,
        "zoneCode": "O",
        "shotType": "snap",
        "shootingPlayerId": 8483512,
        "goalieInNetId": 8477968,
        "awaySOG": 0,
        "homeSOG": 1
      },
      "sortOrder": 45
    },
    {
      "eventId": 145,
      "timeInPeriod": "07:24",
      "typeCode": 505,
      "typeDescKey": "goal",
      "details": {
        "xCoord": -80,
        "yCoord": -1,
        "zoneCode": "O",
        "shotType": "wrist",
        "scoringPlayerId": 8475200,
        "scoringPlayerTotal": 2,
        "assist1PlayerId": 8477505,
        "assist1PlayerTotal": 31,
        "assist2PlayerId": 8478402,
        "assist2PlayerTotal": 78,
        "awayScore": 1,
        "homeScore": 0
      },
      "sortOrder": 92
    },
    {
      "eventId": 203,
      "timeInPeriod": "14:18",
      "typeCode": 509,
      "typeDescKey": "penalty",
      "details": {
        "committedByPlayerId": 8476468,
        "drawnByPlayerId": 8478402,
        "descKey": "tripping",
        "duration": 2,
        "typeCode": "MIN",
        "teamAbbrev": "SJS"
      }
    }
  ]
}
```

**Shot type values:** `"wrist"`, `"snap"`, `"slap"`, `"backhand"`, `"tip-in"`, `"deflected"`, `"wrap-around"`

---

## 13. Player Spotlight Response

**Endpoint**: `GET /v1/player-spotlight`

```json
[
  {
    "playerId": 8484144,
    "name": { "default": "Connor Bedard" },
    "playerSlug": "connor-bedard-8484144",
    "position": "C",
    "sweaterNumber": 98,
    "teamId": 16,
    "headshot": "https://assets.nhle.com/mugs/nhl/20252026/CHI/8484144.png",
    "teamTriCode": "CHI",
    "teamLogo": "https://assets.nhle.com/logos/nhl/svg/CHI_light.svg",
    "sortId": 5
  }
]
```

**Notes:** This is a curated list selected by the NHL for the current week/period. The `name` field can have localized variants (e.g., `"cs"`, `"fi"`, `"sk"` for Czech, Finnish, Slovak players).

---

## 14. Standings Season Metadata Response

**Endpoint**: `GET /v1/standings-season`

```json
{
  "currentDate": "2026-03-19",
  "seasons": [
    {
      "id": 20252026,
      "conferencesInUse": true,
      "divisionsInUse": true,
      "pointForOTlossInUse": true,
      "regulationWinsInUse": true,
      "rowInUse": true,
      "standingsStart": "2025-10-08",
      "standingsEnd": "2026-04-18",
      "tiesInUse": false,
      "wildcardInUse": true
    },
    {
      "id": 19171918,
      "conferencesInUse": false,
      "divisionsInUse": false,
      "pointForOTlossInUse": false,
      "regulationWinsInUse": false,
      "rowInUse": false,
      "standingsStart": "1917-12-19",
      "standingsEnd": "1918-03-06",
      "tiesInUse": true,
      "wildcardInUse": false
    }
  ]
}
```

---

## 15. ID Reference Tables

### Team IDs and Tricodes

| Team Name | Tricode | Numeric ID |
|-----------|---------|------------|
| Anaheim Ducks | ANA | 24 |
| Utah Hockey Club | UTA | 53 |
| Boston Bruins | BOS | 6 |
| Buffalo Sabres | BUF | 7 |
| Calgary Flames | CGY | 20 |
| Carolina Hurricanes | CAR | 12 |
| Chicago Blackhawks | CHI | 16 |
| Colorado Avalanche | COL | 21 |
| Columbus Blue Jackets | CBJ | 29 |
| Dallas Stars | DAL | 25 |
| Detroit Red Wings | DET | 17 |
| Edmonton Oilers | EDM | 22 |
| Florida Panthers | FLA | 13 |
| Los Angeles Kings | LAK | 26 |
| Minnesota Wild | MIN | 30 |
| Montreal Canadiens | MTL | 8 |
| Nashville Predators | NSH | 18 |
| New Jersey Devils | NJD | 1 |
| New York Islanders | NYI | 2 |
| New York Rangers | NYR | 3 |
| Ottawa Senators | OTT | 9 |
| Philadelphia Flyers | PHI | 4 |
| Pittsburgh Penguins | PIT | 5 |
| San Jose Sharks | SJS | 28 |
| Seattle Kraken | SEA | 55 |
| St. Louis Blues | STL | 19 |
| Tampa Bay Lightning | TBL | 14 |
| Toronto Maple Leafs | TOR | 10 |
| Vancouver Canucks | VAN | 23 |
| Vegas Golden Knights | VGK | 54 |
| Washington Capitals | WSH | 15 |
| Winnipeg Jets | WPG | 52 |

### Key Player IDs (Current Stars)

| Player | Team | Position | ID |
|--------|------|----------|----|
| Connor McDavid | EDM | C | 8478402 |
| Nathan MacKinnon | COL | C | 8477492 |
| Nikita Kucherov | TBL | R | 8476453 |
| Leon Draisaitl | EDM | C | 8477934 |
| Auston Matthews | TOR | C | 8479318 |
| Macklin Celebrini | SJS | C | 8484801 |
| Mark Scheifele | WPG | C | 8476460 |
| David Pastrnak | BOS | R | 8477956 |
| Jason Robertson | DAL | L | 8480027 |
| Kirill Kaprizov | MIN | L | 8478864 |
| Martin Necas | COL | C | 8480039 |
| Andrei Vasilevskiy | TBL | G | 8476883 |
| Ilya Sorokin | NYI | G | 8478009 |
| Connor Bedard | CHI | C | 8484144 |

### Game Type IDs

| ID | Type |
|----|------|
| 1 | Preseason |
| 2 | Regular Season |
| 3 | Playoffs |
| 4 | All-Star |

### Division and Conference Abbreviations

| Abbreviation | Full Name |
|-------------|-----------|
| E | Eastern Conference |
| W | Western Conference |
| A | Atlantic Division |
| M | Metropolitan Division |
| C | Central Division |
| P | Pacific Division |

---

## Sources

- Live API responses from `api-web.nhle.com` fetched 2026-03-19
- [GitHub - Zmalski/NHL-API-Reference](https://github.com/Zmalski/NHL-API-Reference)
- [GitHub - coreyjs/nhl-api-py](https://github.com/coreyjs/nhl-api-py)
