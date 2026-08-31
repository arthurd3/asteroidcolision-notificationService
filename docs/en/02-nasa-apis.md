[← Architecture](01-architecture.md) · **English** · [Português (Brasil)](../pt-BR/02-nasa-apis.md)

# The NASA APIs

Five APIs, all under `https://api.nasa.gov`, all authenticated the same way: an
`api_key` query parameter. Get one free at <https://api.nasa.gov>.

> **`DEMO_KEY` is 30 requests per hour across *every* endpoint**, not 30 per endpoint.
> One load of this project's home page spends four. Get a real key before you do
> anything else — this is the single most common reason the project appears broken.

## Near-Earth Object Web Service (NeoWs)

Three endpoints over the same resource. Only the first drives the alerting pipeline;
the other two exist to be browsed.

| Endpoint | Purpose |
|---|---|
| `GET /neo/rest/v1/feed?start_date=&end_date=` | objects approaching between two dates |
| `GET /neo/rest/v1/neo/{id}` | one object, with its orbit |
| `GET /neo/rest/v1/neo/browse?page=&size=` | the whole catalogue, paginated |

**The trap: the feed's window is capped at seven days.** That is NASA's limit, not a
choice made here, which is why `ScanWindow.MAX_FEED_DAYS` is 7 and why the front end's
date form refuses a wider range.

**The second trap: `orbital_data` is only on lookup and browse.** The feed omits it to
keep the payload small. One `Asteroid` record covers all three endpoints — forking it
into `Asteroid` and `NeoDetail` would duplicate `estimated_diameter`,
`close_approach_data` and both helper methods so that one nullable field could be
non-null in one of them. The cost is that `orbitalData()` is null on feed responses,
which is stated on the record rather than left to be discovered.

**Browse is genuinely large**: about 62,000 objects, so more than 3,000 pages at
NASA's maximum page size of 20. There is no "fetch it all and filter" version.

Every orbital element arrives as a decimal *string*, and stays one. Parsing twenty
fields to `BigDecimal` in order to render them back as text buys nothing and turns one
malformed field into a failure of the whole response. Only
`miss_distance.kilometers` is parsed, because it crosses the Kafka boundary as a
`BigDecimal` in the event contract — parsing it at the edge means a bad value is a
skipped object rather than a failure inside the consumer's listener.

**One field is required.** `is_potentially_hazardous_asteroid` maps to a primitive
`boolean`, so a response without it fails to parse rather than defaulting to `false`.
That is deliberate: this flag decides whether an alert is published, and treating
"NASA did not say" as "not hazardous" is the one wrong answer.

## APOD — Astronomy Picture of the Day

```
GET /planetary/apod?date=YYYY-MM-DD
```

Small, fast, and the friendliest of the five. The archive starts on **1995-06-16**;
`ApodController` rejects anything earlier without calling NASA, because a knowably
invalid request must not spend one of thirty hourly slots.

**The trap: `media_type`.** About one entry a week is a video, and on those days `url`
is a YouTube or Vimeo embed rather than an image, and `hdurl` is absent entirely. A
page that renders every entry in an `<img>` is broken once a week. The branch lives on
the `ApodEntry` record — `image()`, `video()`, `displayUrl()` — rather than being
rediscovered by each view.

**APOD's URLs carry no API key**, so a browser can load them directly. Hold that
thought for EPIC.

## DONKI — space weather

```
GET /DONKI/CME?startDate=&endDate=      coronal mass ejections
GET /DONKI/GST?startDate=&endDate=      geomagnetic storms
GET /DONKI/FLR?startDate=&endDate=      solar flares
```

Thematically the closest neighbour to the asteroid feed — both answer "what is
happening out there that could affect Earth". Operationally it is the opposite of
every other API here.

**The trap: DONKI is genuinely slow.** A query over thirty days routinely takes 60–90
seconds. Measured, not assumed: the first attempt at capturing a response for this
project timed out at 30 seconds and only succeeded under a 90-second budget. This one
number is the reason the whole client configuration is shaped the way it is —
[document 03](03-http-clients-and-resilience.md) is mostly about it.

