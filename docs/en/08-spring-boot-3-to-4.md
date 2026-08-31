[← Testing](07-testing.md) · **English** · [Português (Brasil)](../pt-BR/08-spring-boot-3-to-4.md)

# Spring Boot 3 to 4

Everything in this document cost someone real time to find. Most produce no useful
error message; several produce no error at all.

## Starters were renamed

| Boot 3 | Boot 4 |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` |
| `org.springframework.kafka:spring-kafka` | `spring-boot-starter-kafka` |
| `org.flywaydb:flyway-core` alone | `spring-boot-starter-flyway` |
| *(part of `-web`)* | `spring-boot-starter-restclient` |

**`-restclient` is the one that surprises people.** `RestClient` and `RestTemplate`
auto-configuration is no longer part of the web starter. Without it you get no
`RestClient.Builder` bean, and the failure is a missing-bean error at startup that does
not mention the starter you need.

**`flyway-core` alone is no longer enough.** The auto-configuration moved into
`spring-boot-flyway`, which the starter brings. With only `flyway-core` on the
classpath, migrations simply never run — no error, and with `ddl-auto: validate` you
then get a schema validation failure that blames the entity mapping.

**Resilience4j is `@Aspect`-based**, so it needs `spring-boot-starter-aspectj`. Without
it `@Retry` and `@CircuitBreaker` compile, start, and do nothing at all.

## `spring.http.client.*` → `spring.http.clients.*`

Plural. Deprecated since 4.0.0. The singular keys still bind, so nothing breaks
immediately — but they are marked deprecated in the metadata, IDEs flag them, and
`spring-boot-properties-migrator` complains.

## `server.error.*` → `spring.web.error.*`

**This one silently does nothing.** The whole family — `server.error.whitelabel.enabled`,
`server.error.include-message`, `server.error.path` and the rest — is deprecated at
level **`error`** since 4.0.0, meaning it no longer binds. Set
`server.error.whitelabel.enabled: false` in Boot 4 and the whitelabel stays on.

That matters here because a JSP `error.jsp` is unreachable while the whitelabel is
enabled — see [document 06](06-jsp-frontend.md).

## `server.servlet.register-default-servlet` now defaults to `false`

No `DefaultServlet` is registered, so nothing serves a WAR's document root except
Jasper serving `.jsp`. Static assets under `src/main/webapp/` return 404 with no
explanation. Put them in `src/main/resources/static/`.

## Per-client HTTP settings are now first-class

Not a trap — an improvement worth knowing about. `HttpClientAutoConfiguration` publishes
an `HttpClientSettings` bean, and `withTimeouts(Duration, Duration)` returns a copy:

```java
settings.getIfAvailable(HttpClientSettings::defaults)
        .withTimeouts(connect, read)
```

Combined with `ClientHttpRequestFactoryBuilder.build(HttpClientSettings)` and
`RestClient.Builder#requestFactory`, per-client timeouts are a few lines and you keep
every other setting Boot manages. See [document 03](03-http-clients-and-resilience.md).

Boot 4 also adds an HTTP Service Registry (`@ImportHttpServices`,
`spring.http.serviceclient.<group>.*`) which does this declaratively.

## Test annotations moved packages

```java
// Boot 3: org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

// and, less obviously
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
```

The test starters were split up too: `spring-boot-starter-webmvc-test`,
`spring-boot-starter-data-jpa-test`, `spring-boot-starter-restclient-test`,
`spring-boot-starter-kafka-test`.

## Jackson 3

The runtime package is `tools.jackson`; **annotations stay on
`com.fasterxml.jackson.annotation`**. That split is deliberate but disorienting: a
class can import from both.

```java
import com.fasterxml.jackson.annotation.JsonProperty;   // annotation
import tools.jackson.databind.ObjectMapper;             // runtime
```

`java.time` support is folded into `jackson-databind` — no separate `jsr310` module.

## Hibernate 7

`MySQL8Dialect` was removed. Do not replace it with another dialect class: the dialect
is auto-detected from JDBC metadata, so the correct fix is to delete the property.

## Testcontainers 2.0

Two changes, both compile errors rather than silent failures.

**Module artifact ids are prefixed:** `org.testcontainers:mysql` →
`org.testcontainers:testcontainers-mysql`, and the same for `-kafka`,
`-junit-jupiter`.

**The self-typed generic was dropped:**

```java
static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");   // no <?>
```

Also: there are now three Kafka container classes. `org.testcontainers.kafka.KafkaContainer`
is the KRaft-native one and the right choice against a modern broker.

## Failsafe is managed but not activated

`spring-boot-starter-parent` sets Failsafe's version but does not bind its goals.
Without an explicit `<executions>` block in your own POM, **every `*IT` class is
silently skipped and the build reports success**. That is worse than having no
integration tests, because it looks like you have them.

## The Resilience4j BOM gap

`resilience4j-bom` 2.4.0 lists `resilience4j-spring-boot3` but **not** the Boot 4
starter, so importing the BOM does not manage `resilience4j-spring-boot4`. It has to be
pinned by hand:

```xml
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-spring-boot4</artifactId>
  <version>${resilience4j.version}</version>
</dependency>
```

Tracked upstream at <https://github.com/resilience4j/resilience4j/issues/2427>.

## WireMock versus Jetty

Plain `org.wiremock:wiremock` needs Jetty 11; Boot 4 manages Jetty 12. The mismatch
fails at startup with "Jetty 11 is not present". Use the shaded
`org.wiremock:wiremock-standalone`, which carries its own server.

## Kafka 4 removed ZooKeeper

`docker-compose.yml` runs a single-node KRaft broker. Spring Kafka's test broker is
KRaft-only now, so keeping ZooKeeper locally would only mean development and test
disagreed about the topology.

## Where to look next

Almost every item above is also recorded as a comment at the place it applies — in
`pom.xml`, `asteroid-service/src/main/resources/application.yaml`, or the module POMs.
This document exists because a comment has room for the fix but not for the reasoning.

## Try it yourself

```bash
# every migration note that lives in the build files
grep -rn "Boot 4\|was renamed\|no longer\|deprecated" --include=pom.xml --include=*.yaml . | grep -v target

# confirm a deprecation for yourself, straight from Boot's own metadata
python3 - <<'EOF'
import zipfile, json, glob
for j in glob.glob('~/.m2/repository/org/springframework/boot/**/*4.1.1.jar'.replace('~','/home/'+__import__('getpass').getuser()), recursive=True):
    try: z = zipfile.ZipFile(j)
    except Exception: continue
    for n in z.namelist():
        if n.endswith('spring-configuration-metadata.json'):
            for p in json.loads(z.read(n)).get('properties', []):
                if p['name'].startswith('server.error') and p.get('deprecation'):
                    print(p['name'], '->', p['deprecation'].get('replacement'))
EOF
```

The second command prints the replacement for every `server.error.*` key, from the jar
rather than from anyone's memory.
