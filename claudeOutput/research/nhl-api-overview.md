# NHL Web API — Overview

> **API Version**: v1
> **Documented**: 2026-03-19
> **Base URL**: `https://api-web.nhle.com`
> **Secondary Base URL**: `https://api.nhle.com/stats/rest`
> **Official Documentation**: None — this is an undocumented internal API used by the NHL website

---

## What This API Is

The NHL Web API (`api-web.nhle.com`) is the backend data service that powers the official NHL website (nhl.com). It is not publicly documented or officially supported by the NHL, but it is freely accessible — no API key, OAuth token, or any form of authentication is required. The community has reverse-engineered and documented it extensively.

A second base URL, `api.nhle.com/stats/rest`, serves more traditional "stats REST" queries and complements the web API with additional player, team, and franchise data.

---

## Authentication

**There is no authentication required.** All endpoints are open HTTP GET requests. No API keys, tokens, or headers are needed. You can call any endpoint directly from an Android app without any credential management.

```
GET https://api-web.nhle.com/v1/standings/now
```

That is a complete, working request.

---

## Rate Limits

**No rate limits are officially documented.** Community developers have not widely reported hitting rate limits under normal usage patterns (fetching data for apps, scripts, and analysis tools). That said:

- Since this is an undocumented internal API, the NHL could impose limits or change behavior without notice.
- For a trivia app, standard practices apply: cache responses locally, avoid hammering the API in tight loops, and use the "current" or "now" endpoints rather than repeated date-range polling.
- A reasonable safe limit to self-impose: no more than 1 request per second per endpoint.

---

## CORS Policy

The API supports CORS (Cross-Origin Resource Sharing) for browser-based requests. For an Android app making direct HTTP requests, CORS does not apply — you can call the API freely from any Android HTTP client (Retrofit, OkHttp, Ktor, etc.).

---

## Request Requirements

- **Method**: HTTP GET only (all known endpoints are read-only)
- **Headers**: None required. Standard `Accept: application/json` is optional but harmless.
- **Body**: None (GET requests)
- **Query Parameters**: Some endpoints accept optional query parameters (documented per endpoint)

---

## Response Format

All responses are JSON. There is no XML, CSV, or other format available.

Multi-language string fields use an object with a `default` key (English) and optional locale keys:

```json
"teamName": {
  "default": "Maple Leafs",
  "fr": "Maple Leafs"
}
```

---

## Season ID Format

Seasons are represented as 8-digit integers combining start and end years:

| Season | ID |
|--------|----|
| 2025-2026 | `20252026` |
| 2024-2025 | `20242025` |
| 2023-2024 | `20232024` |
| 1917-1918 (first NHL season) | `19171918` |

---

## Game Type Codes

| Code | Meaning |
|------|---------|
| `1` | Preseason |
| `2` | Regular Season |
| `3` | Playoffs |
| `4` | All-Star |

---

## Game State Codes

| Code | Meaning |
|------|---------|
| `FUT` | Future (not started) |
| `PRE` | Pregame |
| `LIVE` | Game in progress |
| `CRIT` | Critical moment (game is close/late) |
| `OFF` | Official (game is over) |
| `FINAL` | Final |

---

## Game ID Format

Game IDs are 10-digit integers with an embedded structure:

```
2025021083
^^^^         = season start year (2025 = 2025-26 season)
    ^^       = game type (02 = regular season)
      ^^^^   = game number within that type (1083)
```

Examples:
- `2025020001` — first regular season game of 2025-26
- `2025030111` — playoff game (game type 03)

---

## Team Identifiers

Teams are identified by both a numeric ID and a 3-letter abbreviation (tricode):

| Team | Tricode | Numeric ID |
|------|---------|------------|
| Toronto Maple Leafs | TOR | 10 |
| Edmonton Oilers | EDM | 22 |
| Colorado Avalanche | COL | 21 |
| Tampa Bay Lightning | TBL | 14 |
| Boston Bruins | BOS | 6 |
| New York Rangers | NYR | 3 |
| Chicago Blackhawks | CHI | 16 |
| Detroit Red Wings | DET | 17 |
| Seattle Kraken | SEA | 55 |
| Winnipeg Jets | WPG | 52 |

Most web API endpoints use the 3-letter tricode. The stats REST API (`api.nhle.com/stats/rest`) uses numeric IDs.

> **Note**: The team list from `api.nhle.com/stats/rest/en/team` returns 62 entries including historical/defunct franchises and placeholder entries (id 70 = "To be determined", id 99 = "NHL").

---

## Player IDs

Players are identified by numeric IDs (typically 7 digits starting with 847x or 848x for modern players):

| Player | ID |
|--------|----|
| Connor McDavid | `8478402` |
| Nathan MacKinnon | `8477492` |
| Nikita Kucherov | `8476453` |
| Leon Draisaitl | `8477934` |
| Auston Matthews | `8479318` |
| Andrei Vasilevskiy | `8476883` |
| Macklin Celebrini | `8484801` |

Player IDs are stable and do not change when a player changes teams.

---

## Asset URLs

The API returns URLs to NHL-hosted assets (logos, headshots):

- **Player headshots**: `https://assets.nhle.com/mugs/nhl/20252026/{TEAM}/{PLAYER_ID}.png`
  - Example: `https://assets.nhle.com/mugs/nhl/20252026/EDM/8478402.png`
- **Team logos (light)**: `https://assets.nhle.com/logos/nhl/svg/{TEAM}_light.svg`
  - Example: `https://assets.nhle.com/logos/nhl/svg/EDM_light.svg`
- **Team logos (dark)**: `https://assets.nhle.com/logos/nhl/svg/{TEAM}_dark.svg`

---

## Terms of Use / Legal

The NHL has no published developer terms of service for this API. Since it is an undocumented internal API:

- There is no official license to use it
- The NHL could shut it down or restrict access at any time
- Use it for personal/educational projects at your own risk
- Do not use it in ways that would stress the NHL's infrastructure
- Do not resell the data

Community libraries wrapping this API (nhl-api-py, Nhl.Api for .NET) have operated for years without legal interference.

---

## Stability / Versioning

This API uses a `/v1/` prefix, suggesting versioning awareness, but the NHL has not documented any versioning or migration policy. The API was significantly overhauled in 2023 (replacing the older `statsapi.web.nhl.com` API). Breaking changes can occur without notice.

---

## Key Community Resources

- [Zmalski/NHL-API-Reference](https://github.com/Zmalski/NHL-API-Reference) — Most comprehensive unofficial endpoint reference
- [dfleis/nhl-api-docs](https://github.com/dfleis/nhl-api-docs) — Automated documentation of 500+ endpoints
- [coreyjs/nhl-api-py](https://github.com/coreyjs/nhl-api-py) — Python wrapper (2025/2026 updated)
- [Afischbacher/Nhl.Api](https://github.com/Afischbacher/Nhl.Api) — .NET wrapper

---

## Sources

- [GitHub - Zmalski/NHL-API-Reference](https://github.com/Zmalski/NHL-API-Reference)
- [GitHub - coreyjs/nhl-api-py](https://github.com/coreyjs/nhl-api-py)
- [GitHub - dfleis/nhl-api-docs](https://github.com/dfleis/nhl-api-docs)
- [GitHub - dword4/nhlapi](https://github.com/dword4/nhlapi)
- [freepublicapis.com - NHL API Documentation](https://www.freepublicapis.com/nhl-api-documentation)
- Live API responses fetched on 2026-03-19
