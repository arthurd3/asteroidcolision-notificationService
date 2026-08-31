# Recorded NASA responses

These files stand in for api.nasa.gov in the client tests, so the suite needs no
network and no API key.

| File | Provenance |
|---|---|
| `donki-cme.json` | **Captured from the live API.** Field names, the seconds-less `startTime` and the null `activeRegionNum` are all as NASA sent them. |
| `epic-natural.json` | **Captured from the live API.** Note `date` is `"2026-08-29 00:41:06"` — a space, not the `T` ISO-8601 requires. |
| `donki-gst.json` | **Hand-written from NASA's documentation — NOT captured.** |
| `donki-flr.json` | **Hand-written from NASA's documentation — NOT captured.** |

## Why the last two matter

`@JsonIgnoreProperties(ignoreUnknown = true)` makes an *extra* field harmless. It does
nothing about a *missing* one: if `GeomagneticStorm` names a component DONKI does not
actually send, it deserialises to `null`, silently, and the test still passes — because
the test parses this hand-written file rather than a real response.

So these two fixtures prove the records parse *these files*, and nothing more.

## Settling it

DEMO_KEY is capped at 30 requests/hour across every api.nasa.gov endpoint, which is why
they were not captured. With a real key from <https://api.nasa.gov>:

```bash
set -a; . ./.env; set +a
S=$(date -u -d '-30 days' +%F); E=$(date -u +%F)

curl -s "https://api.nasa.gov/DONKI/GST?startDate=$S&endDate=$E&api_key=$NASA_API_KEY" \
  | python3 -m json.tool > asteroid-service/src/test/resources/nasa/donki-gst.json

curl -s "https://api.nasa.gov/DONKI/FLR?startDate=$S&endDate=$E&api_key=$NASA_API_KEY" \
  | python3 -m json.tool > asteroid-service/src/test/resources/nasa/donki-flr.json

./mvnw -pl asteroid-service test -Dtest=RestNasaDonkiClientTest
```

Then fix whatever the test reports, delete the "unverified" paragraphs from
`GeomagneticStorm` and `SolarFlare`, and update the table above. Allow a couple of
minutes — DONKI is slow.
