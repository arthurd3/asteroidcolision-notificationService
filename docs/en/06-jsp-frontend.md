[← Persistence and Flyway](05-persistence-and-flyway.md) · **English** · [Português (Brasil)](../pt-BR/06-jsp-frontend.md)

# The JSP front end

## Start with the honest part

Spring Boot's own reference documentation says:

> If possible, JSPs should be avoided. There are several known limitations when using
> them with embedded servlet containers.

That is Boot's position, it is reasonable, and a teaching repository that quietly
omitted it would be teaching you something false.

So why is this project using JSP anyway?

Because an enormous amount of existing Java runs on it. If you work on Java web
applications you *will* meet JSP, usually in something old enough that nobody is going
to rewrite it, and being able to read and safely modify it is a real skill. Learning it
in a small clean codebase is far better than meeting it for the first time in a
fifteen-year-old one.

If you are starting something new, use Thymeleaf. This project uses it too, for the
email bodies in `notification-service`.

## WAR, not JAR

The one structural difference between `web-ui` and the other two modules:

```xml
<packaging>war</packaging>
```

Boot's executable-JAR layout **cannot serve JSPs at all** — the pages live in the
archive's document root, which a JAR does not have. This is a requirement, not a
preference.

An executable WAR still works with `java -jar`, and can also be dropped into a
standalone Tomcat. `WebUiApplication extends SpringBootServletInitializer` is what
makes that second option real: a standalone container has no `main`, and drives startup
through `ServletContainerInitializer` instead.

`./mvnw -pl web-ui spring-boot:run` works exactly like the other modules, because the
run mojo filters only *test*-scoped artifacts from the classpath and therefore keeps
the `provided` Jasper.

## The dependencies, and why they are `provided`

```xml
<dependency>
  <groupId>org.apache.tomcat.embed</groupId>
  <artifactId>tomcat-embed-jasper</artifactId>
  <scope>provided</scope>
</dependency>
<dependency>
  <groupId>org.glassfish.web</groupId>
  <artifactId>jakarta.servlet.jsp.jstl</artifactId>
  <scope>provided</scope>
</dependency>
```

`provided` puts them under `WEB-INF/lib-provided` in the repackaged WAR, which
`java -jar` loads and a standalone container ignores — so neither ends up with two
copies of Jasper. `spring-boot-starter-tomcat` is `provided` for the same reason.

`tomcat-embed-jasper` brings `org.eclipse.jdt:ecj`, the compiler Jasper uses to turn a
`.jsp` into a servlet on first request.

There is **no `web.xml`**, and none is needed: `maven-war-plugin` has defaulted
`failOnMissingWebXml` to false since 3.1.0 when `jakarta.servlet.annotation.WebServlet`
is on the classpath, which `tomcat-embed-core` provides.

## Five things that fail silently

Each of these cost real time. None produces a useful error.

### 1. Taglib URIs must be the Jakarta ones

```jsp
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
```

The `http://java.sun.com/jsp/jstl/core` URI in every pre-2020 tutorial is **not
declared** by JSTL 3.0 and fails translation with "The absolute uri cannot be
resolved". Jakarta EE renamed the namespaces; the tutorials did not.

### 2. CSS must not live in `src/main/webapp`

Boot 4 defaults `server.servlet.register-default-servlet` to **false**. No
`DefaultServlet` is registered, so nothing serves the WAR's document root except Jasper
serving `.jsp`. A stylesheet under `webapp/` is a 404 with no explanation.

Static assets go in `src/main/resources/static/`, which Spring MVC's own
`ResourceHttpRequestHandler` serves. `resources/static/css/app.css` → `/css/app.css`.

### 3. `error.jsp` needs the whitelabel disabled — in the right spelling

```yaml
spring:
  web:
    error:
      whitelabel:
        enabled: false
```

Two separate things have to be right.

**The spelling.** The whole `server.error.*` family is deprecated at level `error`
since Boot 4.0.0, replaced by `spring.web.error.*`. The old key binds to nothing and
silently does not work.

**The resolver order.** Boot's whitelabel page is a `View` **bean named `error`**, and
`BeanNameViewResolver` (order 10) outranks `InternalResourceViewResolver`
(`LOWEST_PRECEDENCE`). While the whitelabel is enabled, returning `"error"` from a
`@ControllerAdvice` resolves to it and `/WEB-INF/jsp/error.jsp` is never reached — no
matter what your advice does.

