package com.arthur.asteroid.webui.backend;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * One backend service: a {@link RestClient}, its display name, and the failure
 * translation.
 *
 * <p>The mirror image of asteroid-service's {@code NasaEndpoint}, one layer down. The
 * difference worth noticing is what it does on an error status: instead of keeping
 * only the status code, it tries to decode the body as {@code application/problem+json}
 * and hand the backend's own title and detail to the error page. Two services already
 * take the trouble to produce RFC 9457; this is the code that makes that worth having.
 */
@Slf4j
public final class BackendEndpoint {

    private final RestClient restClient;
    private final String displayName;
    private final ObjectMapper objectMapper;

    public BackendEndpoint(final RestClient restClient,
                           final String displayName,
                           final ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.displayName = displayName;
        this.objectMapper = objectMapper;
    }

    public String displayName() {
        return displayName;
    }

    public <T> T get(final String path,
                     final UnaryOperator<UriBuilder> parameters,
                     final ParameterizedTypeReference<T> type) {
        return exchange(HttpMethodKind.GET, path, parameters, type);
    }

    public <T> T post(final String path,
                      final UnaryOperator<UriBuilder> parameters,
                      final ParameterizedTypeReference<T> type) {
        return exchange(HttpMethodKind.POST, path, parameters, type);
    }

    /** Raw bytes, for proxying an image through this service. */
    public byte[] getBytes(final String path) {
        return get(path, UnaryOperator.identity(), new ParameterizedTypeReference<>() {
        });
    }

    private <T> T exchange(final HttpMethodKind method,
                           final String path,
                           final UnaryOperator<UriBuilder> parameters,
                           final ParameterizedTypeReference<T> type) {

        final Function<UriBuilder, URI> uri =
                builder -> parameters.apply(builder.path(path)).build();

        try {
            final RestClient.RequestHeadersSpec<?> request = method == HttpMethodKind.GET
                    ? restClient.get().uri(uri)
                    : restClient.post().uri(uri);

            return request
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, response) -> {
                        throw new BackendUnavailableException(displayName,
                                response.getStatusCode(),
                                decodeProblem(response),
                                displayName + " returned " + response.getStatusCode(),
                                null);
                    })
                    .body(type);
        } catch (BackendUnavailableException ex) {
            throw ex;
        } catch (RestClientException ex) {
            // no status: nothing was listening, or the response never completed
            throw new BackendUnavailableException(displayName, null, null,
                    "Could not reach " + displayName, ex);
        }
    }

    /**
     * Reads the backend's RFC 9457 body, if it sent one.
     *
     * <p>Best effort on purpose. A backend that failed badly enough may not manage a
     * well-formed problem document, and losing the detail is much better than
     * replacing the original failure with a parse error about the failure.
     */
    private ProblemDetail decodeProblem(final ClientHttpResponse response) {
        try {
            final MediaType contentType = response.getHeaders().getContentType();
            if (contentType == null || !MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(contentType)) {
                return null;
            }
            final String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
            return body.isBlank() ? null : objectMapper.readValue(body, ProblemDetail.class);
        } catch (IOException | RuntimeException ex) {
            log.debug("Could not decode a problem+json body from {}", displayName, ex);
            return null;
        }
    }

    private enum HttpMethodKind { GET, POST }
}
