package com.arthur.asteroid.alerting.nasa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * One NASA API: a {@link RestClient}, the API key, and the failure translation that
 * every caller must not get wrong.
 *
 * <p>Composition rather than an abstract base class, so the four rules that matter
 * are written once and tested once instead of being copy-pasted into four clients
 * where the fourth forgets one:
 *
 * <ol>
 *   <li>an error status becomes a {@link NasaUnavailableException};</li>
 *   <li>a body that will not parse becomes one too;</li>
 *   <li>a null body becomes one too, rather than a {@code NullPointerException}
 *       three frames further up;</li>
 *   <li><em>the request URI never reaches a log line or an exception message.</em></li>
 * </ol>
 *
 * <p>That last rule is the reason this class exists at all. NASA takes the API key as
 * an {@code api_key} query parameter - their design, not a choice made here - so any
 * code that helpfully includes the URI in an error is publishing the credential to
 * the log aggregator. {@link #get} appends the key itself, so no client has to
 * remember to, and nothing it throws carries the URI.
 *
 * <p>Package-private on purpose: it is the seam between the clients in this package
 * and NASA, not something the domain or web layers should reach for.
 */
@Slf4j
final class NasaEndpoint {

    private final RestClient restClient;
    private final String apiKey;

    /** Used in exception messages, so it must name the API without naming the URI. */
    private final String displayName;

    NasaEndpoint(final RestClient restClient, final String apiKey, final String displayName) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.displayName = displayName;
    }

    /**
     * Performs a GET and deserialises the body.
     *
     * @param path       endpoint path relative to the API root, e.g. {@code /planetary/apod}
     * @param parameters adds this request's own query parameters. {@code api_key} is
     *                   appended afterwards, so a caller cannot forget it and cannot
     *                   accidentally log it
     * @param type       target type; a {@link ParameterizedTypeReference} because
     *                   several NASA APIs answer with a bare JSON array
     * @throws NasaUnavailableException on any error status, transport failure,
     *                                  unparseable body or empty body
     */
    <T> T get(final String path,
              final UnaryOperator<UriBuilder> parameters,
              final ParameterizedTypeReference<T> type) {

        final T body;
        try {
            body = restClient.get()
                    .uri(uri(path, parameters))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new NasaUnavailableException(
                                displayName + " returned " + response.getStatusCode());
                    })
                    .body(type);
        } catch (RestClientException ex) {
            // ex is kept as the cause but its message is never copied into this one:
            // the underlying client puts the full URI, api_key included, in its own
            // message. ApiExceptionHandler renders only getMessage(), never the cause.
            throw new NasaUnavailableException("Could not reach " + displayName, ex);
        }

        if (body == null) {
            throw new NasaUnavailableException(displayName + " returned an empty body");
        }
        return body;
    }

    /**
     * Fetches raw bytes, for image endpoints that answer with PNG rather than JSON.
     *
     * <p>The caller is responsible for having validated every part of {@code path}
     * that came from a request: this method concatenates it onto the API root with
     * the key attached, so an unvalidated path turns the service into an open proxy
     * for arbitrary api.nasa.gov endpoints.
     */
    byte[] getBytes(final String path) {
        return get(path, UnaryOperator.identity(), new ParameterizedTypeReference<>() {
        });
    }

    private Function<UriBuilder, URI> uri(final String path,
                                          final UnaryOperator<UriBuilder> parameters) {
        return builder -> parameters.apply(builder.path(path))
                .queryParam("api_key", apiKey)
                .build();
    }
}
