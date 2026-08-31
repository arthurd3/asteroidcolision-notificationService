# Recorded NASA responses

These files stand in for api.nasa.gov in the client tests, so the suite needs no
network and no API key.

| File | Provenance |
|---|---|
| `donki-cme.json` | Captured from the live API. |
| `donki-gst.json` | Captured from the live API. |
| `donki-flr.json` | Captured from the live API. |
| `epic-natural.json` | Captured from the live API. |

All four are real responses, trimmed to a few representative records. They keep the
details that matter and that a hand-written file would get wrong:

- **seconds-less DONKI timestamps** (`2026-05-04T01:13Z`) — these parse only because
  `ISO_OFFSET_DATE_TIME` treats seconds as optional;
- **EPIC's non-ISO `date`** (`2026-08-29 00:41:06`) — a space where ISO-8601 requires
  a `T`, which is why `EpicImage` needs an explicit `@JsonFormat` pattern;
- **genuinely null fields** — `activeRegionNum` on a CME with no identified source,
  `linkedEvents` on an unlinked solar flare;
- **the range that matters** — flares of class C, M and X, and a geomagnetic storm
  with fourteen Kp readings alongside one with a single reading.

## Why the provenance is recorded at all

`@JsonIgnoreProperties(ignoreUnknown = true)` makes an *extra* field harmless. It does
nothing about a *missing* one: if a record names a component NASA does not actually
send, it deserialises to `null`, silently, and a test that parses a hand-written
fixture passes anyway.

`donki-gst.json` and `donki-flr.json` were hand-written from NASA's documentation for
a while, precisely because of a `DEMO_KEY` rate limit, and this file said so. They have
since been replaced with live captures and the records checked field by field against
them — no mismatches were found, but that was worth confirming rather than assuming.

**If you add a fixture, say where it came from.** The distinction is invisible in the
JSON itself and it changes what the test proves.

## Re-recording

```bash
set -a; . ./.env; set +a
S=$(date -u -d '-120 days' +%F); E=$(date -u +%F)

curl -s "https://api.nasa.gov/DONKI/CME?startDate=$S&endDate=$E&api_key=$NASA_API_KEY" | python3 -m json.tool
curl -s "https://api.nasa.gov/DONKI/GST?startDate=$S&endDate=$E&api_key=$NASA_API_KEY" | python3 -m json.tool
curl -s "https://api.nasa.gov/DONKI/FLR?startDate=$S&endDate=$E&api_key=$NASA_API_KEY" | python3 -m json.tool
curl -s "https://api.nasa.gov/EPIC/api/natural?api_key=$NASA_API_KEY"                  | python3 -m json.tool
```

Trim to a few records that keep the awkward cases above, then run:

```bash
./mvnw -pl asteroid-service test -Dtest='RestNasaDonkiClientTest+RestNasaEpicClientTest'
```

DONKI is slow — allow a couple of minutes — and it returns a transient `503` often
enough that a retry is worth building into any script that does this.
