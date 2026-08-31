[← The NASA APIs](02-nasa-apis.md) · **English** · [Português (Brasil)](../pt-BR/03-http-clients-and-resilience.md)

# HTTP clients and resilience

The most detailed reasoning in the project, and it all follows from one measurement:
**a DONKI query takes 60–90 seconds while the NEO feed answers in under two.**

## Why `base-url` had to become the API root

`asteroid.nasa.base-url` used to be the full feed endpoint:

```yaml
asteroid:
  nasa:
    base-url: https://api.nasa.gov/neo/rest/v1/feed   # before
```

…and `RestNasaNeoClient` did `restClientBuilder.baseUrl(properties.baseUrl())`. That
works when there is exactly one API and stops working the moment there are five.

The fix is not just mechanical. An endpoint's path is part of *that client's contract
with NASA*, not a deployment setting: which host to call can differ between
environments, but `/planetary/apod` is what APOD is, everywhere, forever. So the root
lives in configuration and the path lives in the client:

```yaml
asteroid:
  nasa:
    base-url: https://api.nasa.gov          # after
```

```java
static final String PATH = "/planetary/apod";   // in RestNasaApodClient
```

A reader of the client can now see which endpoint it calls without cross-referencing a
YAML file.

## Why one `RestClient` per API

`spring.http.clients.read-timeout` is a single number. Set it to 10 seconds and DONKI
cannot work at all. Set it to 120 and a hung NEO feed pins a request thread for two
minutes. There is no value that is right for both.

Boot 4 makes per-client timeouts about five lines. `HttpClientAutoConfiguration`
publishes an `HttpClientSettings` bean bound from `spring.http.clients.*`, and
`HttpClientSettings#withTimeouts` returns a copy with different ones:

```java
final HttpClientSettings perApi = settings.getIfAvailable(HttpClientSettings::defaults)
        .withTimeouts(timeouts.connect(), timeouts.read());

return builder.clone()
        .baseUrl(properties.baseUrl())
        .requestFactory(factories.getIfAvailable(ClientHttpRequestFactoryBuilder::detect)
                .build(perApi))
        .build();
```

The point of going through `HttpClientSettings` rather than hand-rolling a request
factory is that everything *else* Boot manages — redirect handling, SSL bundles, which
HTTP client implementation was detected — is preserved. Overriding two fields is not
the same as replacing the object.

See `NasaRestClientsConfig`. Four beans, differing only in their timeout budget:

| Client | connect | read | why |
|---|---|---|---|
| `nasaNeoRestClient` | 3s | 10s | fast, and on the alerting path |
| `nasaApodRestClient` | 3s | 10s | small payload |
| `nasaDonkiRestClient` | 3s | **120s** | measured: 60–90s is normal |
| `nasaEpicRestClient` | 3s | 30s | 2 MB PNGs |

Four `RestClient` beans in one context make a bare `RestClient` injection ambiguous,
so every client takes a `@Qualifier`. The parent POM compiles with `-parameters`, so
name-based resolution would also work — but relying on a parameter name to select a
timeout budget is not obvious enough to be worth it.

## `NasaEndpoint`: four rules written once

Every NASA client needs the same four behaviours. With one client that is fine; with
four it is four copies and only the first has a test.

```java
<T> T get(String path, UnaryOperator<UriBuilder> parameters, ParameterizedTypeReference<T> type)
```

1. An error status becomes a `NasaUnavailableException`.
2. A body that will not parse becomes one too.
3. A `null` body becomes one too, rather than a `NullPointerException` three frames up.
4. **The request URI never reaches a log line or an exception message.**

Rule 4 is why the class exists. NASA takes the key as a query parameter, so any code
that helpfully includes the URI in an error is publishing the credential. `NasaEndpoint`
appends the key itself — so no client can forget — and throws nothing carrying the URI.

It keeps the underlying `RestClientException` as the *cause*, which does contain the
full URI in its own message, but never copies that text: `ApiExceptionHandler` renders
`getMessage()` straight into the response body. `NasaEndpointTest` pins both halves.

Composition rather than an abstract base class, deliberately. The clients own their
paths, DTOs and resilience instance names; they share only transport.

## Resilience4j: instances per failure domain

The naming rule is: **one instance per upstream failure domain, not per method.**

- The feed, lookup and browse share `nasaNeo`. They are one service behind one
  rate-limit bucket — a failing feed genuinely predicts a failing lookup, so they
  should share a circuit.
- DONKI gets `nasaDonki` precisely because it does *not*. Its latency profile is
  unrelated, and a DONKI timeout must not open a circuit that sheds APOD calls.

