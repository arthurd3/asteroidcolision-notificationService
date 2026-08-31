/**
 * Read models for what the two backends return.
 *
 * <p>These records duplicate shapes that already exist in asteroid-service and
 * notification-service, and that is a deliberate cost rather than an oversight.
 *
 * <p>The obvious alternative is to depend on those modules and reuse their types.
 * It was rejected because it inverts the dependency this architecture is built on:
 * the front end would compile against the producer's internals, so renaming a field
 * in {@code EpicImageView} would break a module that is only supposed to be an HTTP
 * client. The other alternative, moving the shapes into {@code contracts}, is worse -
 * {@code contracts} is the Kafka wire contract between the two services and is
 * deliberately Spring-free and dependency-light; filling it with HTTP view models
 * would couple the event schema to the web layer and give both services a reason to
 * change it.
 *
 * <p>So the duplication buys a real boundary: web-ui talks to its backends over HTTP
 * and JSON like any other client would, and {@code ignoreUnknown} means a backend can
 * add a field without this module needing to know. What it costs is that a field
 * genuinely removed upstream shows up here as a null rather than a compile error.
 * {@code WebUiPagesIT} is what catches that, by rendering every page against stubbed
 * backend responses.
 */
package com.arthur.asteroid.webui.backend.dto;
