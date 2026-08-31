package com.arthur.asteroid.alerting.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * One {@link RestClient} per NASA API, differing only in its timeout budget.
 *
 * <p>All four point at the same {@code asteroid.nasa.base-url} root, so each client
 * keeps its own endpoint path at the call site. What they do not share is time:
 * DONKI is allowed two minutes and the NEO feed ten seconds, because a single global
 * {@code spring.http.clients.read-timeout} cannot be right for both. That is the
 * whole reason this class exists rather than everyone injecting the auto-configured
 * builder.
 *
 * <p>The timeouts are applied by building a request factory per client from Boot's
 * {@link HttpClientSettings} bean, which is itself bound from
 * {@code spring.http.clients.*}. Overriding just the two timeouts keeps every other
 * setting Boot manages - redirect handling, SSL bundles, the detected HTTP client
 * implementation - instead of hand-rolling a factory and silently losing them.
 *
 * <p>Four {@code RestClient} beans in one context make a bare {@code RestClient}
 * injection ambiguous. Every NASA client therefore takes a {@code @Qualifier}. The
 * parent pom compiles with {@code -parameters}, so name-based resolution would also
 * work, but relying on a parameter name to pick a timeout budget is not obvious
 * enough to be worth it.
 */
@Configuration(proxyBeanMethods = false)
public class NasaRestClientsConfig {

    @Bean
    RestClient nasaNeoRestClient(RestClient.Builder builder,
                                 ObjectProvider<ClientHttpRequestFactoryBuilder<?>> factories,
                                 ObjectProvider<HttpClientSettings> settings,
                                 NasaProperties properties) {
        return build(builder, factories, settings, properties, properties.neo().timeouts());
    }

    @Bean
    RestClient nasaApodRestClient(RestClient.Builder builder,
                                  ObjectProvider<ClientHttpRequestFactoryBuilder<?>> factories,
                                  ObjectProvider<HttpClientSettings> settings,
                                  NasaProperties properties) {
        return build(builder, factories, settings, properties, properties.apod().timeouts());
    }

    @Bean
    RestClient nasaDonkiRestClient(RestClient.Builder builder,
                                   ObjectProvider<ClientHttpRequestFactoryBuilder<?>> factories,
                                   ObjectProvider<HttpClientSettings> settings,
                                   NasaProperties properties) {
        return build(builder, factories, settings, properties, properties.donki().timeouts());
    }

    @Bean
    RestClient nasaEpicRestClient(RestClient.Builder builder,
                                  ObjectProvider<ClientHttpRequestFactoryBuilder<?>> factories,
                                  ObjectProvider<HttpClientSettings> settings,
                                  NasaProperties properties) {
        return build(builder, factories, settings, properties, properties.epic().timeouts());
    }

    private static RestClient build(final RestClient.Builder builder,
                                    final ObjectProvider<ClientHttpRequestFactoryBuilder<?>> factories,
                                    final ObjectProvider<HttpClientSettings> settings,
                                    final NasaProperties properties,
                                    final NasaProperties.Timeouts timeouts) {

        final HttpClientSettings perApi = settings.getIfAvailable(HttpClientSettings::defaults)
                .withTimeouts(timeouts.connect(), timeouts.read());

        final ClientHttpRequestFactoryBuilder<?> factoryBuilder =
                factories.getIfAvailable(ClientHttpRequestFactoryBuilder::detect);

        // clone() because the auto-configured builder bean is prototype-scoped but
        // shared through this method; mutating it in place would let the last caller
        // win and give every client the same timeouts.
        return builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(factoryBuilder.build(perApi))
                .build();
    }
}