### 4. `<fmt:formatDate>` cannot take `java.time`

JSTL 3.0 still only accepts `java.util.Date` and `Calendar`. Handed a `LocalDate` it
fails at *render* time, inside the page, with a message that names no field and no page.

The fix is not to convert in the JSP — that means scriptlets. It is to format in Java.
`Formats` is a `@Component` that produces strings, and the view models carry values
that are already right. Everything is UTC and says so; a page rendering astronomical
times in whatever zone the server happens to be in is quietly wrong for most readers.

### 5. JSP source files should stay ASCII

`pageEncoding` can only be declared once per translation unit — it is in `head.jspf` —
and a statically included `.jspf` does not inherit it for the purpose of reading its
*own* bytes. A literal UTF-8 character in a fragment is decoded with the container's
default and renders as mojibake (`â` where `◎` was meant).

Every JSP here is ASCII and uses HTML entities: `&#9678;`, `&middot;`, `&mdash;`.
Portable, and needs no `web.xml`.

## `${...}` does not escape

**The most important thing on this page.**

Unlike Thymeleaf's `th:text`, JSP's `${...}` writes raw. Untrusted text reaches these
pages from four directions:

- APOD `explanation` and `copyright`;
- DONKI `note` and `sourceLocation`;
- EPIC `caption`;
- `notification_delivery.last_error` — which contains whatever an SMTP server said.

So **every dynamic string goes through `<c:out>`**, whose `escapeXml` defaults to
`true`:

```jsp
<td class="wrap-text"><c:out value="${delivery.lastError}"/></td>
```

`WebUiPagesIT#escapesUntrustedText` feeds an APOD title of `<script>alert(1)</script>`
and asserts the response contains `&lt;script&gt;` and not the raw tag.

Note where the risk actually is. The scariest field is not the NASA data — it is
`last_error`, because it is a string from a third party that this system stores and
later renders.

## Layout: plain includes

JSP has no native layout mechanism. The options are Apache Tiles (retired by the ASF in
2018, no Jakarta EE release), SiteMesh (a servlet filter and a dependency), or JSP's own
static include. This project uses the third:

```jsp
<%@ include file="layout/head.jspf" %>
   ... page body ...
<%@ include file="layout/foot.jspf" %>
```

`<%@ include %>` is **static** — resolved at translation time, producing one compiled
servlet per page. `<jsp:include>` is **dynamic** — a request dispatch per include, on
every request, for markup that never varies. The `.jspf` extension marks a fragment as
not being a page anyone should request directly.

Everything lives under `WEB-INF/`, which a servlet container will not serve directly,
so nobody can request a `.jsp` and bypass the controller meant to populate its model.

## Why `@WebMvcTest` is not enough

**`@WebMvcTest` does not render JSPs.** There is no servlet container in a slice, so
`MockMvc` reports the forward to `/WEB-INF/jsp/apod.jsp` and stops. A broken taglib URI,
a typo in an EL expression, a method that does not exist on a view model — all pass.

So slice tests assert the view name and the model, never HTML:

```java
.andExpect(view().name("apod"))
.andExpect(forwardedUrl("/WEB-INF/jsp/apod.jsp"))
```

…and `WebUiPagesIT` — `@SpringBootTest(webEnvironment = RANDOM_PORT)` with WireMock
standing in for both backends — is the only test in the project that actually compiles
the pages. It is parameterised over every path. Details in [document 07](07-testing.md).

## Try it yourself

```bash
./mvnw -pl web-ui spring-boot:run
```

Then, with the backends stopped, open <http://localhost:8082>: every panel greys out
individually and names the command that starts the missing service. That is
`HomeController#optional`, and it is the only place a `BackendUnavailableException` is
swallowed — everywhere else the page *is* the backend's data, so failing to the error
view is the honest outcome.

Break something on purpose:

```bash
# change jakarta.tags.core to http://java.sun.com/jsp/jstl/core in head.jspf,
# restart, and load any page. The failure names the URI, which is the one
# genuinely helpful JSP error message you will get.

# move app.css from resources/static to webapp/css and reload:
# HTTP 200 for the page, 404 for the stylesheet, no error anywhere.
./mvnw -pl web-ui verify -Dtest='!*' -Dit.test=WebUiPagesIT
```