```yaml
resilience4j:
  retry:
    configs:
      default:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        retry-exceptions:
          - com.arthur.asteroid.alerting.nasa.NasaUnavailableException
    instances:
      nasaNeo:   { base-config: default }
      nasaApod:  { base-config: default }
      nasaDonki: { base-config: default, max-attempts: 2 }
      nasaEpic:  { base-config: default }
```

`configs.default` is special-cased by Resilience4j: any instance naming no
`base-config` inherits it. Declaring the policy once means it cannot be changed for
three APIs and forgotten on the fourth.

**DONKI retries once, not twice.** Three attempts at ninety seconds is a
four-and-a-half-minute request, which nobody is still waiting for.

### The coupling that has no compiler

`retry-exceptions` names `NasaUnavailableException` by **fully-qualified string**.
Move or rename that class and retry silently stops working — no startup error, no
warning, just a policy that never matches. This is also why there is one exception type
for all four APIs rather than one per API: every subclass would be another FQCN in a
list nobody re-reads.

`NasaConfigurationTest` turns that into a failing build. It loads the real YAML, pulls
the registries, and asserts the predicate still matches:

```java
assertThat(retryRegistry.retry(instance).getRetryConfig()
        .getExceptionPredicate()
        .test(new NasaUnavailableException("boom")))
        .isTrue();
```

It catches the exception-FQCN half of the coupling. It does **not** catch a mistyped
*instance* name — Resilience4j silently creates an instance from the default config
instead of failing. The mitigation is that the default config is sane, so the failure
mode is degraded behaviour rather than breakage. Worth knowing.

## The bulkhead

```yaml
  bulkhead:
    instances:
      nasaDonki:
        max-concurrent-calls: 2
        max-wait-duration: 0
```

A DONKI call holds a Tomcat worker for up to two minutes. Tomcat's default
`threads.max` is 200, so a handful of refreshes is survivable — but "a handful of
impatient page refreshes hold several request threads for minutes" is exactly how a
service starves without anything appearing to be broken. `max-wait-duration: 0` means
the third caller is rejected immediately rather than queueing behind the first two.

`BulkheadFullException` maps to **429**, not 503. Nothing is broken; the caller is one
of too many asking for a slow query at once, and retrying shortly will work.

## Caching, which is not optional here

`DEMO_KEY` allows 30 requests an hour across *every* endpoint. One load of the home
page spends four. Without a cache the front end spends most of its life rendering the
error page, which reads as a bug in this project rather than as a quota.

Caffeine, ten-minute TTL, and two rules worth understanding:

**`findAsteroids` is deliberately not cached.** It is what the alerting scan calls, and
a cached scan would publish events from a window read minutes ago while reporting them
as current. The two read-only NeoWs methods beside it *are* cached, which makes the
distinction visible rather than accidental.

**One cache name per method, never one per API.** Spring's `SimpleKeyGenerator` builds
its key from the method *arguments only* — the method itself is not part of it — and
DONKI's three endpoints all take `(LocalDate from, LocalDate to)`. Sharing a cache
between them means a request for solar flares returns the coronal mass ejections
already fetched for that window: silently, and looking entirely plausible.
`NasaCacheIT#donkiEndpointsDoNotShareACache` exists so that stays true.

That test is an **integration** test rather than a unit test, because `@Cacheable` is
proxy-based: calling a client directly bypasses the advice, and a unit test would pass
whether or not caching worked.

## The other way to do all this

Boot 4 ships an HTTP Service Registry — `@ImportHttpServices` with
`spring.http.serviceclient.<group>.*` — which gives per-group base URLs and timeouts
declaratively, with no configuration class at all. It is a genuinely good option.

It is not used here because it replaces the hand-written `RestClient` idiom with
`@HttpExchange` interfaces, which is a second and unrelated lesson. If you are building
something new rather than reading something, look at it.

## Try it yourself

```bash
# per-API timeouts really are different
grep -A2 "donki:" -A6 asteroid-service/src/main/resources/application.yaml | grep timeouts

# the FQCN coupling, guarded
./mvnw -pl asteroid-service test -Dtest=NasaConfigurationTest

# break it on purpose: change the class name in retry-exceptions to
# com.arthur.asteroid.alerting.nasa.NoSuchException and run it again.
# The build fails. Without that test, nothing would have.

# the cache really spares the rate limit
./mvnw -pl asteroid-service verify -Dtest='!*' -Dit.test=NasaCacheIT
```
