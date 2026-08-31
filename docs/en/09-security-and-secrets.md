[← Spring Boot 3 to 4](08-spring-boot-3-to-4.md) · **English** · [Português (Brasil)](../pt-BR/09-security-and-secrets.md)

# Security and secrets

## A real incident, first

Commit `8b6a1f4` is called `security: purge committed .env files and add secret
hygiene`. Environment files containing real credentials had been committed to this
repository, and that commit removed them and added the `.gitignore` rules that stop it
happening again.

That is the most useful thing in this document, because it is not hypothetical. It is
also worth knowing what removing them does **not** do: `git rm` deletes a file from the
current tree, not from history. A credential that was ever pushed must be treated as
compromised and **rotated**, not just deleted.

```
### Secrets - never commit these ###
.env
.env.*
!.env.example
*.pem
*.key
secrets/
```

The rules are unanchored, so `.env` is ignored at any depth — which is why a stale
`notification-service/.env` left over from before that commit is untracked today. It
holds live-looking `EMAIL.USERNAME` and `EMAIL.PASSWORD` values that **nothing reads**:
the application uses `${MAIL_USERNAME}`/`${MAIL_PASSWORD}`, and Spring Boot does not
load `.env` files at all. It is dead residue, worth deleting, and worth rotating those
credentials if they were ever real.

`.env.example` is committed and holds the *shape* without the values. That is the
pattern: the repository documents which variables exist; it never carries what they
contain.

## The API key problem

NASA authenticates with `?api_key=...`. A query parameter is the worst place for a
credential, and it is not this project's choice:

- it appears in server access logs at every hop;
- it appears in browser history and in `Referer` headers;
- it appears in the message of any exception that includes the request URI.

Three defences, all in `asteroid-service`.

**The key never leaves the service.** No browser and no other service ever receives it.
`web-ui` has no NASA client at all.

**`NasaEndpoint` appends the key itself**, so no client can forget it — and throws
nothing that carries the URI:

```java
throw new NasaUnavailableException(displayName + " returned " + response.getStatusCode());
```

The status code, the API's name, nothing else.

**The cause's message is never copied.** `NasaEndpoint` keeps the underlying
`RestClientException` as the cause, and *that* does contain the full URI including the
key. But `ApiExceptionHandler` renders only `getMessage()`, never the cause, so the key
cannot reach a response body. `NasaEndpointTest` asserts both halves:
`hasMessageNotContaining(API_KEY)` on an error status, and again on a refused
connection.

## The EPIC image proxy

The one place the key problem becomes a design problem.

NASA's EPIC archive needs the key as a query parameter, so an archive URL cannot go
into an `<img src>` — that publishes the credential to every browser, proxy and history
file that sees the page. Compare APOD, whose URLs carry no key and are linked directly.
Whether a media URL needs a credential is a property of the individual API.

The solution has three parts:

1. **`EpicImageView` has no field capable of holding a NASA URL.** Its `imagePath` is a
   path on `asteroid-service` — `/api/v1/nasa/epic/image/natural/2026/08/29/epic_1b_…`
   — with no query string and no key. Because there is nowhere to put a real archive
   URL, this cannot regress by accident, only on purpose.
2. **`asteroid-service` serves that path** by fetching the bytes server-side.
3. **`web-ui` proxies it once more**, so the browser only ever talks to one origin.

### The proxy is the security-critical part

Those path segments are concatenated onto `https://api.nasa.gov` **with the key
attached**. Without validation the endpoint becomes an open proxy for any
`api.nasa.gov` path, signed with our credential — which is strictly *worse* than the
leak it exists to prevent.

`EpicImageAssembler` therefore:

- whitelists `{collection}` to `natural` or `enhanced`;
- anchors `{image}` to `^epic_[A-Za-z0-9]{1,8}_\d{14,20}$`, so a value containing a
  slash, a dot or a percent-encoded traversal cannot match;
- builds the date through `LocalDate.of(...)`, so `2026-13-40` is rejected before it
  becomes part of a URL;
- appends `.png` **server-side**, never taking the extension from the caller.

`EpicImageAssemblerTest` fires `../../../../planetary/apod`, `../DONKI/CME` and
`epic_1b_…?api_key=stolen` at it. `EpicControllerTest` asserts no response body
anywhere contains `api_key`, the configured key value, or the string `api.nasa.gov`.
`WebUiPagesIT` asserts the same about the rendered HTML.

**Honestly**: `epic.gsfc.nasa.gov` serves the same PNGs with no key at all, which would
make both proxy hops unnecessary. What proxying buys is a single origin and a cache in
front of a rate-limited upstream; what it costs is moving a couple of megabytes through
two JVMs for a photograph. The EPIC page says so out loud.

## Cross-site scripting in the front end

JSP's `${...}` does **not** escape, unlike Thymeleaf's `th:text`. Untrusted text
reaches these pages from APOD explanations, DONKI notes, EPIC captions, and
`notification_delivery.last_error`.

That last one deserves emphasis: it holds **whatever an SMTP server said**. It is a
string from a third party that this system stores in its own database and later renders
into HTML — the classic shape of a stored XSS. Every dynamic value goes through
`<c:out>`, and `WebUiPagesIT#escapesUntrustedText` proves it with
`<script>alert(1)</script>`.

## SQL injection

Not a risk here, and worth understanding why rather than assuming. Every query is
either a Spring Data derived method or a `@Query` with **named parameters**:

```java
where d.notification.eventId = :eventId
```

Values are bound by the driver, never concatenated. The one place a request value
reaches a query-like string is the NeoWs lookup id, and that is constrained in the
mapping itself:

```java
@GetMapping("/{id:\\d{4,10}}")
```

A non-numeric id matches no route at all, so it never reaches any code. That is
stronger than validating inside the method, and `NeoCatalogControllerTest` includes
`../planetary/apod` among its cases.

## What is deliberately missing

**There is no authentication.** Every endpoint is open. For a local teaching project
that is a reasonable choice, and it should be stated rather than assumed.

**The scan form has no CSRF token.** `POST /scan` triggers a real NASA read and
publishes real events. With no Spring Security and no session, there is currently
nothing to forge *into* — CSRF requires ambient credentials the browser attaches
automatically, and there are none.

That stops being true the moment anyone adds authentication. If you add login to this
project, add `spring-boot-starter-security` and a CSRF token to that form in the same
commit. A sentence here now is cheaper than the bug later.

**Actuator is partly exposed.** `health,info,metrics,prometheus`, with
`show-details: when-authorized` — so health details are hidden without authentication,
but the metrics endpoint is open. Fine locally, not fine on a public network.

## Try it yourself

```bash
# the key is nowhere in the rendered HTML
curl -s localhost:8082/epic | grep -c "api_key"     # 0

# nor in an error response
curl -s "localhost:8080/api/v1/nasa/apod?date=1990-01-01" | grep -c "api_key"   # 0

# the traversal defence
curl -s -o /dev/null -w "%{http_code}\n" \
  "localhost:8080/api/v1/nasa/epic/image/natural/2026/08/29/arbitrary"          # 400

# and the guards that keep it that way
./mvnw -pl asteroid-service test -Dtest='EpicImageAssemblerTest+EpicControllerTest+NasaEndpointTest'
```