**The second trap: `camelCase` parameters.** `startDate` and `endDate`, not NeoWs's
`start_date` and `end_date`. The two APIs simply disagree, and DONKI *silently
ignores* a parameter it does not recognise and answers with its own default window.
Getting this wrong looks like working code returning the wrong events.

**The third trap: timestamps have no seconds.** `2026-08-02T10:45Z`. This parses only
because `ISO_OFFSET_DATE_TIME` treats seconds as optional; a hand-written
`yyyy-MM-dd'T'HH:mm:ss'Z'` pattern fails on every record.

**All three record shapes are verified against live captures**, and the fixtures in
`asteroid-service/src/test/resources/nasa/` are real responses trimmed to a few
representative records.

That is worth stating because for a while two of them were not. `GeomagneticStorm` and
`SolarFlare` were written from NASA's documentation while a `DEMO_KEY` rate limit made
capture impossible, and the code and docs said so in as many words. When a real key
became available they were checked field by field: no mismatches, but that was worth
confirming rather than assuming. `@JsonIgnoreProperties(ignoreUnknown = true)` makes an
*extra* field harmless and does nothing about a *missing* one — a component whose name
does not match deserialises to `null`, silently, and a test parsing a hand-written
fixture passes anyway.

**A fourth trap, found during that capture: DONKI returns transient 503s.** Not a rate
limit — an upstream failure that succeeds on retry. Which is a fair advertisement for
the retry policy in [document 03](03-http-clients-and-resilience.md).

## EPIC — Earth Polychromatic Imaging Camera

```
GET /EPIC/api/natural                    metadata for the most recent set
GET /EPIC/api/natural/date/YYYY-MM-DD    one day
GET /EPIC/api/natural/available          every date with frames
```

Full-disc photographs of Earth from the DSCOVR spacecraft, about a million miles out,
roughly a dozen frames a day.

**The trap: `date` is not ISO-8601.** It is `"2026-08-29 00:41:06"` — a space where
ISO requires a `T`. Jackson cannot parse it without being told, so the `@JsonFormat`
pattern on `EpicImage` is load bearing rather than decoration: without it every single
frame fails.

**The second trap: there is no image URL in the response.** It has to be assembled:

```
/EPIC/archive/natural/{yyyy}/{MM}/{dd}/png/{image}.png
```

…and the date parts come from the `date` field, **not** from `identifier`. The
identifier is also timestamp-shaped and looks like it would do, but the two disagree
by minutes, so using it 404s on any frame crossing midnight UTC.

**The third trap, and the interesting one: that archive URL needs the API key.** So
unlike APOD, an EPIC image URL can never be handed to a browser — doing so publishes
the credential to every browser, proxy and history file that sees it. The solution is
in [document 09](09-security-and-secrets.md), and it is the most security-relevant code
in the project.

## Mars Rover Photos — decommissioned

`https://api.nasa.gov/mars-photos/*` is documented and dead. It returns a Heroku
"No such app" 404 page: the backend it proxied has been shut down.

This is in the documentation on purpose. "The API in the tutorial no longer exists" is
a thing learners hit constantly, and how you establish it is more useful than the API
would have been. The check took one command:

```bash
curl -s -w "\nHTTP=%{http_code}\n" \
  "https://api.nasa.gov/mars-photos/api/v1/rovers/curiosity/photos?sol=1000&api_key=DEMO_KEY" \
  | tail -3
```

An HTML error page where JSON was promised, and a 404 that comes from Heroku rather
than from NASA, tells you the proxy target is gone rather than that your request was
wrong.

## Try it yourself

```bash
set -a; . ./.env; set +a

# APOD - fast
curl -s "https://api.nasa.gov/planetary/apod?api_key=$NASA_API_KEY" | head -c 300

# the seconds-less DONKI timestamp, and the slowness. Allow 90 seconds.
time curl -s "https://api.nasa.gov/DONKI/CME?startDate=2026-08-01&endDate=2026-08-31&api_key=$NASA_API_KEY" \
  | head -c 200

# EPIC's non-ISO date
curl -s "https://api.nasa.gov/EPIC/api/natural?api_key=$NASA_API_KEY" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['date'])"
```

The third command prints something like `2026-08-29 00:41:06`. That space is the whole
lesson.
